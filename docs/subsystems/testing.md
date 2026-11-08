# Subsystem: `testing:common` and `testing:fixtures` — test helpers and benign fixtures

`testing:common` holds the helpers that tests in several modules share.
`testing:fixtures` will hold benign fixture APKs that emulate risky app
profiles; it never holds real malware (`AGENTS.md` rule 12). Phase 0 built
`testing:common` with injected test dispatchers, a controllable clock, and a
recording log sink. Nothing in `testing:fixtures` is built yet, and M1's
exit criteria need its fixture apps. The test layers, gates and
malware-sample handling are in `docs/04-quality.md`.

## Contents

| Module or class | Provides |
| :- | :- |
| `testing:common` `TestAegisDispatchers` | `AegisDispatchers` that route `default`, `io` and `main` to one `StandardTestDispatcher` on the test's own scheduler |
| `testing:common` `MutableClock` | A `java.time.Clock` that moves only when a test calls `advanceBy` or `set` |
| `testing:common` `RecordingLogSink` | A `LogSink` that collects records, so tests can assert that nothing sensitive was logged |
| `testing:common` fixtures loader (planned) | The architecture lists a fixtures loader here; none exists yet |
| `testing:fixtures` (planned) | Benign fixture APKs, built from source |

`testing:common` applies `aegis.jvm.library` (plain Kotlin/JVM, compiled
against the JDK 17 API, with JUnit 5 for its own tests). It has `api`
dependencies on `core:common` and `kotlinx-coroutines-test`, so a test that
uses the helpers gets both. `checkSourceRules` skips the `testing:*`
helpers, which may print.

## Key types

```kotlin
class TestAegisDispatchers(scheduler: TestCoroutineScheduler) : AegisDispatchers {
    constructor(scope: TestScope)            // uses scope.testScheduler
    val testDispatcher: TestDispatcher       // StandardTestDispatcher(scheduler)
    override val default: TestDispatcher     // all three return testDispatcher
    override val io: TestDispatcher
    override val main: TestDispatcher
}

class MutableClock(
    start: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    fun advanceBy(duration: Duration)
    fun set(instant: Instant)
    override fun withZone(zone: ZoneId): Clock   // shares this clock's instant
}

class RecordingLogSink : LogSink {
    val records: List<LogRecord>                 // a snapshot of everything written
}
```

## Invariants

- **One scheduler.** Under `runTest`, work on any dispatcher from
  `TestAegisDispatchers` runs on `runTest`'s scheduler, so the test controls
  all scheduling, including virtual time (`advanceUntilIdle`).
- **Clocks share time.** `MutableClock` is thread-safe. A clock returned by
  `withZone` shares the original's instant, so advancing one advances all
  of them. Two clocks are equal only when they share that state and have
  the same zone.
- **No lost records.** `RecordingLogSink` is safe to write from several
  threads.

## Users today

- `core:security`: `WrappedSecretStoreTest` (JVM) and
  `KeystoreKeyWrapperTest` (instrumented) use `TestAegisDispatchers` and
  `RecordingLogSink` (`core-security.md`).
- `core:data`: `EncryptedDatabaseFactoryTest` and `SettingsDataStoreTest`
  (instrumented) use `RecordingLogSink`, and the factory test uses
  `TestAegisDispatchers` (`core-data.md`).

## Fixture APKs (`testing:fixtures`, planned)

- **Benign, built from source.** Fixtures *emulate* risky profiles: they
  request the banking-trojan permission set, declare accessibility or admin
  components, embed a dummy DEX in assets, and so on. They do nothing
  harmful. Each is signed with a test key that is clearly named as a test
  key.
- **The one place an AccessibilityService may appear.** Fixtures under
  `testing/fixtures/` may declare one so that they emulate risky apps; the
  Aegis app never does (`AGENTS.md` rule 2, ADR-0004).
- **Their own convention plugin.** `aegis.android.application` is only for
  `:app`, because it sets Aegis's applicationId and release signing.
  Fixture APKs need their own plugin, with their own IDs and a test-named
  key (`docs/04-quality.md`, "Malware sample handling").
- **What the specs call for:**
  - the malicious-profile fixtures that, with the AMTSO Android test
    artifacts, must produce the expected verdicts (M1);
  - a fixture zip bomb and a malformed-DEX corpus (`engine-apk.md`);
  - AI-1 lookalikes: "ChatGPT AI Chat", sideloaded and requesting SMS, and
    `com.openai.chatgpt` signed with a test key (`ai-guard.md`);
  - a fixture accessibility service and notification listener, for the
    audit and the agent guard;
  - a stalkerware fixture, whose notification must never contain the word
    "stalkerware" on the lock screen;
  - a fixture app that connects to an address inside a test-only prefix
    injected through a debug bundle, for the AI Crawler Shield
    (`shield.md`).
- **Never real malware.** Live malware never goes into this repository, its
  history or LFS, and samples are never downloaded into this working tree
  (`AGENTS.md` rule 12). Real-sample evaluation runs in an isolated lab
  outside the repo, and only aggregate metrics come back
  (`docs/04-quality.md`, "Malware sample handling").

## Rules

`.claude/rules/tests-and-fixtures.md` applies to `testing/**` and to every
module's test source sets.

## Tests

- `TestHelpersTest` (JUnit 5):
  - work on any injected dispatcher runs under `runTest`'s scheduler and
    virtual time (a 5,000 ms delay finishes at virtual time 5,000);
  - zoned clocks share the instant of the clock they came from;
  - `set` moves the clock to an exact instant;
  - clocks are equal only when they share state and zone;
  - the recording sink keeps every record written from eight threads
    (4,000 records).

## Open questions

- **The fixtures loader** that the architecture lists for `testing:common`
  has no design yet.
