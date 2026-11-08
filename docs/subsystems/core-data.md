# Subsystem: `core:data` — encrypted databases and settings

Persistence for both app processes: Room databases encrypted with SQLCipher
and keyed by secrets from `core:security`, and one multi-process settings
DataStore. It's an Android library (`aegis.android.library`, `aegis.hilt`)
that depends on `core:common`, `core:security`, Room 3 (`androidx.room3`),
SQLCipher for Android 4.20 and DataStore Preferences. Phase 0 built the
database factory and the settings store. No real database exists yet:
`app.db`'s entities, migrations and schema export arrive with the Phase 1
requirements that define them, and `shield.db` with the Shield in Phase 3.
Until then the factory is tested on a test-only database. No repository
exists yet either.

## Contents

| Package or file | Provides |
| :- | :- |
| `database` | `EncryptedDatabaseFactory`, which opens a Room database with SQLCipher and a secret from `core:security`; `DatabaseResult`; `sqlCipherRawKey` and `RAW_KEY_BYTES` |
| `settings` | `settingsDataStore()`, the multi-process Preferences DataStore with its corruption handler, and `SETTINGS_RESET_MARKER` |
| `di` | `DataModule`: the singleton settings `DataStore<Preferences>`, under the `@SettingsPreferences` qualifier |
| `DataLogEvent` | `DATABASE_UNREADABLE`, `DATABASE_KEY_REPLACED`, `DATABASE_OPEN_FAILED`, `DATABASE_NOT_CLOSED`, `SETTINGS_RESET_AFTER_CORRUPTION`. None names a database, a file or a setting |
| Repositories (planned) | How features reach data. Features reach the settings store through repositories in this module, never directly |

Everything built so far is `internal` to the module.

## Key types

```kotlin
internal sealed interface DatabaseResult<out T> {
    data class Opened<T>(val database: T) : DatabaseResult<T>
    data class KeyUnavailable(val failure: SecretFailure) : DatabaseResult<Nothing>  // core:security had no key
    data object Unreadable : DatabaseResult<Nothing>   // the file exists, this key can't open it; the file is kept
    data object OpenFailed : DatabaseResult<Nothing>   // another SQLite failure, such as a full disk; may pass later
}

internal class EncryptedDatabaseFactory @Inject constructor(
    @ApplicationContext context: Context,
    secrets: SecretStore,
    dispatchers: AegisDispatchers,
    logger: AegisLogger,
) {
    suspend fun <T : RoomDatabase> open(
        name: String,                          // a file under getDatabasePath
        type: Class<T>,
        secretId: SecretId,
        migrations: List<Migration> = emptyList(),
    ): DatabaseResult<T>
}

internal const val RAW_KEY_BYTES = 32                    // SQLCipher 4 uses 256-bit AES keys
internal fun sqlCipherRawKey(key: ByteArray): ByteArray  // the ASCII bytes of x'<64 hex digits>'

internal val SETTINGS_RESET_MARKER: Preferences.Key<Boolean>  // "aegis_settings_reset_after_corruption"
internal fun settingsDataStore(file: File, scope: CoroutineScope, logger: AegisLogger): DataStore<Preferences>
```

## Opening an encrypted database

`EncryptedDatabaseFactory.open` does this:

1. **Get the secret.** It asks `SecretStore.getOrCreate(secretId)`. If the
   store returns `Unavailable`, the result is `KeyUnavailable(failure)` and
   nothing is opened or created. The failure says whether a retry can help
   (`core-security.md`, "Secret failures").
2. **Build the raw key.** It copies the secret's bytes, wipes the `Secret`,
   builds SQLCipher's raw-key form `x'<64 uppercase hex digits>'` as bytes,
   and zeroes the copy. The key is built as bytes, never as a `String`, so
   it can be overwritten. SQLCipher uses a raw key as is and skips its
   PBKDF2 passphrase derivation, which would only slow down every open: the
   key is already 256 random bits.
3. **Refuse a replaced key.** If `core:security` just created the secret
   (`isNew`) but the database file already exists, the database outlived
   its key. The result is `Unreadable`, logged as `DATABASE_KEY_REPLACED`,
   and the file isn't opened.
4. **Check the key.** Otherwise, on the IO dispatcher, it opens one
   connection and reads the schema (`SELECT count(*) FROM sqlite_master`),
   which works only with the right key. Room would otherwise find out on
   its first query, too late to report a typed result. A missing file is
   created, empty and encrypted.
   - A corrupt file or one that isn't a database: `Unreadable`, logged as
     `DATABASE_UNREADABLE`.
   - Any other `SQLException`: `OpenFailed`, logged as
     `DATABASE_OPEN_FAILED`.
5. **Build Room** with `Room.databaseBuilder`, the caller's migrations, the
   `SQLCipherDriver`, and the IO dispatcher as the query coroutine context.
6. **Hand over the key.** The driver keeps the key for as long as the
   database is open, because every new connection needs it. Every other
   exit overwrites the key with zeros.

## Never deleting a database

- **The error handler keeps the file.** SQLCipher's default error handler
  deletes any database it can't open, and a wrong key looks the same as
  corruption, so one lost key would silently wipe the user's findings and
  trust decisions. The factory gives the driver a handler that leaves the
  file alone: the open that hit the problem fails, and the factory reports
  it.
- **Only migrations pass through.** Room options come only through
  `open`'s parameters, never through the builder. Destructive migration
  drops every table, and a prepackaged database (`createFromAsset` and the
  like) can delete the file when a migration is due, so neither is ever
  set.
