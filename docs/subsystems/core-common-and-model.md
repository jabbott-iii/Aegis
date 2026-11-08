# Subsystem: `core:common` and `core:model` — results, logging, dispatchers, domain types

Foundation code that every other module may use. `core:common` holds the
parse result type for untrusted input, the PII-safe logging facade, and the
injected dispatchers. `core:model` holds the domain types, of which only
`Verdict` exists so far. Both are pure Kotlin/JVM modules: no `android.*` or
`androidx.*` imports, and both apply `aegis.jvm.engine` (explicit API mode,
warnings as errors, and the Kover ≥ 80% line-coverage gate). Phase 0 built
everything described here as built. The other domain models arrive with the
requirements that use them.

## Contents

| Module or package | Provides |
| :- | :- |
| `core:common` `result` | `ParseResult<T>` (`Parsed`, `Rejected`), `RejectReason`, and the `map` and `getOrNull` extensions |
| `core:common` `logging` | `AegisLogger`, `LogEvent`, `LogArea`, `LogLevel`, `LogPolicy` (`RELEASE`, `DEBUG`), `LogRecord`, `LogSink`, and `PolicyLogger`, the one implementation |
| `core:common` `coroutines` | `AegisDispatchers` (`default`, `io`, `main`) |
| `core:model` | `Verdict` and `Verdict.requiresSafetyFlow` |
| `core:model` (planned) | `AppRecord`, `Finding`, `Severity`, `IntelVersion`, and the other domain models (`docs/01-architecture.md`, "Module layout") |

`core:common` depends only on `kotlinx-coroutines-core`, as an `api`
dependency. `core:model` has no dependencies. There is no clock type: code
takes a `java.time.Clock` by injection, `app` provides `Clock.systemUTC()`,
and tests use `MutableClock` from `testing:common`.

## Key types

```kotlin
public sealed interface ParseResult<out T> {
    public data class Parsed<out T>(val value: T) : ParseResult<T>
    public data class Rejected(val reason: RejectReason) : ParseResult<Nothing>
}

public enum class RejectReason {           // a rejection carries no input content
    SIZE_LIMIT,                            // the input or an entry is bigger than the size limit
    ENTRY_LIMIT,                           // more entries (archive members, records, labels) than allowed
    DEPTH_LIMIT,                           // nesting (archives, XML elements, pointers) deeper than allowed
    COMPRESSION_RATIO,                     // an entry's ratio suggests a decompression bomb
    TIME_LIMIT,                            // parsing didn't finish within its time budget
    MALFORMED,                             // the input doesn't follow the format
    UNSUPPORTED,                           // well-formed, but a feature or version the parser doesn't handle
}

public inline fun <T, R> ParseResult<T>.map(transform: (T) -> R): ParseResult<R>  // keeps a rejection
public fun <T> ParseResult<T>.getOrNull(): T?

public interface AegisLogger {
    public fun <E> info(event: E, vararg values: Long) where E : Enum<E>, E : LogEvent
    public fun <E> warn(event: E, vararg values: Long) where E : Enum<E>, E : LogEvent
    public fun <E> error(event: E, vararg values: Long, cause: Throwable? = null) where E : Enum<E>, E : LogEvent
    public fun debug(message: () -> String)   // not evaluated when the policy drops it
}

public interface LogEvent { public val area: LogArea }  // implemented by an enum
public enum class LogArea { APP, DATA, SECURITY, SCANNER, INTEL, SHIELD, AI_GUARD, AUDIT }
public enum class LogLevel { DEBUG, INFO, WARN, ERROR }

public data class LogPolicy(val minLevel: LogLevel, val allowDebugText: Boolean) {
    public fun allows(level: LogLevel): Boolean         // level >= minLevel
    public companion object {
        public val RELEASE: LogPolicy                    // WARN and above, no free text
        public val DEBUG: LogPolicy                      // DEBUG and above, free text (to logcat only)
    }
}

public data class LogRecord(
    val level: LogLevel,
    val event: String,                     // "<area>.<constant name>", or "debug" for debug text
    val values: List<Long>,
    val errorType: String?,                // an exception class name (R8-minified in release)
    val debugText: String?,                // null unless the policy allows free text
)

public fun interface LogSink { public fun write(record: LogRecord) }
public class PolicyLogger(sink: LogSink, policy: LogPolicy) : AegisLogger

public interface AegisDispatchers {
    public val default: CoroutineDispatcher
    public val io: CoroutineDispatcher
    public val main: CoroutineDispatcher
}

public enum class Verdict {
    CLEAN, MALICIOUS, STALKERWARE,
    STALKERWARE_POSSIBLE,                  // a stalkerware heuristic or a weak stalkerware IOC fired
    PUA, SUSPICIOUS,
    UNSCANNABLE,                           // static parsing hit a limit; informational on its own
    ;
    public val requiresSafetyFlow: Boolean // true for STALKERWARE and STALKERWARE_POSSIBLE only
}
```

## Logging facade

The facade is the only logger Aegis code may use (`AGENTS.md` rule 8,
`docs/privacy.md`, "Logging"). `./gradlew checkSourceRules` fails the build
on the direct logging calls it recognizes (`Log`, `println`,
`System.out`/`err`, and others), and detekt bans the `android.util.Log`
import. The one adapter allowed to log is `app`'s `LogcatSink`.

