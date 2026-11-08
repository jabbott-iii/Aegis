package io.github.jabbottiii.aegis.core.security.secret

/**
 * Random secrets that Aegis generates on the device and keeps wrapped by an Android Keystore key
 * (docs/01-architecture.md, "Data model"). A secret never leaves the device and is never backed up.
 * Every process that asks for the same [SecretId] gets the same bytes.
 */
interface SecretStore {
    /**
     * Returns the secret for [id], creating it on first use. It never replaces a secret that
     * exists but can't be read, because whatever the secret protects would become unreadable.
     */
    suspend fun getOrCreate(id: SecretId): SecretResult
}

/**
 * What a secret protects. [fileName] names the secret's file and is also bound into its
 * encryption, so one secret's file can't stand in for another's. Never change a file name: the
 * secret stored under the old one would become unreadable.
 */
enum class SecretId(
    internal val fileName: String,
) {
    /** The SQLCipher key for `app.db`. */
    APP_DATABASE("app-db.key"),

    /** The SQLCipher key for `shield.db` (Phase 3). */
    SHIELD_DATABASE("shield-db.key"),
}

sealed interface SecretResult {
    /** [isNew] is true when this call created the secret. */
    class Available(
        val secret: Secret,
        val isNew: Boolean,
    ) : SecretResult

    data class Unavailable(
        val failure: SecretFailure,
    ) : SecretResult
}

/**
 * Why a secret couldn't be returned. [UNREADABLE] is permanent, and [KEYSTORE_ERROR] and
 * [STORAGE_ERROR] may pass on a retry. [KEY_LOST] is usually permanent, but on Android 10 and 11 a
 * failing Keystore looks the same, so confirm it on a later launch before acting on it
 * (docs/00-overview.md, "Open decisions" #9). A [KEYSTORE_ERROR] that repeats on every launch may
 * be permanent too.
 */
enum class SecretFailure {
    /**
     * A secret is stored, but the Keystore reports the key that wraps it as missing. The store
     * won't create a new key while any secret is stored, so new IDs report this too.
     */
    KEY_LOST,

    /** The stored secret is malformed or fails authentication. It can't be recovered. */
    UNREADABLE,

    /** The Keystore failed to create, load, or use its key. A later try may work. */
    KEYSTORE_ERROR,

    /**
     * App storage failed: a secret couldn't be read, written, or made durable. A later try may
     * work, and one that follows a failed write may return the secret that write stored.
     */
    STORAGE_ERROR,
}

/**
 * Secret bytes. [toString] never shows them, and there's no `equals`, so they can't end up in a
 * log line or a comparison by accident. It takes ownership of [bytes]. Outside core:security, only
 * tests construct one, to fake a [SecretStore].
 */
class Secret(
    private val bytes: ByteArray,
) {
    val size: Int get() = bytes.size

    /** A copy that the caller owns. Overwrite it with zeros once it's no longer needed. */
    fun copyBytes(): ByteArray = bytes.copyOf()

    /** Overwrites this secret's bytes with zeros. */
    fun wipe() {
        bytes.fill(0)
    }

    override fun toString(): String = "Secret(${bytes.size} bytes)"
}
