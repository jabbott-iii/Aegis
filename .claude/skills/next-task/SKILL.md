---
name: next-task
description: Pick the next unblocked task from docs/roadmap.md (or the task ID given) and carry it through the definition of done in AGENTS.md, leaving changes uncommitted for review.
argument-hint: "[task-id, e.g. P1.2 or S1]"
disable-model-invocation: true
---

# Work the next roadmap task

Requested task: `$ARGUMENTS` (empty means "pick the next unblocked task").

## 1. Choose the task

1. Open `docs/roadmap.md`.
2. If a task ID was given, use it. If its `Depends` tasks are not all
   ticked, stop and tell the user which ones block it.
3. Otherwise, take the **first** unticked task whose `Depends` are all
   ticked.
   - Skip a task whose remaining work is marked
     `⏳ needs <device or OEM>` when this session lacks that hardware.
     `adb devices` shows what is attached.
   - Mention any task you skipped and why.
4. Check the task's gates before you write code that relies on them:
   - **A `Proposed` ADR** (`docs/decisions.md`): until P0.9 (ADR review
     after the spikes), Phase 0 tasks and spikes go ahead on the `Proposed`
     ADRs. The spikes exist to test them (for example, ADR-0003 is
     validated in S1–S3). After P0.9, if a task relies on an ADR that is
     still `Proposed`, show the user its decision in two or three lines
     and ask them to accept, change or reject it. Record the answer in its
     `Status` line and in its row of the index at the top of
     `docs/decisions.md`, plus the status line above the index. If they
     changed the decision, record a new ADR with `/record-decision`.
   - **An open decision** (`docs/00-overview.md`, "Open decisions"): show
     it the same way and ask the owner to decide first. For example, #8
     must be settled before the first of `engine:scanner`, `engine:ml`,
     `intel:client` or a non-pure `ai:guard` is created, and #9 before
     Phase 1 stores anything in `app.db`. Record their answer in that item
     with the date, as #5 records minSdk 29, and with `/record-decision`
     when it is a significant decision.
   - **A spike:** work that waits on one stays blocked until the spike's
     task is ticked. AI Crawler Shield copy waits on S3, route caps wait
     on S2, and the foreground-service type waits on S1 (`AGENTS.md`, "How
     to work here").
5. Tell the user in one or two sentences which task you're starting and
   how you plan to approach it. Then create a task list. Its last item is
   "Verify and report".

## 2. Load context, and only the context you need

- `AGENTS.md` is already loaded (`CLAUDE.md` imports it). Re-read its
  "Non-negotiable rules" if unsure.
- Read the docs that `AGENTS.md` → "Read before working" lists for the
  modules the task touches: the subsystem spec, the feature spec and its
  acceptance criteria, and the requirement rows in `docs/requirements.md`.
- Read the existing code in those modules before writing new code. Follow
  established patterns and naming.
- If code and docs disagree, stop and ask which one is right (`AGENTS.md`
  rule 17). If the spec is ambiguous, or the change would be hard to
  reverse, weaken a security control or break compatibility: stop,
  describe the conflict, propose the safest option, and ask (rule 16).

## 3. Implement the smallest complete change

- Do exactly what the task's `Do` lines and acceptance criteria require.
  Nothing else: no unrelated refactors, renames or dependency bumps.
- **A spike (S1–S7)?** Read `.claude/skills/run-spike/SKILL.md` and its
  `template.md`, and follow their steps. You can't invoke `/run-spike`
  yourself. The spike's `Verify` line is met by the write-up in
  `docs/spikes/S<n>-<slug>.md`.
- **Requirement work (FR-*, NFR-*)?** Follow `/implement-requirement` for
  each requirement ID the task names.
- **A new module?** Follow `/new-module`.
- **A permission, special access, exported component or foreground
  service type?** Follow `/add-permission`.
- **New external data?** Follow `/add-intel-source`.
- **A new dependency?** Follow `docs/02-dependency-policy.md` ("Before
  adding a dependency"). It goes in the version catalog with an allowed
  license, and `gradle/verification-metadata.xml` is regenerated with its
  diff reviewed.
- **A design decision changed?** Run `/record-decision` and update the
  affected docs.
- Write the tests the acceptance criteria call for, test first for engine
  logic. Every parser gets a fuzz harness.

## 4. Verify honestly

- Run every command in the task's `Verify` line. Never claim a pass you
  didn't see (`AGENTS.md` rule 14).
- Then run `/pre-pr-check`. It runs the gates that exist, checks that the
  docs are in sync, delegates to the review agents, and drafts the
  report.
- Fix failures by fixing the code.
  - Never disable dependency verification or the license check
    (`licensee`) to get a build green
    (`.claude/rules/build-and-dependencies.md`).
  - Never weaken a lint, detekt or ktlint rule, the Kover gate or
    `checkSourceRules` (no `-x test`, `abortOnError = false`, detekt
    baselines or `@Suppress`), and never delete or disable a test, to
    make a check pass, without the owner's explicit approval (rule 16).
- Instrumented tests need an emulator or a device. If none is attached,
  they count as not run.
- Criteria that need a device, OEM hardware, or manual observation:
  1. Do everything possible here. For a device run, give the user exact
     steps and commands, and record their results. Never invent
     measurements.
  2. Leave the box unticked.
  3. Add an indented
     `⏳ needs <device, OEM or tool>: <exact remaining check>` line under
     the task in the roadmap.

## 5. Update the roadmap and docs

- Tick the box **only** if every acceptance criterion is met and every
  verification command ran and passed in this session (`AGENTS.md`,
  "Definition of done for a roadmap task").
- Add brief indented `Note:` lines under the task where useful: versions
  chosen, measured numbers and the devices they came from, conventions
  chosen, and follow-ups for later tasks (`Note (from P0.8): …` under the
  later task).
- Update the docs in the same change: the spec, and
  `docs/permissions-and-policy.md`, `docs/privacy.md` or
  `docs/threat-model.md` whenever their review triggers apply.
- If the task changes what exists or what passes, update the **Status:**
  paragraph and the "Module map" Status column in `AGENTS.md`,
  `README.md` ("Status"), and the status line at the top of
  `docs/roadmap.md`.

## 6. Do not commit

Leave all changes uncommitted (`AGENTS.md` rule 15). Do not commit,
amend, push, rebase, reset or tag. Suggest a Conventional Commit message
in the report.

## 7. Report

End with a summary containing:

- **Task:** ID and title, and whether its box is now ticked.
- **Changes:** what changed, and the files affected.
- **Validation:** each command run, with its real result.
- **Not run:** checks that weren't run, and why. Include `⏳` items.
- **Assumptions** you made.
- **Open issues and residual risks.**
- **Commit message:** a suggested Conventional Commit message that
  references the requirement or spike IDs.
- **Next:** the next unblocked task ID.
