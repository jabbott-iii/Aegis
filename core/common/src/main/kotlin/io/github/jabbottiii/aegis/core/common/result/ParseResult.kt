package io.github.jabbottiii.aegis.core.common.result

/**
 * The result of parsing untrusted input (NFR-SEC-02). Parsers return [Rejected] instead of
 * throwing when the input is malformed or exceeds a limit.
 */
public sealed interface ParseResult<out T> {
    public data class Parsed<out T>(
        val value: T,
    ) : ParseResult<T>

    public data class Rejected(
        val reason: RejectReason,
    ) : ParseResult<Nothing>
}

/** Why a parser rejected its input. A rejection carries no input content. */
public enum class RejectReason {
    /** The input or an entry is bigger than the parser's size limit. */
    SIZE_LIMIT,

    /** The input has more entries (archive members, records, labels) than allowed. */
    ENTRY_LIMIT,

    /** Nesting (archives, XML elements, pointers) is deeper than allowed. */
    DEPTH_LIMIT,

    /** An entry's uncompressed-to-compressed ratio suggests a decompression bomb. */
    COMPRESSION_RATIO,

    /** Parsing didn't finish within its time budget. */
    TIME_LIMIT,

    /** The input doesn't follow the format. */
    MALFORMED,

    /** The input is well-formed but uses a feature or version the parser doesn't handle. */
    UNSUPPORTED,
}

public inline fun <T, R> ParseResult<T>.map(transform: (T) -> R): ParseResult<R> =
    when (this) {
        is ParseResult.Parsed -> ParseResult.Parsed(transform(value))
        is ParseResult.Rejected -> this
    }

public fun <T> ParseResult<T>.getOrNull(): T? =
    when (this) {
        is ParseResult.Parsed -> value
        is ParseResult.Rejected -> null
    }
