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

- **Pure Kotlin where specified.** `engine:apk`, `engine:signatures`, `engine:heuristics`, `shield:dns`, `core:model`, and `core:common` must not import `android.*` or `androidx.*`. Put platform calls behind an interface whose adapter lives in `engine:scanner`, `shield:vpn`, or `app`.
- **Explicit API mode.** Every public declaration needs an explicit visibility and return type. Keep the public surface small and prefer `internal`.
- **No `!!`**, no unchecked casts on untrusted data, and no `lateinit` for state that can be missing.
- **Inject time and concurrency.** Take a `Clock` or time-source parameter and a `CoroutineDispatcher`. Never call `System.currentTimeMillis()`, `Dispatchers.IO`, or `GlobalScope` directly. Never use `runBlocking` in main sources.
- **Cancellation.** Long loops (hashing, parsing, scanning) check `ensureActive()` or `isActive` at chunk or entry boundaries. Hashing streams 1 MiB chunks and never loads a whole APK into memory.
- **Typed results at module boundaries.** Use sealed result types. Exceptions are for programmer errors only. Parsers return `ParseResult.Rejected(reason)` and never throw on malformed input.
- **Deterministic verdicts.** Detectors emit `Signal(detectorId, ruleId, strength, evidence)`. Only `VerdictAggregator` maps signals to verdicts, using the precedence table in `docs/features/malware-detection.md` §3.6. No randomness, and nothing that depends on the network.
- **Evidence is bounded and human-readable.** Keep evidence strings to 256 chars or fewer, and never include raw file, message, or page content.
- **Memory budgets.** NFR-PERF-04: 150 MB or less for scan peaks, and 60 MB or less for the Shield at steady state. Prefer memory-mapped sorted hash arrays for large sets.
- **Tests.** Unit-test every public function. Coverage must be at least 80% (NFR-MAINT-01). Use fakes over mocks for engine collaborators.
