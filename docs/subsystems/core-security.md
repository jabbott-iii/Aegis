# Subsystem: `core:security` — secrets, Keystore, signature verification

The only module that touches the Android Keystore and Tink
(`docs/01-architecture.md`, "Module layout"). `checkModuleGraph` fails if
another module reaches Tink at runtime without going through
`core:security`, or sees Tink on its compile classpath at all (an `api`
leak), so Tink stays an `implementation` dependency here. The Keystore rule
is reviewed by hand. It's an Android library (`aegis.android.library`,
`aegis.hilt`) that depends on `core:common` and Tink for Android. Phase 0
built the secret store: a random secret per database, wrapped by one
Android Keystore AES-256-GCM key. Intel bundle signature verification
arrives with the intel client in Phase 2 (ADR-0006). The integrity
self-check isn't scheduled in any phase yet.

## Contents

| Package or file | Provides |
| :- | :- |
| `secret` | `SecretStore`, `SecretId`, `SecretResult`, `SecretFailure` and `Secret` (the public API); `WrappedSecretStore`, the implementation; `KeyWrapper`, the wrapping-key interface; `fsyncDirectory` |
| `keystore` | `KeystoreKeyWrapper`: a `KeyWrapper` over a non-exportable Keystore AES-256-GCM key, used through Tink's `AndroidKeystore` AEAD |
| `di` | `SecurityModule`: the singleton `SecretStore`, with its directory and key alias |
| `SecurityLogEvent` | `SECRET_CREATED`, `SECRET_KEY_LOST`, `SECRET_UNREADABLE`, `SECRET_KEYSTORE_FAILED`, `SECRET_STORAGE_FAILED`, `STRONGBOX_KEY_FAILED`. None carries key material or a file name |
| Signature verification (planned, Phase 2) | Ed25519 verification of an intel bundle's `manifest.sig` against the pinned keys and the delegation record, before anything in the bundle is parsed |
| Integrity self-check (planned) | A startup check of Aegis's signing certificate against the expected digest |

Only the five public types in `secret` are visible outside the module.
`WrappedSecretStore`, `KeyWrapper`, `KeystoreKeyWrapper`, `fsyncDirectory`
and `SecurityModule` are `internal`.

## Key types

```kotlin
interface SecretStore {
    suspend fun getOrCreate(id: SecretId): SecretResult  // never replaces a secret that exists but can't be read
}

enum class SecretId(internal val fileName: String) {     // fileName is bound into the encryption
    APP_DATABASE("app-db.key"),                          // the SQLCipher key for app.db
    SHIELD_DATABASE("shield-db.key"),                    // the SQLCipher key for shield.db (Phase 3)
}

sealed interface SecretResult {
    class Available(val secret: Secret, val isNew: Boolean) : SecretResult  // isNew: this call created it
    data class Unavailable(val failure: SecretFailure) : SecretResult
}

enum class SecretFailure { KEY_LOST, UNREADABLE, KEYSTORE_ERROR, STORAGE_ERROR }

class Secret(bytes: ByteArray) {           // takes ownership of bytes; no equals
    val size: Int
    fun copyBytes(): ByteArray             // the caller's copy: overwrite it with zeros when done
    fun wipe()                             // overwrites this secret's bytes with zeros
    override fun toString(): String        // "Secret(32 bytes)", never the bytes
}

internal interface KeyWrapper {            // every method can throw GeneralSecurityException or ProviderException
    fun hasKey(): Boolean                  // throws on a Keystore failure, where the platform can tell
    fun createKey()                        // replaces any key: only while hasKey() is false and no secret is stored
    fun wrap(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun unwrap(ciphertext: ByteArray, associatedData: ByteArray): ByteArray  // BadPaddingException if it doesn't authenticate
}
```

## Secret failures

| Failure | Meaning | Can a retry help? |
| :- | :- | :- |
| `KEY_LOST` | A secret is stored, but the Keystore reports the key that wraps it as missing. The store won't create a new key while any secret is stored, so new IDs report this too | Usually not. On Android 10 and 11 a failing Keystore looks the same, so confirm it on a later launch before acting on it (`docs/00-overview.md`, "Open decisions" #9) |
| `UNREADABLE` | The stored secret is malformed or fails authentication | No. It can't be recovered |
| `KEYSTORE_ERROR` | The Keystore failed to create, load or use its key | Yes, a later try may work. One that repeats on every launch may be permanent |
| `STORAGE_ERROR` | App storage failed: a secret couldn't be read, written or made durable | Yes, a later try may work. One that follows a failed write may return the secret that write stored |

## Secret store

`WrappedSecretStore` keeps each secret as a file in
`noBackupFilesDir/aegis-secrets/`, which Auto Backup and device transfer
never copy (NFR-PRIV-04). A secret never leaves the device, and every
process that asks for the same `SecretId` gets the same bytes.

