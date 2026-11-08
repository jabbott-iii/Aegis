# Contributing to Aegis

Aegis is built one roadmap task at a time. It is a security product, so a
bug in it becomes a weakness on every device that runs it. Every change
maps to a requirement, comes with the tests that prove it, and claims only
what was actually run and verified.

The project is in Phase 0. The build, the CI workflows, and an app shell
exist, and the commands below work today. CI on GitHub currently fails on
dependency verification ([`README.md`](README.md), "Status").

No license has been chosen yet (see "License" below). The owner decides on
one before accepting outside contributions.

## Contribution workflow

1. **Find the work** in [`docs/roadmap.md`](docs/roadmap.md). Every change
   maps to requirement IDs (FR-*, NFR-*) in
   [`docs/requirements.md`](docs/requirements.md) or to a spike (S1–S7).
2. **Read first.** Read the docs that [`AGENTS.md`](AGENTS.md) → "Read
   before working" lists for the modules you will touch, the feature spec
   and its acceptance criteria, and any ADR the work relies on in
   [`docs/decisions.md`](docs/decisions.md).
3. **Check the gates.** Some work waits on a spike (AI Crawler Shield copy
   waits on S3, route caps on S2) or on an open decision in
   [`docs/00-overview.md`](docs/00-overview.md) ("Open decisions").
4. **Branch** with the names in "Commits and pull requests" below.
5. **Write the tests first** for engine logic, then make the smallest
   complete change that meets the acceptance criteria.
6. **Update the docs** in the same change (see "Documentation").
7. **Run the gates** in "Commands", and say which ones you couldn't run.
8. **Open a pull request** from the template. It asks for the requirement
   IDs and a checklist (see "Commits and pull requests").

## Ground rules

These are summarized from `AGENTS.md` ("Non-negotiable rules"), which is
authoritative. Changing one of rules 1–13 needs a new or revised ADR and
the owner's explicit approval.

- **No TLS interception,** no CA installation, and no inspection of HTTP
  payloads or user agents (ADR-0002).
- **No AccessibilityService** in the Aegis app (ADR-0004).
- **No LLM decides a verdict** (ADR-0005), and ML alone never yields
  *Malicious*.
- **Intel bundles are verified before parsing,** with two-tier Ed25519
  signing. No private key goes in the repo or in logs (ADR-0006).
- **Untrusted input is parsed with size, depth, and time limits.** APK,
  DEX, and media parsing runs in the `:parser` isolated process
  (NFR-SEC-02).
- **No data leaves the device** except signed bundle downloads and
  forwarded DNS (NFR-PRIV-01). No analytics, ads, or attribution SDKs
  (NFR-PRIV-02).
- **No PII in logs.** Use only the `core:common` logging facade.
- **The Shield fails open** with a visible alert (FR-NET-09).
- **Honest claims.** UI copy states only verified protection. "Inbound"
  crawler blocking stays forbidden until spike S3 passes (FR-ACS-08).
- **Every new permission or exported component** updates
  [`docs/permissions-and-policy.md`](docs/permissions-and-policy.md) in the
  same change, with a disclosure and a fallback.
- **No live malware** in the repo, its history, or LFS, and no sample
  downloads into the working tree.
- **Stalkerware** notifications use neutral wording, and removal happens
  only through the safety flow, never automatically.
- **Honest verification.** Say what you ran, with results, and what you
  didn't run.

## Development setup

| You need | Notes |
| :- | :- |
| Android Studio | The latest stable version |
| JDK 21 | For the Gradle daemon (ADR-0007). Compiled code targets Java 17. |
| Android SDK | With the compileSdk platform, API 37 (`platforms;android-37.0`) |
| An emulator or device | Running API 29 or newer |
| `python3` | Standard library only, for the Claude Code hooks and their self-tests |

