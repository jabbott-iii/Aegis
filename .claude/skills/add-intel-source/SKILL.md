---
name: add-intel-source
description: Use before ingesting any new external data into the Aegis threat-intel pipeline (an IOC feed, blocklist, crawler IP ranges, registry data). Verifies the terms first, then adds the docs/data-sources.md entry, the adapter, the validation gates and the attribution.
argument-hint: "<source name or URL>"
---

# Add an intel source

Source: `$ARGUMENTS`

External data reaches Aegis only through the CI pipeline, never through
the app. Its terms decide whether we may use it at all, so they come
first: write no code until step 1 is done.

## 1. Verify the terms first

1. Fetch the source's own terms, license or API page with WebFetch, and
   quote the relevant lines. Record:
   - the license or terms (CC-BY, MIT, fair use, non-commercial, a paid
     tier for commercial use, …)
   - attribution requirements
   - whether an API key or account is needed (keys stay in CI secrets,
     never in the app or the repo)
   - rate limits
2. Add a row to `docs/data-sources.md` with the date you verified it.
   - If any term is unclear or restricts commercial use, mark the source
     **To verify** or note the decision it needs, and tell the user.
   - Open decision #2, commercial or non-commercial
     (`docs/00-overview.md`, "Open decisions"), may block it.
3. **If the source supplies malware samples** rather than indicators,
   stop. Samples never enter this repo or the pipeline (`AGENTS.md`
   rule 12; `docs/04-quality.md`, "Malware sample handling"). Only hashes
   and metadata are allowed.

## 2. Write the adapter

`tools/intel-pipeline/` is planned for Phase 2. If it doesn't exist yet,
tell the user: it is created with `/new-module`.

- Add the adapter to `tools/intel-pipeline/`, one adapter per source
  format. Pin the source URL in config, and record the ETag and the fetch
  time.
- Normalize to the bundle schema, with provenance on every entry: the
  source ID, the operator, and `fetchedAt`.
- Add golden-file tests built from a small, **redistributable** excerpt of
  the source format. If redistribution isn't allowed, use synthetic data
  in the same shape.

## 3. Pass the validation gates

The new source must pass the existing gates:

- the schema
- prefix breadth (IPv4 /16 or longer, IPv6 /32 or longer)
- the canary allowlist
- the churn limit
- the evidence rule for curated configs

Add source-specific checks where needed, such as a maximum entry count or
field formats.

## 4. Attribution and privacy

- Make sure `attribution.json` generation includes the source if its
  license requires attribution (`docs/data-sources.md`, "Attribution in
  the app").
- Confirm that the app never contacts the source directly. Only CI
  fetches it (`AGENTS.md` rule 7).

## 5. Docs and review

- Update the feature spec that consumes the data, for example
  `docs/features/ai-crawler-shield.md` ("Data: crawler range sources")
  for crawler ranges.
- Delegate to `detection-quality-reviewer` for false-positive and
  collateral-blocking risk, and to `policy-privacy-reviewer` for
  licensing.

## 6. Report

- The source, its terms with the quoted lines, and the date you verified
  them.
- The `docs/data-sources.md` row, and anything still **To verify** or
  waiting on a decision.
- The files changed, each test command with its real result, and the
  reviewers' findings.
