---
name: pre-pr-check
description: Run the Aegis definition-of-done check on the current uncommitted changes, covering build and test gates, doc sync, review agents, and a completion summary with a suggested commit message, without committing. Use when finishing a task or before the user opens a PR.
---

# Pre-PR check

## Current changes

!`git status --short`

!`git diff --stat HEAD`

## 1. Run the gates that exist

Run each one that exists in the build and record its actual result. Mark a gate *not available yet* if the build doesn't define it, and never report a pass you didn't see.

1. `./gradlew assembleDebug`
2. `./gradlew test`
3. `./gradlew lint detekt ktlintCheck`
4. Module-specific tests for the touched modules
5. `python3 .claude/hooks/test_hooks.py` if anything under `.claude/hooks/` changed

Instrumented tests (`connectedDebugAndroidTest`) need a device or emulator. Run them if one is attached (`adb devices`). Otherwise list them as *not run*.

## 2. Check that the docs are in sync

Check each item against the diff:

- [ ] Every behavior change maps to a requirement or spike ID, and the spec reflects it.
- [ ] A new or changed permission or exported component appears in `docs/PERMISSIONS_AND_POLICY.md`.
- [ ] A new stored, retained, or transmitted data item appears in `docs/PRIVACY.md`.
- [ ] THREAT_MODEL B4 triggers are handled: a new parser, network destination, data source, exported component, permission, or verdict-precedence change.
- [ ] A new external data source appears in `docs/DATA_SOURCES.md` with verified terms.
- [ ] Significant decisions have an ADR.
- [ ] User-facing strings follow `.claude/rules/ui-and-copy.md`.

## 3. Review

Delegate based on what changed:
- `security-reviewer`: always, for code changes in `engine/`, `shield/`, `intel/`, `core/security/`, `ai/guard/`, manifests, or IPC
- `policy-privacy-reviewer`: manifests, permissions, data flows, strings, or DATA_SOURCES
- `detection-quality-reviewer`: rules, IOCs, models, verdict logic, or intel pipeline output

Fix the findings, or explain why each one doesn't apply.

## 4. Report

**Don't commit, stage, or push.** Give the user:

1. A summary of the changes and the files affected.
2. The validation performed, with results.
3. The checks not run, and why.
4. Your assumptions, open issues, and residual risks.
5. A suggested Conventional Commit message (`type(scope): summary` with a short body that references the requirement IDs).
