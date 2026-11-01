package io.github.jabbottiii.aegis.core.common.result

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseResultTest {
    @Test
    fun `map transforms a parsed value`() {
        val result: ParseResult<Int> = ParseResult.Parsed(2)

        assertEquals(ParseResult.Parsed("2"), result.map { it.toString() })
    }

    @Test
    fun `map keeps a rejection without calling the transform`() {
        val result: ParseResult<Int> = ParseResult.Rejected(RejectReason.SIZE_LIMIT)

        assertEquals(ParseResult.Rejected(RejectReason.SIZE_LIMIT), result.map<Int, String> { error("not called") })
    }

    @Test
    fun `getOrNull returns the value or null`() {
        assertEquals(5, ParseResult.Parsed(5).getOrNull())
        assertNull(ParseResult.Rejected(RejectReason.MALFORMED).getOrNull())
    }
}
