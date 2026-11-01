package io.github.jabbottiii.aegis.testing.common

import io.github.jabbottiii.aegis.core.common.logging.LogLevel
import io.github.jabbottiii.aegis.core.common.logging.LogRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestHelpersTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `work on any injected dispatcher runs under runTest's scheduler and virtual time`() =
        runTest {
            val dispatchers = TestAegisDispatchers(this)
            var finished = false

            launch(dispatchers.io) {
                delay(5_000)
                withContext(dispatchers.default) { finished = true }
            }
            advanceUntilIdle()

            assertTrue(finished)
            assertEquals(5_000, currentTime)
        }

    @Test
    fun `zoned clocks share the instant of the clock they came from`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val zoned = clock.withZone(ZoneId.of("Europe/Berlin"))

        clock.advanceBy(Duration.ofMinutes(5))

        assertEquals(Instant.parse("2026-01-01T00:05:00Z"), zoned.instant())
        assertEquals(ZoneId.of("Europe/Berlin"), zoned.zone)
    }

    @Test
    fun `set moves the clock to an exact instant`() {
        val clock = MutableClock()
        clock.set(Instant.parse("2027-03-04T05:06:07Z"))

        assertEquals(Instant.parse("2027-03-04T05:06:07Z"), clock.instant())
    }

    @Test
    fun `clocks are equal only when they share state and zone`() {
        val clock = MutableClock()

        assertEquals(clock, clock.withZone(clock.zone))
        assertEquals(clock.hashCode(), clock.withZone(clock.zone).hashCode())
        assertNotEquals(clock, MutableClock())
        assertNotEquals<java.time.Clock>(clock, clock.withZone(ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `recording sink keeps every record written from several threads`() {
        val sink = RecordingLogSink()
        val record = LogRecord(LogLevel.WARN, "test.event", emptyList(), errorType = null, debugText = null)

        (1..8).map { thread { repeat(500) { sink.write(record) } } }.forEach { it.join() }

        assertEquals(4_000, sink.records.size)
    }
}
