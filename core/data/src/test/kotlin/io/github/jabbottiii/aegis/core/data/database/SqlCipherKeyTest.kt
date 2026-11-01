package io.github.jabbottiii.aegis.core.data.database

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlCipherKeyTest {
    @Test
    fun `a 32-byte key becomes x quote 64 uppercase hex digits quote`() {
        val key = ByteArray(32) { (it * 8).toByte() }

        val raw = sqlCipherRawKey(key)

        val expected = "x'" + key.joinToString("") { "%02X".format(it.toInt() and 0xFF) } + "'"
        assertEquals(expected, raw.toString(Charsets.US_ASCII))
        assertEquals(67, raw.size)
    }

    @Test
    fun `every byte value maps to two hex digits`() {
        val key = ByteArray(32) { if (it % 2 == 0) 0x00 else 0xFF.toByte() }

        val digits = sqlCipherRawKey(key).toString(Charsets.US_ASCII).removePrefix("x'").removeSuffix("'")

        assertEquals("00FF".repeat(16), digits)
    }

    @Test
    fun `keys of any other length are refused`() {
        listOf(0, 16, 31, 33, 64).forEach { size ->
            assertFailsWith<IllegalArgumentException> { sqlCipherRawKey(ByteArray(size)) }
        }
    }
}
