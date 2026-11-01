package io.github.jabbottiii.aegis.core.data.database

/** SQLCipher 4 keys databases with 256-bit AES keys. */
internal const val RAW_KEY_BYTES = 32

private val HEX_DIGITS = "0123456789ABCDEF".toByteArray(Charsets.US_ASCII)
private val RAW_KEY_PREFIX = "x'".toByteArray(Charsets.US_ASCII)
private val RAW_KEY_SUFFIX = '\''.code.toByte()
private const val BYTE_MASK = 0xFF
private const val LOW_DIGIT_MASK = 0x0F
private const val BITS_PER_DIGIT = 4
private const val DIGITS_PER_BYTE = 2

/**
 * [key] in SQLCipher's raw-key form, the ASCII bytes of `x'<64 hex digits>'`. SQLCipher uses a raw
 * key as is and skips its PBKDF2 passphrase derivation, which would only slow down every open: the
 * key is already 256 random bits. It's built as bytes, never as a String, so the caller can
 * overwrite it.
 */
internal fun sqlCipherRawKey(key: ByteArray): ByteArray {
    require(key.size == RAW_KEY_BYTES) { "A SQLCipher raw key is $RAW_KEY_BYTES bytes" }
    val raw = ByteArray(RAW_KEY_PREFIX.size + key.size * DIGITS_PER_BYTE + 1)
    RAW_KEY_PREFIX.copyInto(raw)
    key.forEachIndexed { index, byte ->
        val value = byte.toInt() and BYTE_MASK
        val at = RAW_KEY_PREFIX.size + index * DIGITS_PER_BYTE
        raw[at] = HEX_DIGITS[value ushr BITS_PER_DIGIT]
        raw[at + 1] = HEX_DIGITS[value and LOW_DIGIT_MASK]
    }
    raw[raw.lastIndex] = RAW_KEY_SUFFIX
    return raw
}
