---
name: implement-requirement
description: Use when asked to build, implement, or start work on an Aegis requirement (an FR-* or NFR-* ID), feature, or phase task. Carries it end to end, from the spec to tests, code, docs and review.
argument-hint: "<requirement-id, e.g. FR-SCAN-02>"
arguments: [req]
---

# Implement a requirement

Requirement: `$req`

Every change maps to a requirement or spike ID. Follow these steps in
order. Stop and ask the user whenever a step turns up ambiguity, a
conflict between docs, or a change that is hard to reverse.

## 1. Locate and understand

1. Find the requirement's row with `grep -n "$req" docs/requirements.md`.
   Note its priority and its linked spec.
2. Read the linked spec section in `docs/features/`, including its
   acceptance criteria. Read the relevant parts of
   `docs/01-architecture.md`, the subsystem spec in `docs/subsystems/` for
   the module, and any ADR the spec cites (`docs/decisions.md`).
3. Find the phase and the task that owns it in `docs/roadmap.md`, then
   check the **gates**:
   - Is a spike prerequisite unfinished? For example, AI Crawler Shield
     copy waits on S3, the route cap on S2, and the FGS type on S1.
   - Do other requirements or modules need to exist first (the task's
     `Depends`)?
   - Does it depend on an open decision (`docs/00-overview.md`, "Open
     decisions")?

   If a gate is open, stop and report what's blocking.
4. Search the code for existing implementations and patterns: `Grep` for
   related types, and look in the module that `docs/01-architecture.md`
   ("Module layout") names.

## 2. Plan (short, in chat)

List:

- the files or modules to create or change
- the tests to write
- the docs to update
- every new permission, exported component, data flow, or dependency,
  each of which triggers the doc updates in step 5
- any assumption you're making

Keep it brief. Ask the user only if a decision is genuinely theirs to
make.

## 3. Write the tests first (engine and core logic)

- Write failing unit tests that encode the spec's acceptance criteria.
- Parsers also need malformed-input cases and a Jazzer fuzz target.
- Platform behavior (VPN, PackageManager, notification listener) needs an
  instrumented test plan, or tests that use the fixture apps in
  `testing/fixtures/`.

## 4. Implement

- Follow the non-negotiable rules in `AGENTS.md`, and the path-scoped
  rules in `.claude/rules/`, which load automatically when you touch
  matching files.
- Keep to the smallest complete change. No unrelated refactors, renames,
  or dependency bumps.
- Fix any post-edit hook warnings before moving on.

## 5. Update the docs in the same change

| If the change… | Update |
| :- | :- |
| Alters behavior or acceptance criteria | The feature spec, and the subsystem spec where the design changed |
| Adds a permission or exported component | `docs/permissions-and-policy.md` (run `/add-permission`) |
| Stores, retains, or sends data | `docs/privacy.md` ("Data inventory"), and the Data safety mapping in `docs/permissions-and-policy.md` ("Data safety form") |
| Hits a review trigger in `docs/threat-model.md` ("B4. Review triggers"): a new parser, network destination, data source, precedence change, … | `docs/threat-model.md` |
| Makes a significant decision | A new ADR in `docs/decisions.md` (run `/record-decision`) |
| Adds an external data source | `docs/data-sources.md` (run `/add-intel-source`) |

## 6. Verify and review

1. Run the narrowest relevant checks first (`./gradlew :<module>:test`),
   then the broader ones. Report the actual results, and never claim a
   pass you didn't see.
2. Delegate reviews by change type:
   - `security-reviewer`: parsers, crypto, IPC, exported components, the
     network, or storage
   - `policy-privacy-reviewer`: the manifest, permissions, data flows, or
     user-facing claims
   - `detection-quality-reviewer`: rules, IOCs, models, or verdict logic
3. Address the findings, or explain why a finding doesn't apply.

## 7. Finish

- Run `/pre-pr-check`. **Don't commit.**
- End with the completion summary that `AGENTS.md` ("Definition of done
  for a roadmap task") describes: what changed and the files affected, the
  validation you ran, what you didn't run, your assumptions, open issues,
  and residual risks. Add a suggested Conventional Commit message.
- If `/next-task` started this work, go back to it: it ticks the roadmap
  box once the task is verified.
