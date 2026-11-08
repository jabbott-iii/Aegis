# Subsystem: `engine:scanner` — scan orchestration and verdicts

Turns triggers into scan jobs, runs the detectors in order of cost, and
combines their signals into one verdict. It's the Android side of the
engine: platform adapters that the pure engine modules need live here (or
in `shield:vpn` and `app`). It runs in the main process and hands APKs to
`engine:apk` in the `:parser` process as `ParcelFileDescriptor`s over AIDL.
It isn't one of the six pure modules. Behavior is specified in
`docs/features/malware-detection.md`. Nothing is built yet. Phase 1 builds
the app inventory, change detection, the IOC and metadata-heuristic stages,
and single-file scanning; Phase 5 adds the AI guard checks; Phase 6 adds the
static-feature and ML stages.

## Contents

| Component | Provides |
| :- | :- |
| `ScanOrchestrator` | Turns triggers (package change, schedule, user action, new file) into scan jobs. Deduplicates and caches by `(path, size, lastUpdateTime)` |
| `DetectorPipeline` | Runs the detectors in order of cost: IOC → metadata heuristics → AI guard → static features → ML. A decisive result stops it early |
| `VerdictAggregator` | Applies the precedence rules that combine detector signals into one verdict, so that ML on its own can never produce *Malicious* |
| `Detector` (extension point) | `Detector { suspend fun evaluate(target: ScanTarget, ctx: DetectionContext): List<Signal> }`, which lets the AI guard and future detectors plug in without changing the orchestrator (`docs/01-architecture.md`, "Extensibility points") |

The verdict type, `Verdict`, is built in `core:model`
(`core-common-and-model.md`).

## Pipeline

Stages run from cheapest to most expensive, and each emits
`Signal(detectorId, ruleId, strength, evidence)`. The sequence for a new
app is in `docs/01-architecture.md` ("New app installed").

| Stage | Where it runs | Spec |
| :- | :- | :- |
| Collect metadata | Main process | `docs/features/malware-detection.md`, "Metadata collection (main process)" |
| IOC match | `engine:signatures` | `engine-detection.md` |
| Metadata heuristics | `engine:heuristics` | `engine-detection.md` |
| AI guard checks (impersonation, AI SDK) | `ai:guard` | `ai-guard.md` |
| Static features | `engine:apk`, in `:parser` | `engine-apk.md` |
| ML classifier | `engine:ml` | `engine-detection.md` |
| Aggregate | `VerdictAggregator` | "Verdict aggregation" below |

- **Metadata** comes from `PackageInfo` (signing certificates with their
  rotation history, permissions, components), the installer source, the
  enabled privileged components, flags such as `FLAG_DEBUGGABLE`, and a
  content fingerprint from `(sourceDir, splitSourceDirs, size,
  lastUpdateTime)`. The fingerprint is the cache key that decides whether
  hashing is needed.
- **The app inventory** (FR-SCAN-01) records each package's version,
  signing-certificate SHA-256, installer source, requested and granted
  permissions, and privileged components.

## Verdict aggregation

`VerdictAggregator` is the only place that maps signals to a verdict. It
uses the precedence table in `docs/features/malware-detection.md`
("Verdict aggregation"), and these rules hold whatever the table says:

- **ML alone never yields *Malicious*.** At most *Suspicious*
  (`AGENTS.md` rule 4, FR-DET-04).
- **No LLM in the verdict path** (`AGENTS.md` rule 3, ADR-0005). No
  randomness, and nothing that depends on the network.
- **Stalkerware goes through the safety flow.** A stalkerware heuristic or
  a weak stalkerware IOC gives *Stalkerware (possible)*, never one-tap
  removal (`AGENTS.md` rule 13).
- **A parser limit is *Unscannable*.** It's informational, and becomes
  *Suspicious* if the app is also sideloaded.

Every finding has a verdict, a severity, the detector or rule that fired,
the evidence, and a plain-language explanation (FR-SCAN-07). Findings are
stored as `Finding` rows in `app.db` (`core-data.md`). An alert goes out
when the severity reaches the threshold.