- **JDK 21.** `gradle/gradle-daemon-jvm.properties` asks for it, because
  detekt 1.23 can't start on JDK 25. Gradle doesn't download a JDK. It has
  to find one: through `JAVA_HOME` or the `java` on `PATH`, in an
  OS-standard location such as `/usr/lib/jvm`, or from SDKMAN!, asdf, or
  Gradle-provisioned toolchains. Android Studio's bundled JBR counts only if
  you point at it, for example with
  `export JAVA_HOME=/path/to/android-studio/jbr` or with
  `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`.
- **Android SDK.** Set `ANDROID_HOME`, or `sdk.dir` in `local.properties`.
- **What stays out of Git.** `.gitignore` already excludes
  `local.properties`, keystores (`*.jks`, `*.keystore`),
  `google-services.json`, and build outputs. Keep it that way.

[`docs/03-platforms.md`](docs/03-platforms.md) ("Toolchain") has the full
toolchain.

## Commands

| Command | What it does |
| :- | :- |
| `./gradlew checkModuleGraph checkSourceRules` | Module dependency rules, plus the source rules for logging, dispatchers, `GlobalScope`, `runBlocking`, and engine `!!` |
| `./gradlew :build-logic:convention:test` | Tests for those checks |
| `./gradlew assembleDebug assembleRelease` | Debug and release builds |
| `./gradlew test koverVerify` | Unit tests, plus the ≥ 80% line-coverage gate on engine modules |
| `./gradlew lint detekt ktlintCheck` | Android Lint, detekt, and ktlint |
| `./gradlew licensee` | The dependency license allowlist |
| `./gradlew :<module>:test` | One module's unit tests |
| `./gradlew connectedDebugAndroidTest` | Instrumented tests on an attached emulator or device |
| `python3 .claude/hooks/test_hooks.py` | The Claude Code hook self-tests |
| `./gradlew ktlintFormat` | Auto-fixes formatting |

CI runs the same gates, plus CodeQL and the instrumented tests on API 29 and
API 37 emulators ([`docs/04-quality.md`](docs/04-quality.md), "CI (GitHub
Actions)"). Dependency verification is strict: after any dependency change,
regenerate `gradle/verification-metadata.xml` and review its diff
([`docs/02-dependency-policy.md`](docs/02-dependency-policy.md),
"Dependency verification").

## Coding conventions

- **Kotlin:** ktlint's `ktlint_official` code style (a stricter superset of
  the Kotlin coding conventions, configured in `.editorconfig`), plus detekt
  for complexity and code smells.
- **Engine modules:** explicit API mode, no `!!`, no Android imports, all
  I/O behind interfaces, and dispatchers injected rather than hard-coded.
- **Coroutines:** structured concurrency only. No `GlobalScope`. Every
  long-running loop must be cancellable and respect `isActive`.
- **Errors:** typed results at module boundaries, and exceptions only for
  programmer errors. Parsers return a `ParseResult.Rejected(reason)` instead
  of throwing on hostile input.
- **Logging:** only the `core:common` facade, with no PII
  ([`docs/privacy.md`](docs/privacy.md), "Logging").
- **UI:** state is hoisted, ViewModels expose `StateFlow<UiState>`, and
  every screen state has a preview.
- **Strings:** everything user-visible goes in resources. Security-claim
  copy is reviewed against the "honest claims" principle.
- **Dependencies:** declared only in `gradle/libs.versions.toml`, with no
  inline versions. Module build files apply convention plugins and declare
  dependencies, and nothing else.
- **Packages:** the base package is `io.github.jabbottiii.aegis`; library
  modules use `io.github.jabbottiii.aegis.<area>.<name>`.

## Tests

- **Test first** for engine logic, and every parser gets a fuzz harness.
- **Unit tests** for each change, plus instrumented tests where platform
  APIs are involved.
- **Coverage:** engine modules keep at least 80% line coverage
  (`koverVerify`).
- **Fixtures** under `testing/fixtures/` are benign apps built from source
  that emulate risky profiles. Never add real malware.