- **Event identifiers are enum names.** A call site passes only an enum
  constant that implements `LogEvent`, plus numbers. The event identifier
  `<area>.<constant name>` is built from two enum names fixed at compile
  time, so per-call data such as a package name, domain, URL, file name or
  message text can't end up in it.
- **Values are counts, sizes or durations.** Never pass an identifier as a
  number: no UIDs, IP addresses, ports, hashes or timestamps. Reviewers
  check this, because the type system can't.
- **Errors keep the class name only.** `error(cause = …)` keeps the
  exception's class name, never its message. Exception messages can carry
  file names or URLs, so stack traces go into `debugText` only when the
  policy allows free text.
- **Free text is for debug builds.** `debug { … }` doesn't evaluate its
  lambda unless the policy allows the `DEBUG` level and free text.
  `LogPolicy.RELEASE` lets through `WARN` and above and no free text.
  `LogPolicy.DEBUG` lets through everything, to logcat only.
- **Each module names its own events.** `DataLogEvent`
  (`core-data.md`) and `SecurityLogEvent` (`core-security.md`) carry no
  database, file, setting or key material. `LogArea` has one constant per
  part of Aegis: `APP`, `DATA`, `SECURITY`, `SCANNER`, `INTEL`, `SHIELD`,
  `AI_GUARD` and `AUDIT`.

## Parse results

- Parsers of untrusted input return `ParseResult.Rejected(reason)` instead
  of throwing when the input is malformed or exceeds a limit (NFR-SEC-02,
  `AGENTS.md` rule 6, `.claude/rules/untrusted-input-parsers.md`).
- A rejection carries a `RejectReason` and nothing else: no input content.
- `map` returns a rejection unchanged without calling its transform.
- When static parsing fails on a limit, the verdict is *Unscannable*
  (`docs/features/malware-detection.md`, "Verdict aggregation").

## Dispatchers

Code never names `Dispatchers.*`. It injects `AegisDispatchers`, or a
`CoroutineDispatcher` taken from it. `app`'s `DispatchersModule` is the only
binding to the platform dispatchers, and tests use `TestAegisDispatchers`
(`testing.md`). `checkSourceRules` fails on `Dispatchers.IO`, `Default`,
`Main` and `Unconfined`, on `GlobalScope`, and on `runBlocking` in every
module's non-test sources except the `testing:*` helpers.
`DispatchersModule` is exempt from the `Dispatchers.*` rule only.

## Verdict

`Verdict` is the outcome of scanning one target (FR-SCAN-07). The rows of
the precedence table in `docs/features/malware-detection.md` ("Verdict
aggregation") decide which verdict applies, and only `VerdictAggregator`
maps detector signals to a verdict (`engine-scanner.md`).
`requiresSafetyFlow` marks the two stalkerware verdicts: they go through the
safety flow and are never removed in one tap (`AGENTS.md` rule 13,
`docs/features/device-security-audit.md`, "Stalkerware safety flow").

## Rules

Both modules follow `.claude/rules/kotlin-engine.md`:

- **Pure Kotlin/JVM.** `checkModuleGraph` fails if either applies an Android
  plugin, or reaches an Android module or library (`androidx`,
  `com.android`, `com.google.android`, `org.robolectric`) on a production
  classpath, even transitively. Tests may use Android libraries, but neither
  module may declare a dependency on an Android module in any
  configuration.
- **No `!!`.** `checkSourceRules` fails on it under `core/common/` and
  `core/model/`.
- **Explicit API mode.** Every public declaration has an explicit
  visibility and return type. Keep the public surface small.
- **Compiled against the JDK 17 API** (`-Xjdk-release=17`), so nothing
  slips in that Android can't run.

## Invariants

- A `LogRecord`'s event is either `<area>.<constant name>` from two enum
  names or the literal `debug`.
- Under `LogPolicy.RELEASE`, no record carries free text, a stack trace or
  an exception message, and a `debug` lambda never runs.
- A parser rejection carries no input content.
- Only `STALKERWARE` and `STALKERWARE_POSSIBLE` require the safety flow.

## Tests

- `ParseResultTest`: `map` transforms a parsed value; `map` keeps a
  rejection without calling the transform; `getOrNull` returns the value or
  null.
- `PolicyLoggerTest`:
  - the release policy drops `INFO` and keeps `WARN` with the event code
    and values;
  - the release policy never evaluates debug text;
  - the release policy keeps only the exception type, never its message (a
    `FileNotFoundException` that names a file doesn't leak it);
  - the debug policy passes `INFO`, debug text and stack traces;
  - debug text is dropped when the policy disallows free text;
  - a policy allows its minimum level and above.
- `VerdictTest`: only the stalkerware verdicts require the safety flow.
- The tests run on JUnit 5. `./gradlew koverVerify` checks the ≥ 80% line
  coverage (NFR-MAINT-01).

## Open questions

- **NFR-MAINT-01 outside the pure modules.** `aegis.jvm.engine` gives only
  the six pure modules explicit API mode and the coverage gate
  (`docs/00-overview.md`, "Open decisions" #8).
