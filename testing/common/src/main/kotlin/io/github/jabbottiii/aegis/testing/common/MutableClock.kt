package io.github.jabbottiii.aegis.testing.common

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Clock] that only moves when a test calls [advanceBy] or [set]. It is thread-safe, and clocks
 * returned by [withZone] share its instant, so advancing one advances all of them.
 */
class MutableClock private constructor(
    private val now: AtomicReference<Instant>,
    private val zone: ZoneId,
) : Clock() {
    constructor(
        start: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        zone: ZoneId = ZoneOffset.UTC,
    ) : this(AtomicReference(start), zone)

    override fun instant(): Instant = now.get()

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    fun advanceBy(duration: Duration) {
        now.updateAndGet { it.plus(duration) }
    }

    fun set(instant: Instant) {
        now.set(instant)
    }

    override fun equals(other: Any?): Boolean = other is MutableClock && other.now === now && other.zone == zone

    override fun hashCode(): Int = System.identityHashCode(now) * 31 + zone.hashCode()
}