- [`docs/04-quality.md`](docs/04-quality.md) describes the test layers,
  detection-quality gates, malware-sample handling, and the device matrix.

## Documentation

Update the docs in the same pull request as the behavior change:

- the feature spec in `docs/features/`, when behavior or acceptance
  criteria change;
- `docs/permissions-and-policy.md`, for a new permission, exported
  component, or sensitive API;
- `docs/privacy.md`, when data is stored, retained, or sent;
- `docs/threat-model.md`, whenever one of its review triggers applies;
- `docs/data-sources.md`, for a new external data source;
- `docs/decisions.md`, with a new ADR, when a decision is made or changes.

Tick a roadmap task only when its acceptance criteria are met and its
verification commands passed.

## Commits and pull requests

- **`main` is protected:** changes go through pull requests only, with
  required CI checks and linear history.
- **Branch names:** `feat/<area>-<short>`, `fix/<area>-<short>`,
  `docs/<short>`, `spike/S<n>-<short>`.
- **Commit messages:** Conventional Commits (`feat(shield): …`,
  `fix(scanner): …`, `docs: …`). Breaking changes to the bundle format use
  `!` and bump `minEngineVersion`.
- **The pull request template** asks for:
  - what changed and why, and the requirement IDs (FR-*, NFR-*, or spike
    S<n>); every change maps to at least one;
  - **permissions or policy:** no new permission, exported component, or
    sensitive API, or `docs/permissions-and-policy.md` updated with a
    disclosure and a fallback;
  - **privacy:** the data inventory in `docs/privacy.md` is still accurate,
    nothing new leaves the device, logs carry no PII, and logger `values`
    are counts, sizes, or durations, never UIDs, addresses, ports, hashes,
    or timestamps;
  - **threat model:** no review trigger applies
    (`docs/threat-model.md`, "B4. Review triggers"), or
    `docs/threat-model.md` is updated;
  - **docs:** the feature spec and any ADR are updated in the same pull
    request;
  - **supply chain:** any new dependency is in the version catalog, has an
    allowed license, and `gradle/verification-metadata.xml` is
    regenerated;
  - **honest claims:** new UI copy states only verified protection;
  - screenshots for UI changes, in light, dark, and 200% font scale.
- **Dependabot** (`.github/dependabot.yml`) opens dependency update pull
  requests. Update the verification metadata in the same pull request.

## Coding agents

Agents follow [`AGENTS.md`](AGENTS.md). Claude Code also loads
[`CLAUDE.md`](CLAUDE.md) and the shared configuration in `.claude/`:

- the path-scoped rules in `.claude/rules/`, which load when matching files
  are touched (engine, parsers, manifest, Shield, UI copy, build, intel
  pipeline, tests);
- the project skills in `.claude/skills/` (`/implement-requirement`,
  `/next-task`, `/new-module`, `/add-permission`, `/add-intel-source`,
  `/run-spike`, `/record-decision`, `/pre-pr-check`);
- the read-only review agents in `.claude/agents/` (`security-reviewer`,
  `policy-privacy-reviewer`, `detection-quality-reviewer`);
- the hooks in `.claude/hooks/`, which need `python3`, and the shared
  permissions in `.claude/settings.json`.

Personal overrides belong in `.claude/settings.local.json` and
`CLAUDE.local.md`, which are both gitignored. The hooks are guardrails, not
a security boundary: CI gates and code review remain the enforcement
points. Agents never commit, push, or tag; a human reviews and commits
their work.

## Security issues

Don't open a public issue for a vulnerability. Follow
[`SECURITY.md`](SECURITY.md). Never attach malware samples to an issue or
a pull request.

## License

Aegis has no license yet, and there is no `LICENSE` file. Choosing one is
open decision #7 in [`docs/00-overview.md`](docs/00-overview.md) ("Open
decisions"). It affects contributions and how third-party data is
redistributed, because some data sources carry attribution or
commercial-use terms ([`docs/data-sources.md`](docs/data-sources.md)).
