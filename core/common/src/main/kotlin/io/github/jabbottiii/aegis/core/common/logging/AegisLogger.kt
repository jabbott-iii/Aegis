package io.github.jabbottiii.aegis.core.common.logging

/**
 * The only logger Aegis code may use (docs/privacy.md, "Logging"). `./gradlew checkSourceRules`
 * fails the build on the direct logging calls it recognizes (Log, println, System.out/err, and
 * others).
 *
 * Call sites can pass only an enum constant that implements [LogEvent], plus numbers. A record's
 * event identifier is `<area>.<constant name>`, built from two enum names that are fixed at
 * compile time, so per-call data such as a package name, domain, URL, file name, or message text
 * can't end up in it. Free text goes through [debug], which runs only when the [LogPolicy] allows
 * it (debug builds).
 *
 * `values` must be counts, sizes, or durations. Never pass an identifier as a number: no UIDs, IP
 * addresses, ports, hashes, or timestamps. Reviewers check this, because the type system can't.
 */
public interface AegisLogger {
    public fun <E> info(
        event: E,
        vararg values: Long,
    ) where E : Enum<E>, E : LogEvent

    public fun <E> warn(
        event: E,
        vararg values: Long,
    ) where E : Enum<E>, E : LogEvent

    /** Logs [event]. Only the exception's class name is kept from [cause], never its message. */
    public fun <E> error(
        event: E,
        vararg values: Long,
        cause: Throwable? = null,
    ) where E : Enum<E>, E : LogEvent

    /** Free-text detail for debug builds. [message] isn't evaluated when the policy drops it. */
    public fun debug(message: () -> String)
}

/** Implement it on an enum. The constant's name is the event, and [area] says which part of Aegis logged it. */
public interface LogEvent {
    public val area: LogArea
}

/** The part of Aegis that logged an event (docs/01-architecture.md, "Module layout"). */
public enum class LogArea {
    APP,
    DATA,
    SECURITY,
    SCANNER,
    INTEL,
    SHIELD,
    AI_GUARD,
    AUDIT,
}

public enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/** What a [PolicyLogger] lets through. */
public data class LogPolicy(
    val minLevel: LogLevel,
    val allowDebugText: Boolean,
) {
    public fun allows(level: LogLevel): Boolean = level >= minLevel

    public companion object {
        /** Release builds log nothing at INFO or below, and no free text. */
        public val RELEASE: LogPolicy = LogPolicy(minLevel = LogLevel.WARN, allowDebugText = false)

        /** Debug builds may log free text, to logcat only. */
        public val DEBUG: LogPolicy = LogPolicy(minLevel = LogLevel.DEBUG, allowDebugText = true)
    }
}

/**
 * One log line, as handed to a [LogSink]. [event] is `<area>.<constant name>`. [errorType] is an
 * exception class name (R8-minified in release). [debugText] is null unless the policy allows
 * free text.
 */
public data class LogRecord(
    val level: LogLevel,
    val event: String,
    val values: List<Long>,
    val errorType: String?,
    val debugText: String?,
)

public fun interface LogSink {
    public fun write(record: LogRecord)
}
