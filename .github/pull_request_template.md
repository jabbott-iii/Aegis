<!--
Thank you for contributing to Aegis. Read CONTRIBUTING.md before opening this
pull request: the project rules in AGENTS.md apply to every change.
Every change maps to at least one requirement or spike ID (FR-*, NFR-*, S<n>).
Replace the comments below, and remove the sections that do not apply.
-->

## Linked issue, roadmap task and requirements

Closes # <!-- or "none" -->

- Roadmap task: <!-- such as S1, or "none" -->
- Requirement IDs: <!-- FR-*, NFR-*, or spike S<n>. Every change maps to at least one. -->

## Summary

<!-- What changed, and why. Keep the pull request to the one task or issue. -->

## Type of change

- [ ] Bug fix
- [ ] Roadmap task (every acceptance criterion in `docs/roadmap.md` is met)
- [ ] Spike write-up (S1–S7, in `docs/spikes/`)
- [ ] Detection change: IOCs, heuristic rules, ML features or models, verdict logic
- [ ] Permission, manifest entry or exported component
- [ ] Threat-intel bundle format or pipeline (a breaking bundle format change uses `!` and bumps `minEngineVersion`)
- [ ] A decision: a new or revised ADR in `docs/decisions.md`
- [ ] User-facing copy or UI
- [ ] Documentation
- [ ] Build, CI, dependencies or Claude Code configuration
- [ ] Tests or refactor only, with no change in behavior

## Validation

<!-- Tick only what you ran, and paste the output below. Say which checks
you could not run, and why: instrumented tests need an emulator or a
device, and CodeQL runs only in CI, for example. -->

- [ ] `./gradlew checkModuleGraph checkSourceRules`
- [ ] `./gradlew :build-logic:convention:test`
- [ ] `./gradlew assembleDebug assembleRelease`
- [ ] `./gradlew test koverVerify` (unit tests and the ≥ 80% engine coverage gate)
- [ ] `./gradlew lint detekt ktlintCheck`
- [ ] `./gradlew licensee`
- [ ] `python3 .claude/hooks/test_hooks.py`
- [ ] `./gradlew connectedDebugAndroidTest`, on an emulator or a device (CI runs API 29 and API 37 emulators)
- [ ] `gradle/verification-metadata.xml` regenerated and its diff reviewed, for a dependency or plugin change (`docs/02-dependency-policy.md`)
- [ ] Measured on the device matrix (`docs/04-quality.md`, "Device matrix"), for a spike or a performance change

**Tested on:** <!-- device or emulator, Android version and API level, and the JDK running the Gradle daemon -->

**Not run, and why:** <!-- or "none" -->

<details>
<summary>Command output</summary>

```text
paste output here
```

</details>

## Checklist

- [ ] **Permissions or policy:** no new permission, exported component, or sensitive API, *or* `docs/permissions-and-policy.md` is updated with a disclosure and a fallback.
- [ ] **Privacy:** the data inventory in `docs/privacy.md` is still accurate. Nothing new leaves the device. Logs carry no PII, and logger `values` are counts, sizes, or durations, never UIDs, addresses, ports, hashes, or timestamps.
- [ ] **Threat model:** no review trigger applies (`docs/threat-model.md`, "B4. Review triggers"), *or* `docs/threat-model.md` is updated.
- [ ] **Docs:** the feature spec and any ADR (`docs/decisions.md`) are updated in this PR.
- [ ] **Supply chain:** any new dependency is in the version catalog, has an allowed license, and `gradle/verification-metadata.xml` is regenerated.
- [ ] **Honest claims:** new UI copy states only verified protection.
- [ ] **Non-negotiable rules:** nothing changes one of the rules 1–13 in `AGENTS.md` without a new or revised ADR and the owner's explicit approval.
- [ ] **Modules:** each module depends only on what `docs/01-architecture.md` ("Dependency rules") allows. `feature:*` modules don't depend on each other, and the pure Kotlin/JVM modules have no `android.*` imports.
- [ ] **Untrusted input:** it is parsed with size, depth and time limits, and APK, DEX and media parsing runs in the `:parser` isolated process. Every new parser has a fuzz harness.
- [ ] **Tests:** unit tests cover the change, plus instrumented tests where platform APIs are involved. Performance budgets are checked on the reference mid-range device where they apply.
- [ ] **No live malware, secrets or signing material:** no samples, private keys, keystores, `local.properties` or tokens are included.
- [ ] **Roadmap:** a task's box is ticked only if every acceptance criterion is met and its `Verify` commands passed.
- [ ] If a coding agent wrote part of this change, I have reviewed all of it, and the agent followed `AGENTS.md`.

## Security and compatibility

<!--
Security: does this change how untrusted input is parsed (APKs, archives,
DEX, DNS packets, QR codes, media, shared text), intel bundle signature
verification, the Keystore or Tink, IPC or exported components, network
destinations, or CI workflows and their permissions? Describe the effect.
Never describe an undisclosed vulnerability here: report it privately as
SECURITY.md describes.
Compatibility: does this change the intel bundle format, the database schema
(Room migrations), a setting, or the applicationId (which can't change after
the first Play upload)? Write "None" if neither applies.
-->

## Screenshots

<!-- For UI changes: light, dark, and 200% font scale. Remove this section otherwise. -->

## Notes for reviewers

<!-- What to look at first, known limits, and follow-up work. -->
