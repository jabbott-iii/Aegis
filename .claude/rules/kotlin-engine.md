---
paths:
  - "engine/**/*.kt"
  - "shield/dns/**/*.kt"
  - "ai/guard/**/*.kt"
  - "intel/client/**/*.kt"
  - "core/model/**/*.kt"
  - "core/common/**/*.kt"
---

# Engine and core Kotlin rules

These modules decide verdicts and parse hostile data, so they must be deterministic, fast to test, and safe.

- **Pure Kotlin where specified.** `engine:apk`, `engine:signatures`, `engine:heuristics`, `shield:dns`, `core:model`, and `core:common` must not import `android.*` or `androidx.*`. Put platform calls behind an interface whose adapter lives in `engine:scanner`, `shield:vpn`, or `app`. These six must apply `aegis.jvm.engine` (only `core:model` and `core:common` exist so far). The `checkModuleGraph` CI gate fails if one doesn't, if one applies an Android plugin, or if one reaches an Android module or library on a production classpath, even transitively. Tests may use Android libraries (such as Robolectric), but a pure module can't declare a dependency on an Android module in any configuration, including tests. Whether `ai:guard` can be pure is still open (ARCHITECTURE §3).
- **Explicit API mode.** Every public declaration needs an explicit visibility and return type. Keep the public surface small and prefer `internal`.
- **No `!!`**, no unchecked casts on untrusted data, and no `lateinit` for state that can be missing.
- **Inject time and concurrency.** Take a `Clock` or time-source parameter, and inject `AegisDispatchers` from `core:common` (or a `CoroutineDispatcher` taken from it). Never call `System.currentTimeMillis()`, `Dispatchers.IO`, or `GlobalScope` directly. Never use `runBlocking` in main sources.
- **What the build enforces today.**
  - The `checkSourceRules` CI gate scans every module's non-test sources, except the `testing:*` helpers. It fails on `!!` under `engine/`, `shield/`, `ai/`, `intel/`, `core/security/`, `core/common/`, and `core/model/`. Everywhere it scans, it also fails on `Dispatchers.IO`/`Default`/`Main`/`Unconfined`, `GlobalScope`, `runBlocking`, and direct logging. The app's `LogcatSink` may log, and its `DispatchersModule` may name `Dispatchers.*`. Nothing else is exempt.
  - `aegis.jvm.engine` adds explicit API mode, `allWarningsAsErrors`, and the Kover ≥ 80% line-coverage gate, but only to the six pure modules. The other modules this rule covers (`engine:scanner`, `engine:ml`, `intel:client`, and `ai:guard` unless it becomes pure) get none of the three, whether they apply `aegis.android.library` or `aegis.jvm.library`.
  - How to enforce NFR-MAINT-01 for those modules is an open owner decision (PLAN §6 #8). Ask the owner before you create the first of them, and follow this rule by hand until the build enforces it.
  - Everything else in this rule is checked in review. That includes unchecked casts, `lateinit`, the clock and `System.currentTimeMillis()`, cancellation, typed results, deterministic verdicts, evidence bounds, and memory budgets.
- **Cancellation.** Long loops (hashing, parsing, scanning) check `ensureActive()` or `isActive` at chunk or entry boundaries. Hashing streams 1 MiB chunks and never loads a whole APK into memory.
- **Typed results at module boundaries.** Use sealed result types. Exceptions are for programmer errors only. Parsers return `ParseResult.Rejected(reason)` and never throw on malformed input.
- **Deterministic verdicts.** Detectors emit `Signal(detectorId, ruleId, strength, evidence)`. Only `VerdictAggregator` maps signals to verdicts, using the precedence table in `docs/features/malware-detection.md` §3.6. No randomness, and nothing that depends on the network.
- **Evidence is bounded and human-readable.** Keep evidence strings to 256 chars or fewer, and never include raw file, message, or page content.
- **Memory budgets.** NFR-PERF-04: 150 MB or less for scan peaks, and 60 MB or less for the Shield at steady state. Prefer memory-mapped sorted hash arrays for large sets.
- **Tests.** Unit-test every public function. Coverage must be at least 80% (NFR-MAINT-01). `./gradlew koverVerify` checks it for the `aegis.jvm.engine` modules only. Use fakes over mocks for engine collaborators.
