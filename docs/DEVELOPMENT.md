# Development Guide

*Last updated: 2026-11-01 · Status: Phase 0 scaffold in place. Sections marked "planned" describe later work.*

---

## Prerequisites

- Android Studio (latest stable)
- **JDK 21** for the Gradle daemon ([ADR-0007](adr/0007-platform-and-build-baseline.md)). `gradle/gradle-daemon-jvm.properties` asks for it, and detekt 1.23 can't start on JDK 25. Compiled code targets Java 17. Gradle doesn't download a JDK. It has to find one: through `JAVA_HOME` or the `java` on `PATH`, in an OS-standard location such as `/usr/lib/jvm`, or from SDKMAN!, asdf, or Gradle-provisioned toolchains. Android Studio's bundled JBR counts only if you point at it, for example with `export JAVA_HOME=/path/to/android-studio/jbr` or with `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`.
- Android SDK with the compileSdk platform (API 37, `platforms;android-37.0`). Set `ANDROID_HOME` or `sdk.dir` in `local.properties`.
- An emulator or a device running API 29 or newer

The repository's `.gitignore` already excludes `local.properties`, keystores (`*.jks`, `*.keystore`), `google-services.json`, and build outputs. Keep it that way.

## Project layout

See [ARCHITECTURE § Module layout](ARCHITECTURE.md#3-module-layout). The convention plugins in `build-logic/` are:

| Plugin | Applies to | Sets |
|---|---|---|
| `aegis.android.application` | `app` only (it refuses any other module) | SDK levels, R8 and resource shrinking, release signing from `AEGIS_SIGNING_*` environment variables. A baseline profile is planned for Phase 7. Fixture APKs will need their own plugin. |
| `aegis.android.library` | Android library modules | SDK levels, lint, test options, namespace from the Gradle path |
| `aegis.android.compose` | UI modules | Compose compiler, the Compose BOM, previews |
| `aegis.jvm.library` | Plain Kotlin/JVM modules (`testing:common`) | Kotlin JVM with `-Xjdk-release=17`, JUnit 5 |
| `aegis.jvm.engine` | Pure Kotlin engine and core modules | Everything in `aegis.jvm.library`, plus explicit API mode, `allWarningsAsErrors`, and the Kover ≥ 80% line-coverage gate. Jazzer gets wired in with the first parser (Phase 1). |
| `aegis.hilt` | Modules with DI | Hilt + KSP |
| `aegis.root` | The root project | `checkModuleGraph` (plus a `checkResolvedGraph` task in each module) and `checkSourceRules` |

Every module plugin also applies detekt (`config/detekt/detekt.yml`), ktlint (`.editorconfig`), and
the licensee allowlist (Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause). The module build files apply
plugins and declare dependencies, and nothing else.

AGP 9 compiles Kotlin itself ("built-in Kotlin"), so Android modules don't apply
`org.jetbrains.kotlin.android`.

## Conventions

- **Kotlin:** ktlint's `ktlint_official` code style (a stricter superset of the Kotlin coding conventions, configured in `.editorconfig`), plus detekt for complexity and code smells.
- **Engine modules:** explicit API mode, no `!!`, no Android imports, all I/O behind interfaces, and dispatchers injected rather than hardcoded.
- **Coroutines:** structured concurrency only. No `GlobalScope`. Every long-running loop must be cancellable and respect `isActive`.
- **Errors:** use typed results at module boundaries, and use exceptions only for programmer errors. Parsers return a `ParseResult.Rejected(reason)` instead of throwing on hostile input.
- **Logging:** use only the `core:common` facade, with no PII ([PRIVACY § Logging](PRIVACY.md#logging)).
- **UI:** state is hoisted, ViewModels expose `StateFlow<UiState>`, and every screen state has a preview.
- **Strings:** everything user-visible goes in resources. Security-claim copy is reviewed against the "honest claims" principle.

## Branching and commits

- `main` is protected: changes go through PRs only, with required CI checks and linear history.
- Branch names: `feat/<area>-<short>`, `fix/<area>-<short>`, `docs/<short>`, `spike/S<n>-<short>`.
- Commit messages: Conventional Commits (`feat(shield): …`, `fix(scanner): …`, `docs: …`). Breaking changes to the bundle format use `!` and bump `minEngineVersion`.
- PR template: linked requirement IDs, a permission or policy impact checkbox, a privacy inventory checkbox, a threat-model trigger checkbox, and screenshots for UI changes.

## CI gates

These checks run in GitHub Actions on every PR (`.github/workflows/ci.yml` and `codeql.yml`).
Items 4 and 6 start once their first code lands.

1. `./gradlew assembleDebug assembleRelease` (the release build uses a throwaway CI key and is never published)
2. `./gradlew test koverVerify`: unit tests, plus the ≥ 80% line-coverage gate on engine modules. Robolectric tests join with the first Android adapter that needs them.
3. `./gradlew lint detekt ktlintCheck`. Lint fails on errors, and the convention plugin promotes the TLS, Network Security Config (user CAs, insecure base config), exported-component, WebView, dynamic-code-loading, and world-readable-file checks to errors. CI's plain `detekt` has no type resolution, so rules that need it don't run (see `config/detekt/detekt.yml`).
4. Short fuzz runs (Jazzer, time-boxed). *Planned: starts with the first parser.*
5. Instrumented smoke tests on emulators: API 29 and API 37
6. `tools/intel-pipeline` tests. *Planned: start with the pipeline in Phase 2.*
7. Gradle **dependency verification** (`gradle/verification-metadata.xml`), which applies to every Gradle run
8. **License allowlist** check for dependencies: `./gradlew licensee` (Cash App's licensee, with the allowlist in `QualityConventions.kt`)
9. CodeQL analysis for Java and Kotlin
10. `./gradlew checkModuleGraph checkSourceRules`: module dependency rules, including transitive dependencies, plus the source rules for logging, dispatchers, `GlobalScope`, `runBlocking`, and engine `!!`
11. `./gradlew :build-logic:convention:test` (tests for those checks) and `python3 .claude/hooks/test_hooks.py`

Workflow hardening:

- Pin third-party actions by commit SHA.
- Set the default `GITHUB_TOKEN` permissions to read-only.
- Never expose secrets to workflows triggered by forks.

## Dependencies

- Declare every dependency in the version catalog, with no inline versions.
- Before adding a dependency, check its license against the allowlist, its maintenance activity, and its transitive size and permissions. Prefer platform or AndroidX APIs.
- Dependabot (`.github/dependabot.yml`) opens update PRs. Update the dependency verification metadata in the same PR.
- **Regenerating verification metadata.** Run the CI task set with `--write-verification-metadata sha256`, then review the diff. Every new artifact needs a reason to be there:

  ```bash
  ./gradlew --no-configuration-cache --write-verification-metadata sha256 \
    checkModuleGraph assembleDebug assembleRelease assembleDebugAndroidTest \
    test lint detekt ktlintCheck licensee koverVerify
  ```

  With a device or emulator attached, also run `connectedDebugAndroidTest` the same way so that the test-platform artifacts get recorded. Gradle only records the artifacts it resolved, which on Linux means only the `linux` `aapt2` jar. When AGP changes, add the `osx` and `windows` `aapt2` jars for the new version by hand, and check each hash against the SHA-1 that Google Maven publishes.
- **Forbidden:** analytics, ads, or attribution SDKs, and any SDK that makes network calls Aegis doesn't control.

## Secrets and signing

| Secret | Where it lives | Never |
|---|---|---|
| Play upload key | Developer's machine or hardware token, and optionally a CI secret on a protected release environment | Never in the repo or in plain CI variables |
| App signing key | Play App Signing (managed by Google) | — |
| **Intel root key (Ed25519)** | Offline, on the maintainer's hardware token ([ADR-0006](adr/0006-signed-threat-intel-updates.md)). Used only to sign delegation records. | Never in CI, never in the repo |
| **Intel online signing key (Ed25519)** | A secret in a protected GitHub Actions environment with required reviewers. It is delegated by the root key for 90 days or less. | Never in the repo or in workflows that forks can trigger |
| Third-party API keys (future) | A backend proxy only | Never inside the APK |

## Releasing (planned)

1. Bump `versionCode` and `versionName`, and update the changelog.
2. Tag the release, and CI builds the release AAB.
3. Upload to the Play **internal** track, then closed testing, then production with a staged rollout (10% → 50% → 100%, watching the crash rate and FP reports).
4. Release the intel bundle on its own cadence. A bundle's `minAppVersion` keeps it from reaching app versions that can't use it.

## Working with Claude Code

The repository ships shared Claude Code configuration. Personal overrides belong in `.claude/settings.local.json` and `CLAUDE.local.md`, which are both gitignored.

| Path | Purpose |
|---|---|
| `CLAUDE.md` | Project instructions: doc map, non-negotiables, architecture rules, workflow |
| `.claude/rules/*.md` | Path-scoped rules that load when matching files are touched (engine, parsers, manifest, Shield, UI copy, build, intel pipeline, tests) |
| `.claude/settings.json` | Shared permissions (gradle allowed; git history changes ask; secrets unreadable) and hooks |
| `.claude/hooks/` | `pre_tool_guard.py` (blocks security-weakening config, secrets, forbidden SDKs, malware downloads) and `post_edit_checks.py` (rule warnings). Both need `python3`. Self-test with `python3 .claude/hooks/test_hooks.py`. |
| `.claude/skills/` | `/implement-requirement`, `/run-spike`, `/new-adr`, `/add-permission`, `/add-intel-source`, `/new-module`, `/pre-pr-check` |
| `.claude/agents/` | `security-reviewer`, `policy-privacy-reviewer`, `detection-quality-reviewer` (all read-only) |

The hooks are guardrails, not a security boundary. CI gates and code review remain the enforcement points.
