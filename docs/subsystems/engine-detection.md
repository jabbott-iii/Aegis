# Subsystem: `engine:signatures`, `engine:heuristics`, `engine:ml` — detectors

The three detection engines that turn an app's metadata and static features
into signals: IOC matching, declarative heuristic rules, and the on-device
ML classifier. They don't decide verdicts. Each stage emits
`Signal(detectorId, ruleId, strength, evidence)`, and only
`VerdictAggregator` maps signals to a verdict (`engine-scanner.md`). Their
data (IOC sets, rules and models) ships in signed intel bundles and can be
updated without an app release (FR-DET-07, `intel.md`). `engine:signatures`
and `engine:heuristics` are pure Kotlin/JVM and must apply
`aegis.jvm.engine`. `engine:ml` wraps LiteRT and isn't one of the six pure
modules. Behavior is specified in `docs/features/malware-detection.md`
("Detection pipeline"). Nothing is built yet. Phase 1 builds IOC matching
and metadata heuristics v1; Phase 6 builds the declarative rules engine,
the ML classifier and the false-positive corpus.

## Contents

| Module | Provides |
| :- | :- |
| `engine:signatures` | Hash, package-plus-certificate and certificate IOC matching (FR-DET-01), including the stalkerware IOC set (FR-DET-05) |
| `engine:heuristics` | The declarative rules engine. Rules ship in the bundle (FR-DET-02, FR-DET-06) |
| `engine:ml` | The LiteRT model wrapper and feature vectors (FR-DET-04) |

## IOC matching (`engine:signatures`)

- **Indicators and strengths** (`docs/features/malware-detection.md`, "IOC
  matching (`engine:signatures`)"): an APK SHA-256 (the base APK and each
  split) or a package name plus signing-certificate SHA-256 pair is
  decisive. A certificate alone is strong, for certificates used only by
  malware families. A package name alone is weak: only a hint, because
  malware reuses legitimate names and the other way round.
- **Data:** the IOC sets are sorted arrays of hashes in the bundle,
  memory-mapped and searched by binary search.
- **Hashing** streams the file in 1 MiB chunks and never loads a whole APK
  into memory. A scan can be cancelled between chunks.
- **Re-matching:** after an intel bundle update, IOCs are re-matched for
  all apps, which is a cheap metadata match.
- **Stalkerware:** the IOC data comes from the public
  *stalkerware-indicators* project (CC-BY 4.0): package names,
  certificates, and C2 domains and IPs (`docs/data-sources.md`). A
  stalkerware match goes through the safety flow (`AGENTS.md` rule 13).

## Heuristic rules (`engine:heuristics`)

- **Rules are data.** They are declarative and live in the bundle as
  `rules.pb`. Each rule has an ID, a version, a severity and an explanation
  template. Rules and models are versioned with `minEngineVersion`.
- **Illustrative v1 rules:** H-BANKER-01, H-SPY-01, H-DROP-01, H-SMS-01,
  H-DEBUG-01 and H-STALE-01, with their conditions in
  `docs/features/malware-detection.md` ("Metadata heuristics
  (`engine:heuristics`)"). H-DROP-01 needs the static stage
  (`engine-apk.md`).
- **Outcomes:** heuristics on their own produce *Suspicious*, *PUA*, or,
  when a rule is marked high-confidence, *Malicious*. The thresholds are
  tuned against the benign corpus.
- **PUA and adware** categories can be turned off by the user (FR-DET-06).
- **By phase.** Phase 1's metadata heuristics v1 cover permission
  combinations (for example accessibility + overlay + SMS or notification
  access + sideloaded), debuggable release builds, disabled launcher
  entries, and a very old targetSdk with dangerous permissions. Phase 6
  builds the declarative engine whose rules ship in the bundle.

## ML classifier (`engine:ml`)

- **Model:** a gradient-boosted or small MLP classifier exported to LiteRT,
  at most 5 MB and at most 50 ms per app (spike S5).
- **Features:** permissions, components, API references, manifest and ZIP
  statistics, and the installer. Prefer features that are costly for an
  attacker to remove without losing malicious capability.
- **Training** happens offline, outside the app, either outside this repo
  or in `tools/ml/`. Dataset terms are respected and recorded
  (`docs/data-sources.md`, "ML training data").
- **Calibration:** pick the threshold that reaches a false-positive rate of
  0.1% or lower on the benign corpus, and report the TPR at that threshold.
- **Delivery:** the model ships in the signed bundle with
  `minEngineVersion`. It is signed and versioned, and can be rolled back.
- **Robustness** (`docs/features/ai-threat-defense.md`, "AI-9. Robustness
  of Aegis's own models"):
  - ML alone yields at most *Suspicious* (`AGENTS.md` rule 4, FR-DET-04).
  - Raw scores are not shown. Verdict thresholds are bucketed, which limits
    how useful Aegis is as an evasion oracle.
  - An adversarial suite runs in the lab before every model release.
- **Spike S5** also measures a small scam-text classifier (at most 10 MB
  and 30 ms per message), which the scam detector uses (`ai-guard.md`).

## Rules

- **Deterministic.** Verdicts come from IOCs, rules or fixed-threshold
  classifiers. No randomness, nothing that depends on the network, and no
  LLM in the verdict path (`AGENTS.md` rule 3, ADR-0005).
- **Evidence is bounded and human-readable:** at most 256 characters, and
  never raw file, message or page content.
- **Memory:** at most 150 MB for scan peaks (NFR-PERF-04). Prefer
  memory-mapped sorted hash arrays for large sets.
- **Engine conventions** (`.claude/rules/kotlin-engine.md`): explicit API
  mode, no `!!` (which `checkSourceRules` enforces under `engine/`), an
  injected `Clock` and `AegisDispatchers`, and cancellation checks at chunk
  boundaries.
- **`engine:ml` gets no explicit API mode and no coverage gate** from the
  build until open decision #8 is settled. Follow the engine rule by hand,
  and ask the owner before creating the module.

## Detection quality

- **The gates** are in `docs/04-quality.md` ("Detection quality"). Per
  release: 0 *Malicious* verdicts on the benign corpus, and *Suspicious* on
  0.5% of it or less.
- **Every change** to rules, the model or IOCs runs the benign corpus in CI
  against the pipeline's feature dumps. A release is blocked when it
  regresses a gate.
- **The false-positive corpus** (Phase 6): the top benign apps, evaluated on
  every model or rule change.
- **Adversarial evaluation** before each model release:
  `docs/04-quality.md` ("Adversarial evaluation").
- **Review:** rule, model, IOC and verdict changes go to the
  `detection-quality-reviewer` agent.

## Limits

| Limit | Value |
| :- | :- |
| Hash chunk | 1 MiB |
| ML model size | At most 5 MB |
| ML latency | At most 50 ms per app |
| ML false-positive rate at the threshold | At most 0.1% on the benign corpus |
| Evidence string | At most 256 characters |

## Tests

- **Unit tests** for every public function, with fakes over mocks for
  engine collaborators. Coverage must be at least 80% (NFR-MAINT-01);
  `./gradlew koverVerify` checks it for the `aegis.jvm.engine` modules only.
- **End to end:** the scanner's fixture verdict matrix, the AMTSO Android
  test artifacts, and the malicious-profile fixtures in
  `testing/fixtures` (`engine-scanner.md`, `testing.md`).

## Open questions

- **Where `Signal` lives.** The docs name it but don't place it in a
  module.
- **NFR-MAINT-01 for `engine:ml`** (`docs/00-overview.md`, "Open decisions"
  #8).
