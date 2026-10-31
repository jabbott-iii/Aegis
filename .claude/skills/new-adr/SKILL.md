---
name: new-adr
description: Create a new Architecture Decision Record in docs/adr/ with the next number, using the project template, and link it from the index and affected specs. Use when a significant technical or policy decision is made or revised.
argument-hint: "[short decision title]"
---

# New ADR: $ARGUMENTS

1. **Number:** list `docs/adr/` and use the next four-digit number after the highest existing `NNNN-*.md`.
2. **File:** `docs/adr/NNNN-<kebab-case-title>.md`. Use the template in `docs/adr/README.md` exactly: Status, Date (today), Related, then Context, Decision, Consequences, and Alternatives considered.
3. **Status** starts as **Proposed**. Only the user marks an ADR *Accepted*.
4. **Content rules:**
   - The Context section states the real constraints (platform, Play policy, privacy, cost) and cites the doc sections or sources behind them.
   - The Decision section is stated plainly enough to check code against.
   - The Consequences section names what gets harder, the risks accepted, and the follow-up work.
   - The Alternatives section covers each option and why it wasn't chosen.
5. **Superseding:** if this ADR replaces an older one, set the old ADR's status to `Superseded by ADR-NNNN` and add a back-link.
6. **Changing a non-negotiable:** if the ADR changes one of the CLAUDE.md non-negotiables, flag it clearly to the user. CLAUDE.md, the matching `.claude/rules/` file, and possibly the hooks must change once the ADR is accepted, never before.
7. **Index:** add a row to the table in `docs/adr/README.md`.
8. **Links:** reference the ADR from every spec, requirement, or plan section it affects.
9. Don't commit. Show the user the ADR and the list of files you touched.
