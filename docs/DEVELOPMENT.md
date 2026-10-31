# Development Guide

*Last updated: 2026-10-31 · Status: Planned. No code exists yet. This describes the intended setup for Phase 0.*

---

## Prerequisites (planned)

- Android Studio (latest stable)
- The JDK version required by the Android Gradle Plugin version pinned in `gradle/libs.versions.toml` (use the Android Studio bundled JDK unless CI pins another)
- Android SDK with the compileSdk platform (latest stable; API 37 at the time of writing)
- An emulator or a device running API 29 or newer

The repository's `.gitignore` already excludes `local.properties`, keystores (`*.jks`, `*.keystore`), `google-services.json`, and build outputs. Keep it that way.

## Project layout

See [ARCHITECTURE § Module layout](ARCHITECTURE.md#3-module-layout). The convention plugins in `build-logic/` are:

| Plugin | Applies to | Sets |
|---|---|---|
| `aegis.android.application` | `app` | SDK levels, R8, signing config from env, baseline profile |
| `aegis.android.library` | Android library modules | SDK levels, lint, test options |
| `aegis.android.compose` | UI modules | Compose compiler, previews |
| `aegis.jvm.engine` | Pure Kotlin engine modules | Explicit API mode, `allWarningsAsErrors`, Jazzer setup |
| `aegis.hilt` | Modules with DI | Hilt + KSP |

## Conventions

- **Kotlin:** official Kotlin coding conventions, enforced by ktlint, with detekt for complexity and code smells.
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

These checks will run in GitHub Actions on every PR:

1. `./gradlew assembleDebug assembleRelease` (the release build uses a throwaway CI key and is never published)
2. `./gradlew test` (unit and Robolectric)
3. `./gradlew lint detekt ktlintCheck`
4. Short fuzz runs (Jazzer, time-boxed)
5. Instrumented smoke tests on emulators: API 29 and API 37
6. `tools/intel-pipeline` tests
7. Gradle **dependency verification** (`gradle/verification-metadata.xml`)
8. **License allowlist** check for dependencies (for example, with a license-validation Gradle plugin such as Cash App's *licensee*)
9. CodeQL analysis for Java and Kotlin

Workflow hardening:

- Pin third-party actions by commit SHA.
- Set the default `GITHUB_TOKEN` permissions to read-only.
- Never expose secrets to workflows triggered by forks.

## Dependencies

- Declare every dependency in the version catalog, with no inline versions.
- Before adding a dependency, check its license against the allowlist, its maintenance activity, and its transitive size and permissions. Prefer platform or AndroidX APIs.
- Renovate or Dependabot opens update PRs. Update the dependency verification metadata in the same PR.
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
