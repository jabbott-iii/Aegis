---
name: run-spike
description: Use when asked to start, run, or record a Phase 0 technical spike (S1–S7 in docs/roadmap.md). Plans, runs, and writes up the spike, then feeds its outcome back into the ADRs and specs.
argument-hint: "<spike-id, e.g. S1>"
arguments: [spike]
disable-model-invocation: true
---

# Run a spike

Spike: `$spike`

A spike answers one platform question with evidence. Its code is
throwaway. Its write-up is what gets kept.

## 1. Frame it

1. Read the `$spike` task in `docs/roadmap.md` ("Phase 0 — Foundations
   and technical spikes"): the question, what it blocks, and its pass
   criteria.
2. Read every doc section that references `$spike`
   (`grep -rn "$spike\b" docs/`), so you know exactly which decisions and
   UI claims depend on it.
3. Create `docs/spikes/$spike-<short-slug>.md` from
   [template.md](template.md). Fill in the question, the pass criteria,
   and the method **before** writing any code.

## 2. Build the experiment

- Put spike code in `spikes/$spike-<slug>/` (a standalone Gradle module
  or app).
- Work on a branch named `spike/$spike-<slug>`, and tell the user to
  create it if it doesn't exist. Don't create branches or commit
  yourself.
- Production modules must never import spike code. It still follows the
  security rules in `AGENTS.md` ("Non-negotiable rules"): no TLS
  interception, no live malware, no secrets.
- Instrument the experiment with timings, memory, battery stats, and
  logs, so the results are numbers and not impressions.

## 3. Run it

- Cover the device matrix that the pass criteria require
  (`docs/04-quality.md`, "Device matrix").
- Where a physical device run is needed, give the user exact steps and
  commands, and record their results. **Never invent measurements.**
- Record raw results in the spike doc's results table, with the device,
  OS, build, and date.

## 4. Decide and propagate

1. State the outcome against each pass criterion: pass, fail, or
   inconclusive (with what's missing).
2. Update every affected doc:
   - **The ADRs the spike tests** (`docs/decisions.md`; for example
     ADR-0003 is validated in S1–S3): the outcome makes each *Accepted*,
     *Rejected*, or *Revised*. Only the owner accepts an ADR, so put the
     outcome to them, and once they decide, change only its `Status` line,
     its row in the index table at the top of `docs/decisions.md`, and the
     status line above that table. A revision goes through
     `/record-decision`.
   - **The spec sections that waited on this spike.** For example, S3
     decides the AI Crawler Shield copy, and S2 sets the route cap.
   - **The spike's task in `docs/roadmap.md`:** indented `Note:` lines
     with the outcome and the key numbers. Tick its box only as
     `AGENTS.md` ("Definition of done for a roadmap task") allows.
   - **`docs/roadmap.md` and `docs/00-overview.md` ("Scope estimate"),**
     if estimates or scope changed.
   - **`docs/00-overview.md` ("Top risks and mitigations"),** for new
     risks.
3. Summarize for the user: the outcome, its evidence, what it unblocks,
   and any new risks. Don't commit.
