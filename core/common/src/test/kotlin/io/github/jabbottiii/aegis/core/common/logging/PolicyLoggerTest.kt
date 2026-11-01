package io.github.jabbottiii.aegis.core.common.logging

import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolicyLoggerTest {
    private enum class TestEvent(
        override val area: LogArea,
    ) : LogEvent {
        SCAN_FINISHED(LogArea.SCANNER),
        ENGINE_FAILED(LogArea.SHIELD),
    }

    private val records = mutableListOf<LogRecord>()
    private val sink = LogSink { records += it }

    @Test
    fun `release policy drops info and keeps warn with event code and values`() {
        val logger = PolicyLogger(sink, LogPolicy.RELEASE)

        logger.info(TestEvent.SCAN_FINISHED, 3)
        logger.warn(TestEvent.SCAN_FINISHED, 3, 150)

        val expected =
            LogRecord(
                level = LogLevel.WARN,
                event = "SCANNER.SCAN_FINISHED",
                values = listOf(3L, 150L),
                errorType = null,
                debugText = null,
            )
        assertEquals(listOf(expected), records)
    }

    @Test
    fun `release policy never evaluates debug text`() {
        val logger = PolicyLogger(sink, LogPolicy.RELEASE)
        var evaluated = false

        logger.debug {
            evaluated = true
            "com.example.app"
        }

        assertFalse(evaluated)
        assertTrue(records.isEmpty())
    }

    @Test
    fun `release policy keeps only the exception type, never its message`() {
        val logger = PolicyLogger(sink, LogPolicy.RELEASE)

        logger.error(TestEvent.ENGINE_FAILED, cause = FileNotFoundException("/sdcard/Download/secret.apk"))

        val record = records.single()
        assertEquals(LogLevel.ERROR, record.level)
        assertEquals("SHIELD.ENGINE_FAILED", record.event)
        assertEquals("java.io.FileNotFoundException", record.errorType)
        assertNull(record.debugText)
        assertFalse(record.toString().contains("secret.apk"))
    }

    @Test
    fun `debug policy passes info, debug text, and stack traces`() {
        val logger = PolicyLogger(sink, LogPolicy.DEBUG)

        logger.info(TestEvent.SCAN_FINISHED)
        logger.debug { "detail" }
        logger.error(TestEvent.ENGINE_FAILED, 7, cause = IllegalStateException("boom"))

        assertEquals(listOf(LogLevel.INFO, LogLevel.DEBUG, LogLevel.ERROR), records.map { it.level })
        assertEquals("detail", records[1].debugText)
        assertEquals(listOf(7L), records[2].values)
        assertNotNull(records[2].debugText).also { assertTrue(it.contains("boom")) }
    }

    @Test
    fun `debug text is dropped when the policy disallows free text`() {
        val logger = PolicyLogger(sink, LogPolicy(minLevel = LogLevel.DEBUG, allowDebugText = false))

        logger.debug { "detail" }

        assertTrue(records.isEmpty())
    }

    @Test
    fun `policy allows its minimum level and above`() {
        assertFalse(LogPolicy.RELEASE.allows(LogLevel.INFO))
        assertTrue(LogPolicy.RELEASE.allows(LogLevel.WARN))
        assertTrue(LogPolicy.RELEASE.allows(LogLevel.ERROR))
        assertTrue(LogPolicy.DEBUG.allows(LogLevel.DEBUG))
    }
}
