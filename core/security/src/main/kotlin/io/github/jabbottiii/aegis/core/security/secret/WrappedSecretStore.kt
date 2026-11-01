package io.github.jabbottiii.aegis.core.security.secret

import io.github.jabbottiii.aegis.core.common.coroutines.AegisDispatchers
import io.github.jabbottiii.aegis.core.common.logging.AegisLogger
import io.github.jabbottiii.aegis.core.security.SecurityLogEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.BadPaddingException

/**
 * Keeps each secret as a file in [directory]: a 4-byte format tag, then the secret encrypted by
 * [wrapper] with its [SecretId] as associated data. [directory] must be app-private and excluded
 * from backup (`noBackupFilesDir`).
 *
 * Creation happens under a file lock, because the main process and `:shield` may both ask for a
 * secret on first launch. Without the lock they could each create the wrapping key, and the second
 * key would make the first process's secret unreadable.
 *
 * Nothing here replaces a stored secret or the key that wraps it, because whatever they protect
 * would become unreadable for good:
 * - A secret that exists but can't be read is reported, never recreated.
 * - The wrapping key is created only while no secret is stored. The Keystore can report a key as
 *   missing when it's only failing, so a stored secret is the proof that a key exists.
 * - A file whose existence or size can't be checked counts as present.
 */
