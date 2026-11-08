---
name: pre-pr-check
description: Use when finishing a task or before the user opens a pull request. Runs the Aegis definition-of-done check on the current uncommitted changes (build and test gates, doc sync, review agents) and ends with a completion summary and a suggested commit message, without committing.
---

# Check the changes before a pull request

## Current changes

!`git status --short`

!`git diff --stat HEAD`

This applies the definition of done (`AGENTS.md`, "Definition of done for
a roadmap task") to the changes above. It never commits, stages or
pushes.

## 1. Run the gates that exist

These match the CI gates in `.github/workflows/ci.yml` and `codeql.yml`
(`docs/04-quality.md`, "CI (GitHub Actions)"). Run each one that exists
in the build, and record its actual result. Mark a gate *not available
yet* if the build doesn't define it, and never report a pass you didn't
see.

1. `./gradlew checkModuleGraph checkSourceRules`: the module dependency
   rules (`docs/01-architecture.md`, "Dependency rules"). Also, in
   production sources only: no direct logging, hard-coded
   `Dispatchers.IO`/`Default`/`Main`/`Unconfined`, `GlobalScope`,
   `runBlocking`, or engine `!!`.
2. `./gradlew :build-logic:convention:test`: unit tests for those checks.
3. `./gradlew assembleDebug assembleRelease`
4. `./gradlew test koverVerify`: unit tests, plus the ≥ 80% line-coverage
   gate on `aegis.jvm.engine` modules.
5. `./gradlew lint detekt ktlintCheck`
6. `./gradlew licensee`: the dependency license allowlist.
7. Module-specific tests for the touched modules.
8. `python3 .claude/hooks/test_hooks.py`: the hook self-tests, which CI
   also runs.

Gates that don't run here:

- **CodeQL** (`codeql.yml`) runs only in CI. List it as *not run
  locally*.
- **Jazzer fuzz runs** and **`tools/intel-pipeline` tests** are planned.
  Mark them *not available yet* until the build defines them.
- **Instrumented tests** (`connectedDebugAndroidTest`) need a device or an
  emulator. Run them if one is attached (`adb devices`). Otherwise list
  them as *not run*. CI runs them on API 29 and API 37 emulators.

**If a dependency or plugin changed,** remember that dependency
verification is strict:

- Confirm that `gradle/verification-metadata.xml` was regenerated
  (`docs/02-dependency-policy.md`, "Dependency verification"), and that
  its diff adds only the expected components.
- Gradle records only the artifacts it resolved. So also confirm that the
  test-platform artifacts were recorded, by running
  `connectedDebugAndroidTest` with a device attached.
- When AGP changes, confirm that the `osx` and `windows` `aapt2` jars for
  the new version were added by hand, with hashes checked against Google
  Maven. A Linux run records only the `linux` jar, so CI stays green while
  macOS and Windows builds fail verification.

## 2. Check that the docs are in sync

Check each item against the diff:

- [ ] Every behavior change maps to a requirement or spike ID, and the
      spec reflects it.
- [ ] A new or changed permission or exported component appears in
      `docs/permissions-and-policy.md`.
- [ ] A new stored, retained, or transmitted data item appears in
      `docs/privacy.md`.
- [ ] The review triggers in `docs/threat-model.md` ("B4. Review
      triggers") are handled: a new parser, network destination, data
      source, exported component, permission, or verdict-precedence
      change.
- [ ] A new external data source appears in `docs/data-sources.md`, with
      verified terms.
- [ ] Significant decisions have an ADR in `docs/decisions.md`.
- [ ] User-facing strings follow `.claude/rules/ui-and-copy.md`.
- [ ] A roadmap task's box in `docs/roadmap.md` is ticked only if every
      acceptance criterion is met and its `Verify` commands passed in
      this session.

## 3. Get reviews

Delegate based on what changed:

- `security-reviewer`: always, for code changes in `engine/`, `shield/`,
  `intel/`, `core/security/`, `ai/guard/`, manifests, or IPC
- `policy-privacy-reviewer`: manifests, permissions, data flows, strings,
  or `docs/data-sources.md`
- `detection-quality-reviewer`: rules, IOCs, models, verdict logic, or
  intel pipeline output

Fix the findings, or explain why each one doesn't apply.

## 4. Report

**Don't commit, stage, or push.** Give the user:

1. A summary of the changes and the files affected.
2. The validation performed, with results.
3. The checks not run, and why.
4. Your assumptions, open issues, and residual risks.
5. A suggested Conventional Commit message: `type(scope): summary`, with a
   short body that references the requirement IDs.
