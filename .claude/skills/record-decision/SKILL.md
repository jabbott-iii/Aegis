---
name: record-decision
description: Use when a significant technical or policy decision for Aegis is made, revised, or superseded. Appends an architecture decision record to docs/decisions.md with the next number and the file's template, and links it from the docs it affects.
argument-hint: "<short decision title>"
---

# Record an architecture decision

Title: `$ARGUMENTS`

1. Read `docs/decisions.md`. The next number is the highest `ADR-NNNN`
   plus one, zero-padded to four digits.
2. Draft the ADR as `## ADR-NNNN — <title>`, using the template at the top
   of that file exactly:
   - **Status:** starts as `Proposed`. Only the owner marks an ADR
     `Accepted`.
   - **Date:** today's date.
   - **Related:** the requirement IDs and specs it affects.
   - **Context:** the real constraints (platform, Play policy, privacy,
     cost), citing the doc sections or sources behind them.
   - **Decision:** stated plainly enough to check code against.
   - **Consequences:** what gets harder, the risks accepted, and the
     follow-up work.
   - **Alternatives considered:** each option, and why it wasn't chosen.
   - **Revisit when:** a concrete, observable trigger, not "if needed".
3. **If this supersedes an earlier ADR:**
   - Change only that ADR's `Status` line to `Superseded by ADR-NNNN`.
     Don't rewrite its text: history stays intact.
   - Add the back-link: name the old ADR in the new one's `Related` line.
4. **If the owner accepts or rejects a `Proposed` ADR** without changing
   it, change only its `Status` line (`Accepted`, or `Rejected` with the
   date).
5. **If the ADR changes a non-negotiable rule** (`AGENTS.md`, rules 1–13),
   flag it clearly to the user. `AGENTS.md`, the matching `.claude/rules/`
   file, and possibly the hooks (and their description in `CLAUDE.md`)
   must change once the ADR is accepted, never before.
6. Append the new ADR at the end of `docs/decisions.md`. Keep the index
   in the file's header in step: add a row to its table, update the status
   line above it, and change a row's status whenever step 3 or 4 changes
   an ADR's `Status` line.
7. Reference the ADR from every spec, requirement, or roadmap task it
   affects, and update every doc that states the old decision:
   - `AGENTS.md`, the numbered docs, and the subsystem and feature specs
   - `docs/requirements.md` and `docs/roadmap.md`
   - `docs/permissions-and-policy.md`, `docs/privacy.md`,
     `docs/threat-model.md` or `docs/data-sources.md`, where it touches
     them
8. Don't commit. Show the user the ADR, its status, and the list of files
   you touched. If it's `Proposed`, say what the owner needs to decide.
