---
name: implement-requirement
description: Implement an Aegis requirement end to end (FR-*/NFR-* ID), working from spec to tests, code, docs, and review. Use when asked to build, implement, or start work on a requirement, feature, or phase task.
argument-hint: "[requirement-id, e.g. FR-SCAN-02]"
arguments: [req]
---

# Implement requirement $req

Follow these steps in order. Stop and ask the user whenever a step turns up ambiguity, a conflict between docs, or a change that is hard to reverse.

## 1. Locate and understand

1. Find the requirement row with `grep -n "$req" docs/REQUIREMENTS.md`. Note its priority and linked spec.
2. Read the linked spec section in `docs/features/`, including its acceptance criteria. Read the relevant parts of `docs/ARCHITECTURE.md` and any ADR the spec cites.
3. Find the phase in `docs/PLAN.md`, then check the **gates**:
   - Is a spike prerequisite unfinished? For example, AI Crawler Shield copy waits on S3, the route cap on S2, and the FGS type on S1.
   - Do other requirements or modules need to exist first?
   - Does this depend on an open decision in PLAN §6?

   If a gate is open, stop and report what's blocking.
4. Search the code for existing implementations and patterns: `Grep` for related types, and look in the module named in ARCHITECTURE §3.

## 2. Plan (short, in chat)

List:
- the files or modules to create or change
- the tests to write
- the docs to update
- every new permission, exported component, data flow, or dependency, each of which triggers the doc updates in step 5
- any assumption you're making

Keep it brief. Ask the user only if a decision is genuinely theirs to make.

## 3. Tests first (engine and core logic)

- Write failing unit tests that encode the spec's acceptance criteria.
- Parsers also need malformed-input cases and a Jazzer fuzz target.
- Platform behavior (VPN, PackageManager, notification listener) needs an instrumented test plan, or tests that use the fixture apps in `testing/fixtures/`.

## 4. Implement

- Follow `CLAUDE.md` non-negotiables and the path-scoped rules in `.claude/rules/`, which load automatically when you touch matching files.
- Keep to the smallest complete change. No unrelated refactors, renames, or dependency bumps.
- Fix any post-edit hook warnings before moving on.

## 5. Update docs in the same change

| If the change… | Update |
|---|---|
| Alters behavior or acceptance criteria | The feature spec |
| Adds a permission or exported component | `docs/PERMISSIONS_AND_POLICY.md` (run `/add-permission`) |
| Stores, retains, or sends data | `docs/PRIVACY.md` data inventory and the Data safety mapping |
| Hits a THREAT_MODEL B4 trigger (new parser, network destination, data source, precedence change, …) | `docs/THREAT_MODEL.md` |
| Makes a significant decision | A new ADR (run `/new-adr`) |
| Adds an external data source | `docs/DATA_SOURCES.md` (run `/add-intel-source`) |

## 6. Verify and review

1. Run the narrowest relevant checks first (`./gradlew :<module>:test`), then the broader ones. Report actual results and never claim a pass you didn't see.
2. Delegate reviews by change type:
   - `security-reviewer`: parsers, crypto, IPC, exported components, the network, or storage
   - `policy-privacy-reviewer`: manifest, permissions, data flows, or user-facing claims
   - `detection-quality-reviewer`: rules, IOCs, models, or verdict logic
3. Address the findings, or explain why a finding doesn't apply.

## 7. Finish

Run `/pre-pr-check`. **Don't commit.** End with the completion summary from CLAUDE.md and a suggested Conventional Commit message.