## Triggers

The triggers are in `docs/features/malware-detection.md` ("Triggers"). In
short:

- **Install or update:** `PackageManager.getChangedPackages(seq)` with a
  stored sequence number, run on app start, every 15 min through
  WorkManager, and from a runtime-registered package receiver while
  `:shield` runs. Manifest-declared implicit package broadcasts have been
  restricted since API 26 and aren't relied on.
- **On demand:** a full scan with progress, cancellation and resume
  (FR-SCAN-03).
- **Scheduled:** daily by default, with battery-not-low and idle
  constraints (FR-SCAN-04). It's incremental: only packages whose
  fingerprint changed, or that haven't been re-checked since an intel
  version bump.
- **Intel bundle updated:** re-match IOCs for all apps. The static and ML
  stages re-run only if their rules or model changed.
- **New file in shared storage** (with All files access) and **single
  file** (system picker or share-to-Aegis, FR-SCAN-06).

Android gives third-party apps no hook to scan a package before it is
installed. Aegis can catch an APK *file* before the user opens it (with All
files access), and otherwise it scans immediately after install. The UI says
this plainly (`docs/features/malware-detection.md`, "Triggers").

How each trigger runs in the background, including the foreground-service
type that spike S4 decides for a full scan that continues after the user
leaves, is in `docs/01-architecture.md` ("Background execution").

## Trust and remediation

- **Trust rules** are pinned to `(packageName, certSha256)`. An update
  signed by a different certificate re-opens the finding (FR-SCAN-09).
- **Remediation** (uninstall prompt, app settings, device admin guidance,
  quarantine) is in `docs/features/malware-detection.md` ("Remediation"),
  and its screens are in `features.md`.

## Performance

- **First full scan:** hash every APK, with throughput measured in S4.
  After that, the cache keyed by content fingerprint avoids re-hashing.
- **Parallelism:** at most 2 concurrent `:parser` analyses, reduced to 1
  when the device is in thermal throttling or battery saver.
- **Budgets:** NFR-PERF-01 (a full scan of 150 installed apps in 2 min or
  less, with warm caches), NFR-PERF-04 (scan worker peak memory 150 MB or
  less), and FR-SCAN-02 (a verdict within 10 s of detecting the change).

## Rules

- **Engine conventions** (`.claude/rules/kotlin-engine.md`): no `!!` (which
  `checkSourceRules` enforces under `engine/`), an injected `Clock` and
  `AegisDispatchers`, cancellation checks in long loops, and typed results
  at module boundaries.
- **No build-enforced explicit API mode or coverage gate.** `engine:scanner`
  gets neither until open decision #8 is settled. Follow the engine rule by
  hand, and ask the owner before creating the module.

## Tests

- **The scanner test plan** (`docs/04-quality.md`, "Feature test plans"):
  the fixture verdict matrix, the zip-bomb and malformed-DEX corpus,
  install-to-verdict latency, and trust pinning after a certificate change.
- **Acceptance** (`docs/features/malware-detection.md`, "Acceptance
  criteria (selected)"):
  - the AMTSO Android test artifacts and every malicious-profile fixture in
    `testing/fixtures` produce the expected verdicts;
  - 0 *Malicious* verdicts on the benign corpus, and *Suspicious* on 0.5% or
    less of it, per release;
  - a fixture zip bomb produces *Unscannable* within 30 s, with no crash in
    the main process;
  - installing a fixture APK produces a verdict notification within 10 s of
    change detection;
  - a trusted app that updates with a different certificate re-opens its
    finding.
- **Layers:** Robolectric for adapter code paths that don't need real
  system services, and instrumented tests for `PackageManager` flows and
  uninstall prompts.

## Open questions

- **Where `Detector`, `ScanTarget`, `DetectionContext` and `Signal` live.**
  The docs name them but don't place them in a module.
- **NFR-MAINT-01 for this module** (`docs/00-overview.md`, "Open decisions"
  #8).
