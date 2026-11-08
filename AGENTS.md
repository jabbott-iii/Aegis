# Aegis — an Android security app (Kotlin)

**Status:** Phase 0 is in progress. The Gradle scaffold, the CI workflows,
and the foundation modules (`app`, `core:common`, `core:data`, `core:model`,
`core:security`, `core:ui`, `testing:common`) exist, including encrypted
Room and the settings DataStore. The app is a shell with no protection yet.
CI on GitHub hasn't passed yet. The dependency-verification failure is
fixed locally but not yet confirmed on GitHub (`README.md`, "Status"). Still
to do in Phase 0: a clean CI run on `main`, spikes S1–S7, and accepting or
rejecting the ADRs (all seven are *Proposed*; only the user accepts one).
`docs/roadmap.md` ticks each task once it is done and verified. The next
task is the first unticked one whose dependencies are all ticked. This
document also describes modules and commands that don't exist yet: check
before assuming.

## What we are building

- An Android security app with five areas: malware protection, a device
  security audit, DNS-level network protection ("Aegis Shield"), AI threat
  defenses, and an opt-in AI Crawler Shield (`docs/00-overview.md`,
  `docs/features/*.md`).
- **On-device verdicts:** verdicts are computed locally, and the only
  default network traffic is signed intel bundle downloads (ADR-0001), plus
  forwarded DNS when the Shield is on.
- **Deterministic verdicts:** from IOCs, rules, or fixed-threshold
  classifiers, never an LLM (ADR-0005).
- **Network protection without decryption:** a split-routed local
  `VpnService` that filters DNS (ADR-0002, ADR-0003).
- **Platform:** Kotlin, Jetpack Compose, Material 3. minSdk 29, compileSdk
  and targetSdk 37 (ADR-0007). Built with AGP 9.5 (built-in Kotlin), Gradle
  9.8.2, and a JDK 21 daemon (`docs/03-platforms.md`).
- **Out of scope for 1.0:** root-only features, TLS interception,
  anti-theft, remote wipe, app lock, VPN to a remote server, call
  recording, iOS, Wear OS, TV, enterprise MDM, and a cloud backend that
  stores user data (`docs/00-overview.md`, "Non-goals").

## Read before working

Read the relevant doc **before** you change behavior. Don't re-derive
decisions the docs already make.

| If you are touching… | Read first |
| :- | :- |
| Anything | `docs/00-overview.md` (scope, risks, open decisions), then `docs/01-architecture.md` |
| A phase, roadmap task, or spike (S1–S7) | `docs/roadmap.md` |
| A requirement (FR-*, NFR-*) or its priority | `docs/requirements.md` |
| Modules, processes, flows, the data model, the stack | `docs/01-architecture.md` |
| Feature behavior and acceptance criteria | `docs/features/*.md` |
| `app` | `docs/subsystems/app.md` |
| `core/common`, `core/model` | `docs/subsystems/core-common-and-model.md` |
| `core/data` | `docs/subsystems/core-data.md` |
| `core/security` | `docs/subsystems/core-security.md` |
| `core/ui` | `docs/subsystems/core-ui.md` |
| `engine/apk` | `docs/subsystems/engine-apk.md` |
| `engine/signatures`, `engine/heuristics`, `engine/ml` | `docs/subsystems/engine-detection.md`, `docs/features/malware-detection.md` |
| `engine/scanner` | `docs/subsystems/engine-scanner.md` |
| `intel/client`, `tools/intel-pipeline` | `docs/subsystems/intel.md`, `docs/data-sources.md` |
| `shield/vpn`, `shield/dns` | `docs/subsystems/shield.md`, `docs/features/network-protection.md`, `docs/features/ai-crawler-shield.md` |
| `ai/guard` | `docs/subsystems/ai-guard.md`, `docs/features/ai-threat-defense.md` |
| `feature/*` | `docs/subsystems/features.md` |
| `testing/*` | `docs/subsystems/testing.md` |
| Gradle files, `build-logic/`, a new dependency | `docs/02-dependency-policy.md`, `docs/01-architecture.md` ("Convention plugins") |
| SDK levels, the toolchain, API-level or OEM behavior | `docs/03-platforms.md` |
| Manifests, permissions, Play policy, disclosures | `docs/permissions-and-policy.md` |
| The data inventory, what leaves the device, logging | `docs/privacy.md` |
| Threats, trust boundaries, review triggers | `docs/threat-model.md` |
| External data, licenses, verification status | `docs/data-sources.md` |
| Test layers, gates, CI, malware-sample handling | `docs/04-quality.md` |
| Signing, secrets, releases | `docs/05-release.md` |
| Why something is the way it is | `docs/decisions.md` |

