---
name: run-spike
description: Plan, run, and write up a Phase 0 technical spike (S1–S7) from docs/PLAN.md, then feed its outcome back into ADRs and specs. Use when asked to start, run, or record a spike.
argument-hint: "[spike-id, e.g. S1]"
arguments: [spike]
disable-model-invocation: true
---

# Run spike $spike

A spike answers one platform question with evidence. Its code is throwaway. Its write-up is what gets kept.

## 1. Frame it

1. Read the $spike row in `docs/PLAN.md` § Phase 0: the question, what it blocks, and its pass criteria.
2. Read every doc section that references $spike (`grep -rn "$spike\b" docs/`), so you know exactly which decisions and UI claims depend on it.
3. Create `docs/spikes/$spike-<short-slug>.md` from [template.md](template.md). Fill in the question, the pass criteria, and the method **before** writing any code.

## 2. Build the experiment

- Put spike code in `spikes/$spike-<slug>/` (a standalone Gradle module or app). Work on a branch named `spike/$spike-<slug>`, and tell the user to create it if it doesn't exist. Don't create branches or commit yourself.
- Spike code must never be imported by production modules. It still has to follow the security non-negotiables: no TLS interception, no live malware, no secrets.
- Instrument the experiment with timings, memory, battery stats, and logs, so the results are numbers and not impressions.

## 3. Run it

- Cover the device matrix that the pass criteria require (`docs/TESTING.md` §5). Where a physical device run is needed, give the user exact steps and commands, and record their results. **Never invent measurements.**
- Record raw results in the spike doc's results table, with device, OS, build, and date.

## 4. Decide and propagate

1. State the outcome against each pass criterion: pass, fail, or inconclusive (with what's missing).
2. Update every affected doc:
   - the ADR status in `docs/adr/README.md` and the ADR itself (*Accepted*, *Rejected*, or *Revised*)
   - the spec sections that waited on this spike (for example, S3 decides the AI Crawler Shield copy, and S2 sets the route cap)
   - `docs/PLAN.md`, if estimates or scope changed
3. Summarize for the user: the outcome, its evidence, what it unblocks, and any new risks. Don't commit.
