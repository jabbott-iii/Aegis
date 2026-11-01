package io.github.jabbottiii.aegis.core.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.crypto.tink.integration.android.AndroidKeystore
import io.github.jabbottiii.aegis.core.common.logging.AegisLogger
import io.github.jabbottiii.aegis.core.security.SecurityLogEvent
import io.github.jabbottiii.aegis.core.security.secret.KeyWrapper
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException

/**
 * A [KeyWrapper] whose key is a non-exportable AES-256-GCM key in the Android Keystore, used
 * through Tink's Keystore AEAD. The key lives in StrongBox when [preferStrongBox] is set and the
 * device's StrongBox can make one. Otherwise it's in the default Keystore, which is the TEE on most
 * devices. Its security level isn't checked.
 *
 * The key doesn't require user authentication or an unlocked device, because scheduled scans and
 * the Shield open their databases while the screen is locked. The databases live in
 * credential-encrypted storage, so nothing can read them before the first unlock anyway.
 */
internal class KeystoreKeyWrapper(
    private val alias: String,
    private val preferStrongBox: Boolean,
    private val logger: AegisLogger,
) : KeyWrapper {
    /**
     * Not Tink's `hasKey`, which is `containsAlias` and answers false on any Keystore error. On
     * Android 12 and later, `getKey` returns null only for a missing key and throws otherwise (per
     * AOSP's keystore2 `AndroidKeyStoreSpi`). On Android 10 and 11 it checks the alias the same
     * way `containsAlias` does, so a failure there still looks like a missing key.
     */
    override fun hasKey(): Boolean {
        val keyStore =
            try {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            } catch (e: IOException) {
                throw GeneralSecurityException(e)
            }
        return keyStore.getKey(alias, null) != null
    }

    override fun createKey() {
        if (preferStrongBox) {
            try {
                createAndTest(strongBox = true)
                return
            } catch (ignored: GeneralSecurityException) {
                logger.warn(SecurityLogEvent.STRONGBOX_KEY_FAILED)
            } catch (ignored: ProviderException) {
                // Includes StrongBoxUnavailableException.
                logger.warn(SecurityLogEvent.STRONGBOX_KEY_FAILED)
            }
        }
        createAndTest(strongBox = false)
    }

    override fun wrap(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = AndroidKeystore.getAead(alias).encrypt(plaintext, associatedData)

    override fun unwrap(
        ciphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = AndroidKeystore.getAead(alias).decrypt(ciphertext, associatedData)

    /**
     * Some devices create keys they can't use, most often in StrongBox. A key that fails a round
     * trip is deleted again, so the next attempt starts clean.
     */
    private fun createAndTest(strongBox: Boolean) {
        AndroidKeystore.generateNewKeyWithSpec(spec(strongBox))
        var works = false
        try {
            val probe = ByteArray(PROBE_BYTES)
            val roundTrip = unwrap(wrap(probe, SELF_TEST_DATA), SELF_TEST_DATA)
            if (!MessageDigest.isEqual(roundTrip, probe)) {
                throw GeneralSecurityException("The new Keystore key failed its round trip")
            }
            works = true
        } finally {
            if (!works) deleteQuietly()
        }
    }

    private fun deleteQuietly() {
        try {
            AndroidKeystore.deleteKey(alias)
        } catch (ignored: GeneralSecurityException) {
            // The broken key stays, so the store keeps reporting KEYSTORE_ERROR. The original
            // failure is what the caller sees.
        }
    }

    private fun spec(strongBox: Boolean): KeyGenParameterSpec =
        KeyGenParameterSpec
            .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(AES_KEY_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setIsStrongBoxBacked(strongBox)
            .build()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_KEY_BITS = 256
        const val PROBE_BYTES = 32
        val SELF_TEST_DATA = "aegis/keystore/self-test".toByteArray(Charsets.UTF_8)
    }
}
