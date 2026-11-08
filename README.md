# Aegis

Aegis is a planned Android security app, written in Kotlin. It gives you
standard mobile antivirus protection and adds defenses against AI-era
threats. It will cover five areas:

- **Malware protection:** scans apps when they are installed or updated, on
  demand, and on a schedule. Detection uses signature and IOC matching (hash,
  package, signing certificate), manifest and DEX heuristics, and an
  on-device ML classifier. It covers stalkerware detection with a
  safety-first removal flow, and scans APK files in Downloads.
- **Device security audit:** checks screen lock, patch age, developer
  options and USB debugging, user-installed CA certificates, risky permission
  grants, and apps holding accessibility, notification-listener, or
  device-admin access.
- **Network protection ("Aegis Shield"):** a local, on-device VPN that blocks
  malware, phishing, scam, and stalkerware command-and-control domains at the
  DNS level. It attributes blocks to individual apps and offers a link
  checker and a QR checker. Traffic is never decrypted.
- **AI threat defense:** flags fake AI-assistant apps, audits which apps
  have AI capabilities alongside screen or notification access, and alerts
  when an app gains screen-reading access. It also detects scam messages,
  scans for prompt injection and hidden text, checks media provenance
  (C2PA), and offers voice-clone scam safeguards.
- **AI Crawler Shield** (a toggle, off by default): blocks traffic to and
  from the published network ranges of AI crawlers, AI search indexers, and
  user-triggered AI fetchers. It can also control which apps may send data
  to AI services, and it explains how exposed the device actually is. The
  [feature spec](docs/features/ai-crawler-shield.md) says exactly what it can
  and cannot do.

Five principles shape the design:

1. **On-device first.** Verdicts are computed locally. By default the only
   network traffic is the download of signed threat-intelligence updates
   (ADR-0001).
2. **No TLS interception.** Aegis never installs a CA certificate and never
   decrypts traffic (ADR-0002).
3. **Honest claims.** The UI states only protections that have been verified
   on real devices. Platform limits are explained to the user, not hidden.
4. **Least privilege.** Every permission maps to a feature, is requested
   only when that feature is enabled, and comes with a working fallback
   ([`docs/permissions-and-policy.md`](docs/permissions-and-policy.md)).
5. **Deterministic verdicts.** LLMs can help explain a result but never
   decide one (ADR-0005).

It is written in Kotlin with Jetpack Compose and Material 3. It runs on
Android 10 (minSdk 29, chosen on 2026-11-01 in ADR-0007, which is still
proposed) and newer, and targets Android 17 (targetSdk 37). Since
Aug 31 2026, Google Play has required new apps and updates to target
Android 16 (API 36) or higher.

## Status

**Phase 0 (foundations) is in progress.** The build, the CI workflows, and
an app shell exist. The shell offers no protection yet.

- **Built and verified locally:**
  - a multi-module Gradle build with a version catalog and convention
    plugins in `build-logic/` (AGP 9.5 with built-in Kotlin, Gradle 9.8.2, a
    JDK 21 daemon);
  - the `checkModuleGraph` and `checkSourceRules` checks, with unit tests;
  - the foundation modules `app`, `core:common`, `core:model`, `core:ui` (a
    design system with previews), and `testing:common`, with Hilt DI,
    injected dispatchers, and the PII-safe logging facade;
  - `core:security`: per-database secrets wrapped by an Android Keystore
    AES-256-GCM key (StrongBox when available) through Tink, a cross-process
    lock, and no key replacement while a secret is stored;
  - `core:data`: a SQLCipher 4.20 and Room 3.0.3 factory that never deletes
    a database and allows only migrations, and a multi-process settings
    DataStore.
- **Local gates:** on 2026-11-01 the full local gate set passed: the debug
  and release builds, unit tests, Kover, lint, detekt, ktlint, licensee,
  the build-logic tests, and the hook self-tests. Instrumented tests passed
  on an API 37 emulator. They weren't run on API 29 locally.
- **CI on GitHub hasn't passed yet.** Every run so far failed dependency
  verification, because the metadata was written from a warm Gradle cache.
  It has been regenerated from an empty cache. A local simulation of CI,
  from an empty Gradle home under strict verification, passes. GitHub
  hasn't confirmed it yet (`docs/02-dependency-policy.md`, "Dependency
  verification"), so Phase 0's exit criterion of a clean CI run on `main`
  isn't met yet.
- **Not started:** the technical spikes S1–S7 and everything from Phase 1
  on.
- **Decisions:** all seven ADRs are *Proposed*. Each design decision here is
  a proposal and stays one until its ADR in
  [`docs/decisions.md`](docs/decisions.md) is marked *Accepted*.

| Phase | What it delivers | Exit milestone |
| :- | :- | :- |
| 0 | Project scaffold, CI, core security plumbing, technical spikes S1–S7 | M0 Foundations ready |
| 1 | App inventory, IOC matching, metadata heuristics, scan UX, device audit | M1 (with Phase 2) |
| 2 | Intel pipeline (CI), bundle format, signing, update client | M1 Scanner alpha |
| 3 | Local VPN, DNS filter, per-app attribution, link and QR checker, network checks | M2 (with Phase 4) |
| 4 | AI Crawler Shield: range ingestion, routing, AI egress control, exposure check | M2 Shield beta |
| 5 | AI threat defense features | M3 AI defense beta |
| 6 | DEX static analysis, rules engine, ML classifier, false-positive corpus | Feeds M4 |
| 7 | MASVS review, performance and battery work, Play declarations, closed testing | M4 1.0 on Play |