- **One secret per database.** Each secret is 32 random bytes from
  `SecureRandom`.
- **One wrapping key.** Every secret is encrypted with the same Keystore
  key, alias `aegis_secret_wrapping_v1`. Changing the alias orphans every
  stored secret. The associated data is `aegis/secret/v1/<file name>`,
  which binds each wrapped secret to its file, so one secret's file can't
  stand in for another's. Never change a `SecretId` file name: the secret
  stored under the old one would become unreadable.
- **Creation runs under a lock.** The main process and `:shield` may both
  ask for a secret on first launch. Without the lock they could each create
  the wrapping key, and the second key would make the first process's
  secret unreadable. The lock is a file lock on `.lock` in the secrets
  directory, taken inside one process-wide `Mutex`, because
  `FileChannel.lock` throws if this process already holds the lock.
- **Nothing is ever replaced.** Whatever a secret protects would become
  unreadable for good:
  - A secret that exists but can't be read is reported, never recreated.
  - The wrapping key is created only while no secret is stored. Before
    Android 12 the Keystore can report a key as missing when it's only
    failing, so a stored secret is the proof that the key exists. Without
    this rule, one transient error could replace the key and make every
    stored secret unreadable.
  - A file whose existence or size can't be checked counts as present.
- **New secrets are tested before they're stored.** Some Keystore
  implementations return ciphertext they can't decrypt. A new secret's
  wrapped form must have the expected length and decrypt back to the
  secret, or the result is `KEYSTORE_ERROR` and nothing is written.
- **Durable before returned.** A new secret is written to `<file>.tmp` and
  synced, renamed into place atomically, and then the directory is synced
  (`fsyncDirectory`, which calls `fsync` on the directory through
  `android.system.Os`, because Java has no API for it). If any step fails,
  including the directory sync, the result is `STORAGE_ERROR`, so a secret
  is never handed out before it's durable. After a failed sync, the next
  call finds the file and returns it. A leftover temporary file from an
  interrupted write doesn't matter.
- **Secret bytes stay out of logs and comparisons.** `Secret.toString`
  shows only the size, and there's no `equals`. Outside `core:security`,
  only tests construct a `Secret`, to fake a `SecretStore`.

**Reading** a secret for an ID:

1. No file: create one (below).
2. A file that isn't exactly 64 bytes, or whose format tag is wrong:
   `UNREADABLE`, without asking the key wrapper.
3. `hasKey()` is false: `KEY_LOST`.
4. The ciphertext fails authentication (`BadPaddingException`, including
   `AEADBadTagException`), or decrypts to anything but 32 bytes:
   `UNREADABLE`.
5. Otherwise `Available`, with `isNew = false`.

**Creating** one: if the key exists, create the secret. If the key is
missing but any secret is stored, `KEY_LOST`. If the key is missing and
nothing is stored, create the key, then the secret.

**Errors:** an `IOException` is `STORAGE_ERROR`, and a
`GeneralSecurityException` or `ProviderException` is `KEYSTORE_ERROR`. Each
failure logs its `SecurityLogEvent` at `ERROR`, with only the exception's
class name. A created secret logs `SECRET_CREATED` at `INFO`.

## Secret file format

Format 1, exactly 64 bytes. Anything else is `UNREADABLE`.

| Offset | Size | Field |
| :- | :- | :- |
| 0 | 4 | Format tag: `AGS` and format version 1 (`41 47 53 01`) |
| 4 | 12 | AES-GCM IV |
| 16 | 32 | The encrypted secret |
| 48 | 16 | GCM authentication tag |

## Keystore key

- **The key:** a non-exportable AES-256-GCM key (purposes encrypt and
  decrypt, GCM, no padding) in the Android Keystore, used through Tink's
  `AndroidKeystore` AEAD.
- **StrongBox where it works.** `SecurityModule` asks for StrongBox when
  the device reports `FEATURE_STRONGBOX_KEYSTORE`. If StrongBox can't make
  the key (`GeneralSecurityException`, or `ProviderException`, which
  includes `StrongBoxUnavailableException`), the wrapper logs
  `STRONGBOX_KEY_FAILED` and creates the key in the default Keystore, which
  is the TEE on most devices. The key's security level isn't checked.
- **Every new key is tested.** Some devices create keys they can't use,
  most often in StrongBox. A new key must pass an encrypt/decrypt round
  trip of a 32-byte probe (associated data `aegis/keystore/self-test`), or
  it's deleted again, so the next attempt starts clean. If the delete also
  fails, the broken key stays and the store keeps reporting
  `KEYSTORE_ERROR`.
