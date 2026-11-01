package io.github.jabbottiii.aegis.core.common.logging

/** The [AegisLogger] implementation: applies [policy], then hands each record to [sink]. */
public class PolicyLogger(
    private val sink: LogSink,
    private val policy: LogPolicy,
) : AegisLogger {
    override fun <E> info(
        event: E,
        vararg values: Long,
    ) where E : Enum<E>, E : LogEvent {
        emit(LogLevel.INFO, event, values, cause = null)
    }

    override fun <E> warn(
        event: E,
        vararg values: Long,
    ) where E : Enum<E>, E : LogEvent {
        emit(LogLevel.WARN, event, values, cause = null)
    }

    override fun <E> error(
        event: E,
        vararg values: Long,
        cause: Throwable?,
    ) where E : Enum<E>, E : LogEvent {
        emit(LogLevel.ERROR, event, values, cause)
    }

    override fun debug(message: () -> String) {
        if (!policy.allowDebugText || !policy.allows(LogLevel.DEBUG)) return
        sink.write(
            LogRecord(
                level = LogLevel.DEBUG,
                event = DEBUG_EVENT,
                values = emptyList(),
                errorType = null,
                debugText = message(),
            ),
        )
    }

    private fun <E> emit(
        level: LogLevel,
        event: E,
        values: LongArray,
        cause: Throwable?,
    ) where E : Enum<E>, E : LogEvent {
        if (!policy.allows(level)) return
        sink.write(
            LogRecord(
                level = level,
                // Enum names only: neither part can carry per-call data.
                event = "${event.area.name}.${event.name}",
                values = values.toList(),
                errorType = cause?.javaClass?.name,
                // Exception messages can carry file names or URLs, so stack traces are debug-only.
                debugText = if (policy.allowDebugText) cause?.stackTraceToString() else null,
            ),
        )
    }

    private companion object {
        const val DEBUG_EVENT = "debug"
    }
}
