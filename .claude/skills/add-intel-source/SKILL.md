---
name: add-intel-source
description: Onboard a new external data source (IOC feed, blocklist, crawler IP ranges, registry data) into the Aegis threat-intel pipeline, covering terms verification, DATA_SOURCES entry, adapter, validation, and attribution. Use before ingesting any new external data.
argument-hint: "[source name or URL]"
---

# Add intel source: $ARGUMENTS

## 1. Verify terms first (no code until this is done)

1. Fetch the source's own terms, license, or API page with WebFetch, and quote the relevant lines. Record:
   - the license or terms (CC-BY, MIT, fair use, non-commercial, paid tier for commercial use, …)
   - attribution requirements
   - whether an API key or account is needed (keys stay in CI secrets, never in the app or repo)
   - rate limits
2. Add a row to `docs/DATA_SOURCES.md` with the verified date. If any term is unclear or restricts commercial use, mark the source **To verify** or note the decision it needs, and tell the user. Open decision #2 (commercial intent) may block it.
3. If the source supplies malware **samples** rather than indicators, stop. Samples never enter this repo or pipeline (TESTING §2). Only hashes and metadata are allowed.

## 2. Adapter

- Add the adapter to `tools/intel-pipeline/` with one adapter per source format. Pin the source URL in config, and record the ETag and fetch time.
- Normalize to the bundle schema, with provenance on every entry: source ID, operator, and fetchedAt.
- Add golden-file tests built from a small, **redistributable** excerpt of the source format. If redistribution isn't allowed, use synthetic data in the same shape.

## 3. Validation gates

The new source must pass the existing gates: schema, prefix breadth (IPv4 /16 or longer, IPv6 /32 or longer), canary allowlist, the churn limit, and the evidence rule for curated configs. Add source-specific checks where needed, such as a maximum entry count or field formats.

## 4. Attribution and privacy

- Make sure `attribution.json` generation includes the source if its license requires attribution.
- Confirm that the app never contacts the source directly. Only CI fetches it.

## 5. Docs and review

- Update the feature spec that consumes the data (for example, ai-crawler-shield §4 for crawler ranges).
- Delegate to `detection-quality-reviewer` for false-positive and collateral-blocking risk, and to `policy-privacy-reviewer` for licensing.
