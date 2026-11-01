package io.github.jabbottiii.aegis.core.security.secret

import java.security.GeneralSecurityException

/**
 * Encrypts small secrets with one non-exportable key. The Android Keystore implementation is
 * `KeystoreKeyWrapper`. Tests use a software key.
 *
 * Every method can throw [GeneralSecurityException] or `java.security.ProviderException`.
 * [unwrap] throws a `javax.crypto.BadPaddingException`, usually `AEADBadTagException`, when the
 * ciphertext or its associated data doesn't authenticate.
 */
internal interface KeyWrapper {
    /**
     * Whether the key exists. It throws when the Keystore fails, wherever the platform can tell
     * the two apart. Android 10 and 11 can't, so there a failing Keystore can look like a missing key.
     */
    fun hasKey(): Boolean

    /**
     * Creates the key. It replaces any existing key, so call it only when [hasKey] is false and no
     * secret wrapped by an earlier key is stored.
     */
    fun createKey()

    fun wrap(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray

    fun unwrap(
        ciphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray
}