- **No user authentication, no unlocked-device requirement.** Scheduled
  scans and the Shield open their databases while the screen is locked. The
  databases live in credential-encrypted storage, so nothing can read them
  before the first unlock anyway.
- **`hasKey` uses `KeyStore.getKey`,** not Tink's `hasKey`, which is
  `containsAlias` and answers false on any Keystore error. On Android 12
  and later, `getKey` returns null only for a missing key and throws
  otherwise (per AOSP's keystore2 `AndroidKeyStoreSpi`). On Android 10 and
  11 it checks the alias the same way `containsAlias` does, so a failure
  there still looks like a missing key.

## Signature verification (planned)

- **Two-tier Ed25519 signing** (Phase 2, ADR-0006). An offline root key
  that is pinned in the app delegates a short-lived online key, which CI
  uses inside a protected environment. No private key lives in the
  repository or in logs (`AGENTS.md` rule 5).
- **Verify first.** Nothing in a bundle is parsed until `core:security` has
  verified `manifest.sig` against the pinned keys and the delegation record
  (NFR-SEC-01, FR-INTEL-01). `intel:client` then checks the part hashes,
  and only then are the parts parsed (`intel.md`).
- **Tink** provides the Ed25519 verification
  (`docs/01-architecture.md`, "Technology stack").
- The manifest format, the delegation record and key rotation are in
  ADR-0006.

## Integrity self-check (planned)

A startup check verifies Aegis's signing certificate against the expected
digest (`docs/01-architecture.md`, "Security architecture of Aegis
itself"). `docs/threat-model.md` ("B3. Threats and mitigations") pairs it
with a warning screen against a repackaged Aegis APK, verified by an
instrumented test with a re-signed build.

## Other rules

- **Quarantine encryption.** A quarantined file is encrypted with Tink AEAD
  into app-private storage (`docs/features/malware-detection.md`,
  "Remediation"), and only this module touches Tink.
- **No API keys in the APK.** Optional features that need keys, such as Web
  Risk, require a backend proxy (`docs/00-overview.md`, "Open decisions"
  #4).
- `.claude/rules/intel-pipeline.md` applies to `core/security/**`, and
  `checkSourceRules` fails on `!!` under `core/security/`.

## Tests

- `WrappedSecretStoreTest` (JVM, JUnit 4), with a software AES-256-GCM key,
  generated per test run, in place of the Keystore:
  - the first call creates a 32-byte secret, later calls return the same
    bytes, and the directory is synced once;
  - a new store on the same directory, like another process, reads the
    same secret;
  - secrets for different IDs differ and share one wrapping key;
  - concurrent first calls from four stores create exactly one secret;
  - the stored file holds the format tag and ciphertext, never the secret;
  - a lost wrapping key is `KEY_LOST`, and the stored file is left in
    place;
  - while a secret is stored, a missing key is never replaced, even for a
    new ID;
  - with nothing stored, a missing key is simply created again;
  - a tampered secret is `UNREADABLE` and isn't replaced;
  - one secret's file can't stand in for another's;
  - malformed files (empty, the tag alone, too short, too long, a wrong
    format version) are `UNREADABLE` without asking the key wrapper;
  - a wrapped secret of the wrong length is `UNREADABLE`, and so is a
    ciphertext the wrapper rejects as malformed;
  - a Keystore failure while checking for the key is transient and creates
    nothing; one while creating the key leaves nothing behind;
  - a key that can't decrypt what it encrypted is caught before the secret
    is stored;
  - after a transient Keystore failure, the next call creates the secret;
  - a secrets directory that can't be created is a `STORAGE_ERROR`;
  - a failed directory sync withholds the new secret, and the next call
    returns it;
  - a leftover temporary file from an interrupted write doesn't matter;
  - secrets don't show their bytes and can be wiped;
  - nothing logged carries secret material.
- `KeystoreKeyWrapperTest` (instrumented), against the device's real
  Keystore under the test-only alias `aegis_secret_wrapping_test`:
  - a created key wraps and unwraps, with a fresh IV each time (60 bytes
    per wrapped 32-byte secret);
  - tampered ciphertext and different associated data each fail
    authentication (`AEADBadTagException`);
  - preferring StrongBox still creates a working key on every device, and
    logs `STRONGBOX_KEY_FAILED` on a device without StrongBox;
  - the store keeps the same secret across instances;
  - the store reports a lost key as `KEY_LOST` and keeps the file.
- The instrumented tests passed on an API 37 emulator on 2026-11-01 and
  haven't been run on API 29 locally. Android 10 (API 29) and 11 are where
  `hasKey` can't tell a failing Keystore from a missing key.

## Open questions

- **An unreadable database after `KEY_LOST`** is open decision #9
  (`core-data.md`, "Open questions").
- **The integrity self-check** has no phase yet.