[`docs/roadmap.md`](docs/roadmap.md) breaks each phase into tasks with
acceptance criteria.

## Build and run

You need:

| Tool | Notes |
| :- | :- |
| Android Studio | The latest stable version |
| JDK 21 | Runs the Gradle daemon (`gradle/gradle-daemon-jvm.properties`), because detekt 1.23 can't start on JDK 25. Gradle doesn't download one: it must be on `JAVA_HOME` or `PATH`, or in a detected location. To use Android Studio's JBR, point `JAVA_HOME` at it. |
| Android SDK | With the compileSdk platform, API 37 (`platforms;android-37.0`). Set `ANDROID_HOME`, or `sdk.dir` in the gitignored `local.properties`. |
| An emulator or device | API 29 or newer, for the instrumented tests |
| `python3` | Standard library only, for the Claude Code hooks and their self-tests |

```bash
git clone https://github.com/jabbott-iii/Aegis.git
cd Aegis
./gradlew assembleDebug
```

This builds the app shell, which offers no protection yet.
[`CONTRIBUTING.md`](CONTRIBUTING.md) ("Development setup") has more on
finding a JDK.

Other things to run:

| Command | What it runs |
| :- | :- |
| `./gradlew assembleDebug assembleRelease` | The debug and release builds |
| `./gradlew test koverVerify` | Unit tests and the ≥ 80% engine coverage gate |
| `./gradlew lint detekt ktlintCheck` | Android Lint, detekt, and ktlint |
| `./gradlew checkModuleGraph checkSourceRules` | The module dependency rules and the source rules |
| `./gradlew connectedDebugAndroidTest` | Instrumented tests on an attached emulator or device |

[`AGENTS.md`](AGENTS.md) ("Commands") lists every gate that CI runs.

## Repository layout

| Path | Contents |
| :- | :- |
| `app/` | The application: navigation, the DI root, the manifest merge point |
| `core/` | The foundation modules: `common`, `model`, `data`, `security`, `ui` |
| `testing/` | Shared test code (`testing/common`) and, later, benign fixture apps |
| `build-logic/` | The convention plugins and the module-graph and source-rule checks |
| `config/detekt/` | The detekt configuration |
| `gradle/` | The version catalog, dependency verification metadata, the daemon JVM setting, the wrapper |
| `docs/` | The design: overview, architecture, requirements, feature specs, policy, roadmap, decisions |
| `diagrams/` | Diagrams of the architecture and the main flows |
| `.github/` | The CI and CodeQL workflows, Dependabot, issue and pull request templates |
| `.claude/` | Shared Claude Code configuration: rules, skills, review agents, hooks |

The other modules in `docs/01-architecture.md` ("Module layout"), under
`engine/`, `intel/`, `shield/`, `ai/`, `feature/`, and `tools/`, are added
when their phase starts.

## Documentation

| Document | Covers |
| :- | :- |
| [`docs/00-overview.md`](docs/00-overview.md) | Purpose, goals, non-goals, performance budgets, risks, open decisions |
| [`docs/01-architecture.md`](docs/01-architecture.md) | Modules, processes, components, data flows, data model, background execution, tech stack |
| [`docs/02-dependency-policy.md`](docs/02-dependency-policy.md) | What may be added, forbidden SDKs, the license allowlist, dependency verification |
| [`docs/03-platforms.md`](docs/03-platforms.md) | SDK levels, the toolchain, Android behavior by API level and OEM |
| [`docs/04-quality.md`](docs/04-quality.md) | Test strategy, detection-quality metrics, malware-sample handling, the device matrix, CI |
| [`docs/05-release.md`](docs/05-release.md) | Build types, versioning, signing and secrets, Play tracks, intel bundle releases |
| [`docs/requirements.md`](docs/requirements.md) | Functional and non-functional requirements with IDs and priorities |
| [`docs/roadmap.md`](docs/roadmap.md) | Every phase and task, with acceptance criteria, and the spikes S1–S7 |
| [`docs/decisions.md`](docs/decisions.md) | Architecture decision records |
| [`docs/threat-model.md`](docs/threat-model.md) | Threats Aegis defends against, threats to Aegis itself, and residual gaps |
| [`docs/permissions-and-policy.md`](docs/permissions-and-policy.md) | Each permission's justification, Play declarations, distribution rules, and the Data safety mapping |
| [`docs/privacy.md`](docs/privacy.md) | Data inventory, what leaves the device, retention, and logging |
| [`docs/data-sources.md`](docs/data-sources.md) | Threat-intel and crawler-range sources, with license and terms status |
| Feature specs | [Malware detection](docs/features/malware-detection.md) · [Device security audit](docs/features/device-security-audit.md) · [Network protection](docs/features/network-protection.md) · [AI Crawler Shield](docs/features/ai-crawler-shield.md) · [AI threat defense](docs/features/ai-threat-defense.md) |
| [`docs/subsystems/`](docs/subsystems/) | One design spec per module group |
| [`diagrams/`](diagrams/) | C4 views of the system, and the scan, DNS lookup, and intel update sequences |

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). Coding agents read
[`AGENTS.md`](AGENTS.md) (Claude Code also reads [`CLAUDE.md`](CLAUDE.md)).
Report security issues privately, as [`SECURITY.md`](SECURITY.md)
describes.

## License

No license has been chosen yet, and there is no `LICENSE` file. This is
open decision #7 in [`docs/00-overview.md`](docs/00-overview.md) ("Open
decisions"). The owner decides on one before accepting outside
contributions or bundling third-party data, because some data sources carry
attribution or commercial-use terms
([`docs/data-sources.md`](docs/data-sources.md)).