## Non-negotiable rules

Rules 1–13 each come from an ADR, a requirement, or a spec. Changing one
requires a new or revised ADR **and** the user's explicit approval. Rules 14–17 govern how you
work.

1. **No TLS interception.** No CA installation, no decryption, and no HTTP
   payload or user-agent inspection (ADR-0002).
2. **No AccessibilityService in the Aegis app** (ADR-0004). Benign test
   fixtures under `testing/fixtures/` may declare one so that they emulate
   risky apps.
3. **No LLM in the verdict path.** Verdicts come from IOCs, rules, or
   fixed-threshold classifiers (ADR-0005).
4. **ML alone never yields *Malicious*.** At most *Suspicious*
   (`docs/features/malware-detection.md`, "Verdict aggregation").
5. **Verify the signature before parsing any intel bundle content.** Use
   two-tier Ed25519 signing, and never put a private key in the repo or in
   logs (ADR-0006).
6. **Untrusted input is parsed with limits.** APKs, archives, DEX, DNS
   packets, QR codes, media, and shared text are parsed with size, depth,
   and time limits. APK, DEX, and media parsing runs in the `:parser`
   isolated process (NFR-SEC-02).
7. **No data leaves the device** except signed bundle downloads and
   forwarded DNS (NFR-PRIV-01). No analytics, ads, or attribution SDKs
   (NFR-PRIV-02).
8. **No PII in logs.** Use only the `core:common` logging facade. Never log
   package names, domains, URLs, file names, or message text in release
   builds.
9. **The Shield fails open** with a visible alert. It never silently breaks
   connectivity (FR-NET-09).
10. **Honest claims.** UI copy states only protection that has been
    verified. "Inbound" crawler blocking stays forbidden until spike S3
    passes (FR-ACS-08).
11. **Every new permission or exported component** updates
    `docs/permissions-and-policy.md` in the same change, and comes with a
    disclosure and a fallback. In Claude Code, use the `/add-permission`
    skill.
12. **Never put live malware in the repo, its history, or LFS.** Never
    download samples into this working tree (`docs/04-quality.md`,
    "Malware sample handling").
13. **Stalkerware:** notifications use neutral wording, and removal happens
    only through the safety flow. Never auto-remove
    (`docs/features/device-security-audit.md`, "Stalkerware safety flow").
14. **Honest verification.**
    - Never claim that a build, test, or check passed unless you ran it and
      saw the result.
    - If a gate doesn't exist in the build yet, say so instead of guessing.
    - Report what you didn't run.
15. **Git is read-only for agents.** Don't commit, amend, push, rebase,
    reset, or tag. Leave changes uncommitted for the user to review, and
    offer a commit message instead.
16. **Ask first** about anything ambiguous or hard to reverse, and about any
    change that would weaken a security control or break compatibility.
    State the conflict and propose the safest option.
17. **If code and docs disagree, stop and ask** which one is right. Don't
    silently "fix" either.

## Architecture rules