- **The caller decides.** What the app does with an unreadable database is
  open decision #9 (see "Open questions"). On Android 10 and 11 a failing
  Keystore can look like a lost key (`core-security.md`).

## SQLCipher logging

- SQLCipher doesn't load its native library itself, so the factory calls
  `System.loadLibrary("sqlcipher")`. Repeated calls are no-ops.
- **The Java layer goes through the facade.** SQLCipher's messages can
  include paths and SQL, so its Java logger (messages at `WARN` and above)
  becomes `AegisLogger.debug` text, which release builds drop
  (`AGENTS.md` rule 8). Its `CloseGuard` leak reports log
  `DATABASE_NOT_CLOSED` as a warning, with the report as debug text.
- **The native layer can't be redirected.** SQLCipher's native layer
  (forked from AOSP) sends SQLite's own error log to logcat, and Java can't
  redirect it. A line can include an error code, the path of one of Aegis's
  database files, and the text of a failing SQL statement. So SQL never
  contains values: every value is a bound argument, which Room's generated
  queries already guarantee.
- **What an emulator showed.** On an API 37 emulator, opening a database
  with the wrong key logged
  `E/SQLiteLog: (26) file is not a database in "SELECT COUNT(*) FROM sqlite_schema;"`
  and three `D/sqlcipher` codec lines that carry only a page number and
  error codes. No path or row data appeared. The binary's I/O-error and
  WAL-recovery messages can still include a database path, and those
  weren't triggered.

## Settings store

- **One file, every process.** One multi-process Preferences DataStore at
  `files/datastore/settings.preferences_pb`
  (`preferencesDataStoreFile("settings")`), because `:shield` reads
  settings too. Two single-process DataStores on one file would overwrite
  each other's changes, so each process creates exactly one, through
  `settingsDataStore()`. `DataModule` gives it a scope of `SupervisorJob()`
  on the IO dispatcher that lives as long as the process.
- **Choices only.** Settings aren't encrypted, so they hold only choices:
  booleans, numbers and enum names. Anything that identifies the user's
  apps, sites or content goes in an encrypted database instead: package
  names, domains, URLs, or free text such as a custom DoH address.
- **No backup.** The file must be under `filesDir`, which the backup rules
  exclude like every other domain (NFR-PRIV-04).
- **Corruption resets to defaults.** A corrupt file is replaced with the
  shipped defaults plus `SETTINGS_RESET_MARKER` set to true, and the reset
  is logged as a `SETTINGS_RESET_AFTER_CORRUPTION` warning. A reset never
  turns on something the user didn't choose, but it can turn off a
  protection they did. The first feature that keeps a protection's on/off
  state here must check the marker, tell the user, and then clear it, so a
  protection never stops silently. No such setting exists yet.

## Databases (planned)

The main process and `:shield` keep separate Room database files, each with
its own secret: `SecretId.APP_DATABASE` (`app-db.key`) and
`SecretId.SHIELD_DATABASE` (`shield-db.key`). `:shield` writes only
aggregated counters, which the main process reads.

**`app.db`** (main process) holds the entities listed in
`docs/01-architecture.md` ("Data model").

**`shield.db`** (`:shield`) holds `BlockCounter` rows: day, uid or
packageName, category, operator, count. It keeps aggregates only, never
full query logs, and retains them for 7 days (`docs/privacy.md`, "Data
inventory").

Room merges `MultiInstanceInvalidationService` into the manifest. It isn't
exported, and it's idle unless a database enables multi-instance
invalidation, which none does yet (`docs/permissions-and-policy.md`,
"Exported components and merged manifest entries").

## Tests

- `SqlCipherKeyTest` (JVM, JUnit 4):
  - a 32-byte key becomes `x'`, 64 uppercase hex digits and `'` (67 bytes);
  - every byte value maps to two hex digits;
  - keys of any other length (0, 16, 31, 33 and 64 bytes) are refused.
- `EncryptedDatabaseFactoryTest` (instrumented), on a test-only
  `ProbeDatabase` with one `probe` table and a fake `SecretStore`:
  - it opens an encrypted database that Room can use, and the file doesn't
    start with SQLite's plaintext header;
  - row text never reaches any of the database's files in plaintext;
  - it reopens with the same key;
  - a different key is `Unreadable`, logs `DATA.DATABASE_UNREADABLE`, and
    leaves the file byte for byte (by SHA-256); the original key still
    opens it;
  - a new key for an existing database is `Unreadable`, logs
    `DATA.DATABASE_KEY_REPLACED`, and leaves the file untouched;
  - a plaintext SQLite database is `Unreadable` and kept;
  - an unavailable key opens nothing and creates no file;
  - the factory wipes the secret it was given.
- `SettingsDataStoreTest` (instrumented): a setting is written and read
  back with nothing logged; a corrupt file falls back to the defaults plus
  the reset marker, with one `WARN` record,
  `DATA.SETTINGS_RESET_AFTER_CORRUPTION`.
- Instrumented tests need an emulator or a device
  (`./gradlew connectedDebugAndroidTest`). They passed on an API 37
  emulator on 2026-11-01 and haven't been run on API 29 locally.
- `docs/threat-model.md` ("B3. Threats and mitigations") cites these tests
  for the plaintext-header, plaintext-row and file-kept checks.

## Open questions

- **An unreadable `app.db`:** what the app does when `open` returns
  `Unreadable` is `docs/00-overview.md` ("Open decisions") #9.