internal class WrappedSecretStore(
    private val directory: File,
    private val wrapper: KeyWrapper,
    private val dispatchers: AegisDispatchers,
    private val logger: AegisLogger,
    /** Makes a rename in [directory] durable. It throws [IOException] on failure. */
    private val syncDirectory: (File) -> Unit,
    private val random: SecureRandom = SecureRandom(),
) : SecretStore {
    override suspend fun getOrCreate(id: SecretId): SecretResult =
        processLock.withLock {
            withContext(dispatchers.io) {
                try {
                    withFileLock { read(id) ?: create(id) }
                } catch (e: IOException) {
                    unavailable(SecretFailure.STORAGE_ERROR, e)
                } catch (e: GeneralSecurityException) {
                    unavailable(SecretFailure.KEYSTORE_ERROR, e)
                } catch (e: ProviderException) {
                    unavailable(SecretFailure.KEYSTORE_ERROR, e)
                }
            }
        }

    /** Null only when there's certainly no secret for [id] yet. */
    private fun read(id: SecretId): SecretResult? {
        val file = File(directory, id.fileName)
        if (Files.notExists(file.toPath())) return null
        val ciphertext = readCiphertext(file)
        return when {
            ciphertext == null -> unavailable(SecretFailure.UNREADABLE, cause = null)
            !wrapper.hasKey() -> unavailable(SecretFailure.KEY_LOST, cause = null)
            else -> unwrap(ciphertext, id)
        }
    }

    private fun unwrap(
        ciphertext: ByteArray,
        id: SecretId,
    ): SecretResult =
        try {
            val bytes = wrapper.unwrap(ciphertext, associatedData(id))
            if (bytes.size == SECRET_BYTES) {
                SecretResult.Available(Secret(bytes), isNew = false)
            } else {
                bytes.fill(0)
                unavailable(SecretFailure.UNREADABLE, cause = null)
            }
        } catch (e: BadPaddingException) {
            // Includes AEADBadTagException: the file doesn't authenticate under this key.
            unavailable(SecretFailure.UNREADABLE, e)
        }

    private fun create(id: SecretId): SecretResult =
        when {
            wrapper.hasKey() -> {
                createSecret(id)
            }

            // A stored secret proves the key existed. A new key would make it unreadable for good.
            anySecretStored() -> {
                unavailable(SecretFailure.KEY_LOST, cause = null)
            }

            else -> {
                wrapper.createKey()
                createSecret(id)
            }
        }

    private fun anySecretStored(): Boolean =
        SecretId.entries.any {
            !Files.notExists(File(directory, it.fileName).toPath())
        }

    private fun createSecret(id: SecretId): SecretResult {
        val secret = ByteArray(SECRET_BYTES).also(random::nextBytes)
        var handedOver = false
        try {
            val associatedData = associatedData(id)
            val wrapped = wrapper.wrap(secret, associatedData)
            // Some Keystore implementations return ciphertext they can't decrypt. Find out now,
            // before anything is encrypted with the secret.
            if (wrapped.size != FILE_BYTES - FORMAT_TAG.size || !wrapper.roundTrips(wrapped, secret, associatedData)) {
                return unavailable(SecretFailure.KEYSTORE_ERROR, cause = null)
            }
            writeDurably(File(directory, id.fileName), FORMAT_TAG + wrapped)
            logger.info(SecurityLogEvent.SECRET_CREATED)
            handedOver = true
            return SecretResult.Available(Secret(secret), isNew = true)
        } finally {
            if (!handedOver) secret.fill(0)
        }
    }

    /**
     * Writes [target] so that it's complete or absent, and survives a power cut. It throws
     * [IOException] if any step fails, including the directory sync, so a secret is never handed
     * out before it's durable. After a failed sync the next call finds the file and returns it.
     *
     * The rename would replace an existing [target], but [read] gets here only when [target]
     * certainly doesn't exist, and the file lock keeps other Aegis processes out meanwhile.
     */
    private fun writeDurably(
        target: File,
        bytes: ByteArray,
    ) {
        val temporary = File(directory, "${target.name}.tmp")
        FileOutputStream(temporary).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        syncDirectory(directory)
    }

    private inline fun <T> withFileLock(block: () -> T): T {
        directory.mkdirs()
        if (!directory.isDirectory) throw IOException("The secrets directory can't be created")
        RandomAccessFile(File(directory, LOCK_FILE), "rw").use { file ->
            file.channel.lock().use { return block() }
        }
    }

    private fun unavailable(
        failure: SecretFailure,
        cause: Throwable?,
    ): SecretResult {
        val event =
            when (failure) {
                SecretFailure.KEY_LOST -> SecurityLogEvent.SECRET_KEY_LOST
                SecretFailure.UNREADABLE -> SecurityLogEvent.SECRET_UNREADABLE
                SecretFailure.KEYSTORE_ERROR -> SecurityLogEvent.SECRET_KEYSTORE_FAILED
                SecretFailure.STORAGE_ERROR -> SecurityLogEvent.SECRET_STORAGE_FAILED
            }
        logger.error(event, cause = cause)
        return SecretResult.Unavailable(failure)
    }

    companion object {
        const val SECRET_BYTES = 32

        /** "AGS" plus format version 1. */
        val FORMAT_TAG = byteArrayOf(0x41, 0x47, 0x53, 0x01)

        /** Format 1: the tag, then AES-GCM's 12-byte IV, the 32-byte secret, and the 16-byte tag. */
        const val FILE_BYTES = 4 + 12 + SECRET_BYTES + 16

        private const val LOCK_FILE = ".lock"

        /**
         * One process-wide lock, so that two stores on the same directory never request the file
         * lock at the same time. `FileChannel.lock` throws if this process already holds it.
         */
        private val processLock = Mutex()

        /** Binds a wrapped secret to its file, so that one secret's file can't stand in for another's. */
        fun associatedData(id: SecretId): ByteArray = "aegis/secret/v1/${id.fileName}".toByteArray(Charsets.UTF_8)
    }
}

/** The ciphertext, or null when the file isn't a format-1 secret. Throws [IOException] when it can't be read. */
private fun readCiphertext(file: File): ByteArray? {
    val tag = WrappedSecretStore.FORMAT_TAG
    if (Files.size(file.toPath()) != WrappedSecretStore.FILE_BYTES.toLong()) return null
    val stored = file.readBytes()
    val tagged = stored.size == WrappedSecretStore.FILE_BYTES && stored.copyOfRange(0, tag.size).contentEquals(tag)
    return if (tagged) stored.copyOfRange(tag.size, stored.size) else null
}

/** Whether [wrapped] decrypts back to [secret]. The decrypted copy is overwritten either way. */
private fun KeyWrapper.roundTrips(
    wrapped: ByteArray,
    secret: ByteArray,
    associatedData: ByteArray,
): Boolean {
    val unwrapped = unwrap(wrapped, associatedData)
    val same = MessageDigest.isEqual(unwrapped, secret)
    unwrapped.fill(0)
    return same
}