- **The module layout is fixed** in `docs/01-architecture.md` ("Module
  layout"). `feature:*` modules never depend on each other.
- **Six modules are pure Kotlin/JVM,** with no `android.*` imports:
  `engine:apk`, `engine:signatures`, `engine:heuristics`, `shield:dns`,
  `core:model`, and `core:common`. Android adapters live in
  `engine:scanner`, `shield:vpn`, and `app`. Whether `ai:guard` can be pure
  is still open.
- **One owner per sensitive capability.** Only `core:security` touches the
  Keystore and Tink. Only `intel:client` does network I/O for bundles.
- **Three processes:** main, `:shield` (the `VpnService`), and `:parser`
  (`isolatedProcess`). Input reaches the parser as a `ParcelFileDescriptor`
  over AIDL.
- **`checkModuleGraph` enforces the module rules** in CI, transitive
  dependencies included. The Keystore rule and the bundle network I/O rule
  are reviewed by hand.
- **Path-scoped rules** in `.claude/rules/` add detail for each area. Claude
  Code loads them automatically when you touch matching files; `CLAUDE.md`
  lists which files each one covers.

## Module map

Phase 0 created the modules marked built. The others are added when their
phase starts (Claude Code: `/new-module`).

| Path | Gradle path | Purpose | Status |
| :- | :- | :- | :- |
| `app` | `:app` | Application, nav graph, DI root, manifest merge point, the logcat sink of the logging facade | Built in Phase 0: an app shell with no protection and no navigation graph yet |
| `build-logic/convention` | `:build-logic:convention` (included build) | Convention plugins, `checkModuleGraph`, `checkSourceRules` | Built in Phase 0 |
| `core/common` | `:core:common` | Result types (`ParseResult`), dispatchers, clock, PII-safe logging facade. Pure JVM | Built in Phase 0; there is no clock type, so code injects `java.time.Clock` |
| `core/model` | `:core:model` | Domain models: `AppRecord`, `Finding`, `Verdict`, `Severity`, `IntelVersion`, … Pure JVM | Built in Phase 0; only `Verdict` exists so far |
| `core/data` | `:core:data` | Encrypted Room databases, DataStore, repositories | Built in Phase 0: the SQLCipher and Room factory and the settings DataStore. `app.db`'s entities arrive with Phase 1. |
| `core/security` | `:core:security` | Keystore, Tink wrappers, signature verification, integrity self-check | Built in Phase 0: Keystore-wrapped per-database secrets. Signature verification and the integrity self-check aren't built yet. |
| `core/ui` | `:core:ui` | Design system: theme, status components, icons, previews | Built in Phase 0 |
| `engine/apk` | `:engine:apk` | APK, ZIP, binary XML, and DEX parsing (runs in `:parser`). Pure JVM | Not built: Phase 6 (P6.1), after spike S4 |
| `engine/signatures` | `:engine:signatures` | Hash, package and certificate, and certificate IOC matching. Pure JVM | Not built: Phase 1 |
| `engine/heuristics` | `:engine:heuristics` | Declarative rules engine; the rules ship in the intel bundle. Pure JVM | Not built: Phase 1 (metadata heuristics v1) and Phase 6 (the rules engine) |
| `engine/ml` | `:engine:ml` | LiteRT model wrapper and feature vectors | Not built: Phase 6, after spike S5 |
| `engine/scanner` | `:engine:scanner` | Scan orchestration, detector pipeline, verdict aggregation; an Android adapter | Not built: Phase 1 |
| `intel/client` | `:intel:client` | Bundle download, verification, rollback protection, atomic swap | Not built: Phase 2 |
| `shield/vpn` | `:shield:vpn` | `VpnService`, TUN I/O, route builder, coexistence detection; an Android adapter | Not built: Phase 3, extended in Phase 4 for the AI Crawler Shield |
| `shield/dns` | `:shield:dns` | DNS wire parser, domain matcher, upstream (DoH/DoT), UID attribution. Pure JVM | Not built: Phase 3 |
| `ai/guard` | `:ai:guard` | Impersonation, AI SDK fingerprints, agent guard, scam and injection detectors, C2PA | Not built: Phase 5 |
| `feature/dashboard` | `:feature:dashboard` | Dashboard status UI | Not built: Phase 1 |
| `feature/scan` | `:feature:scan` | Scan progress and findings UI | Not built: Phase 1 |
| `feature/audit` | `:feature:audit` | Device security audit UI | Not built: Phase 1 |
| `feature/shield` | `:feature:shield` | Aegis Shield UI | Not built: Phase 3 |
| `feature/ai` | `:feature:ai` | AI threat defense UI | Not built: Phase 5 |
| `feature/onboarding` | `:feature:onboarding` | Onboarding UI | Not built: Phase 1 (P1.7; the roadmap placed it there, and no phase's original scope named it) |
| `feature/settings` | `:feature:settings` | Settings UI | Not built; the docs name no phase yet |
| `tools/intel-pipeline` | `:tools:intel-pipeline` | JVM CLI run in CI: fetch → normalize → validate → compile → sign | Not built: Phase 2 |
| `testing/common` | `:testing:common` | Fakes, test dispatchers, fixtures loader | Built in Phase 0: test dispatchers, `MutableClock`, `RecordingLogSink`. The fixtures loader isn't built yet. |
| `testing/fixtures` | `:testing:fixtures` (planned; the per-fixture layout isn't decided) | Benign fixture APKs that emulate risky profiles (never real malware) | Not built: Phase 1, since M1's exit needs the fixture apps |

## Commands

These match the CI gates (`.github/workflows/ci.yml` and `codeql.yml`;
`docs/04-quality.md`, "CI (GitHub Actions)"). Keep this table in step with
what actually runs.

| Command | What it does |
| :- | :- |
| `./gradlew checkModuleGraph checkSourceRules` | The module rules (`docs/01-architecture.md`, "Module layout"), plus the source rules: no direct logging, hard-coded dispatchers, `GlobalScope`, `runBlocking`, or engine `!!` in production sources |
| `./gradlew :build-logic:convention:test` | Tests for those checks |
| `./gradlew assembleDebug assembleRelease` | Debug and release builds. CI signs the release build with a throwaway key and never publishes it. |
| `./gradlew test koverVerify` | Unit tests, plus the ≥ 80% line-coverage gate on `aegis.jvm.engine` modules. Robolectric tests join with the first Android adapter that needs them. |
| `./gradlew lint detekt ktlintCheck` | Android Lint, detekt, and ktlint. Lint fails on errors, and the convention plugin promotes the TLS, Network Security Config (user CAs, insecure base config), exported-component, WebView, dynamic-code-loading, and world-readable-file checks to errors. |
| `./gradlew licensee` | The dependency license allowlist (Cash App's licensee; the allowlist is in `QualityConventions.kt`) |
| `./gradlew :<module>:test` | One module's unit tests |
| `./gradlew connectedDebugAndroidTest` | Instrumented tests. Needs an emulator or device; CI runs them on API 29 and API 37. |
| `python3 .claude/hooks/test_hooks.py` | The Claude Code hook self-tests, which CI also runs |
| `./gradlew ktlintFormat` | Auto-fixes formatting |

- **CodeQL** (`codeql.yml`) runs only in CI.
- **Not available yet:** short Jazzer fuzz runs start with the first parser
  (Phase 1), and `tools/intel-pipeline` tests start with the pipeline
  (Phase 2).
- **detekt:** CI's plain `detekt` has no type resolution, so don't rely on
  type-resolved detekt rules. `checkSourceRules` covers the critical ones.
- **JDK 21:** the Gradle daemon runs on JDK 21
  (`gradle/gradle-daemon-jvm.properties`), because detekt 1.23 can't start
  on JDK 25. Gradle doesn't download one. A JDK 21 must be on `JAVA_HOME`
  or `PATH` or in a detected location. To use Android Studio's JBR, set
  `JAVA_HOME` to it.
- **Android SDK:** point Gradle at it with `ANDROID_HOME` or with `sdk.dir`
  in the gitignored `local.properties`.
- **Dependency verification is strict,** and it applies to every Gradle
  run. After any dependency change, regenerate
  `gradle/verification-metadata.xml` and review its diff
  (`docs/02-dependency-policy.md`, "Dependency verification").

## Conventions

- **Code style:** ktlint's `ktlint_official` style (`.editorconfig`), a
  stricter superset of the Kotlin coding conventions, enforced by ktlint,
  plus detekt for complexity and code smells.
- **Engine modules:** explicit API mode, no `!!`, no Android imports, all
  I/O behind interfaces.
- **Coroutines:** inject dispatchers and the clock; never hard-code them.
  Structured concurrency only, never `GlobalScope`. Every long-running loop
  is cancellable and respects `isActive`.
- **Errors:** typed results at module boundaries; exceptions only for
  programmer errors. Parsers return `ParseResult.Rejected(reason)` instead
  of throwing on hostile input.
- **Logging:** only the `core:common` facade, with no PII
  (`docs/privacy.md`, "Logging").
- **UI:** state is hoisted, ViewModels expose `StateFlow<UiState>`, and
  every screen state has a preview.
- **Strings:** everything user-visible goes in resources. Security-claim
  copy is reviewed against the "honest claims" rule (rule 10).
- **Dependencies:** all of them go in `gradle/libs.versions.toml`, with no
  inline versions.
- **Commits and branches:** Conventional Commits (`feat(shield): …`).
  Branches: `feat/…`, `fix/…`, `docs/…`, `spike/S<n>-…`.
- **Package and applicationId:** `io.github.jabbottiii.aegis` (chosen
  2026-11-01, ADR-0007; the applicationId can't change after the first Play
  upload). Library modules use `io.github.jabbottiii.aegis.<area>.<name>`,
  with the namespace derived from the Gradle path. `:app` uses the base
  package.
- **SDK levels:** minSdk **29**, compileSdk and targetSdk **37** (chosen
  2026-11-01, ADR-0007). They live in `gradle/libs.versions.toml`, and only
  the convention plugins read them.

## How to work here

1. **Find the requirement.** Every change maps to FR, NFR, or spike IDs.
   Find the task in `docs/roadmap.md` and the requirement in
   `docs/requirements.md`, then read its feature spec and acceptance
   criteria.
2. **Check the gates.** Some work waits on a spike: for example, AI Crawler
   Shield copy waits on S3, route caps wait on S2, and the foreground-service
   type waits on S1. Some work waits on another module or on an open
   decision (`docs/00-overview.md`, "Open decisions"). If a gate is open,
   stop and report what is blocking.
3. **Test first** for engine logic. Every parser gets a fuzz harness.
4. **Make the smallest complete change.** No unrelated refactors, renames,
   or dependency bumps.
5. **Update docs in the same change:** the spec, plus
   `docs/permissions-and-policy.md`, `docs/privacy.md`, or
   `docs/threat-model.md` whenever its review triggers apply. Record new
   decisions in `docs/decisions.md`.
6. **Get a review** of security-sensitive changes; of manifest, permission,
   data-flow, or UI-claim changes for policy and privacy; and of rule,
   model, IOC, or verdict changes for detection quality. Claude Code has a
   review agent for each (`CLAUDE.md`).
7. **Finish** with the definition of done below.

## Definition of done for a roadmap task

1. Every acceptance criterion for the task in `docs/roadmap.md` is met, and
   its requirement IDs are implemented and linked in the PR.
2. There are unit tests, plus instrumented tests where platform APIs are
   involved.
3. The task's verification commands and the gates in "Commands" were run.
   Report each command and its real result. CI is green.
4. The checkbox is ticked only after steps 1 to 3.
5. The spec, the permissions table, and the privacy data inventory are
   updated. If a decision changed, add an ADR to `docs/decisions.md`.
6. Any new permission is behind a disclosure and has a fallback.
7. Performance budgets are checked on the reference mid-range device.
8. UI copy has been reviewed against the "honest claims" rule.
9. The final message summarizes:
   - changes and files affected
   - validation performed (commands and real results)
   - checks not run, and why
   - assumptions
   - open issues and residual risks
   - a suggested Conventional Commit message
