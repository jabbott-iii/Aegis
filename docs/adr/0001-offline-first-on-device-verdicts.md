# ADR-0001: Offline-first, on-device verdicts

- Status: Proposed
- Date: 2026-10-31
- Related: NFR-PRIV-01, FR-INTEL-04, [PRIVACY](../PRIVACY.md)

## Context

Most commercial mobile antivirus products send app hashes, URLs, or samples to cloud services
to get verdicts. Cloud lookups improve detection of new threats, but they leak which apps and
sites a user has, and they need a backend, API keys, and an ongoing operating cost. Aegis is
a privacy-focused project with no backend today. Some useful cloud APIs also have restrictive
terms. For example, Google Safe Browsing is limited to non-commercial use.

## Decision

All verdicts are computed **on the device** from a signed intel bundle that is downloaded from
static hosting. A baseline bundle ships in the APK. Cloud lookups (URL reputation, Play
Integrity, hash reputation) are **optional, opt-in P2 features**, and each requires a backend
proxy plus its own privacy review.

## Consequences

- **Easier:** strong privacy claims, no server to run, protection that works offline, and a simple Data safety form.
- **Harder:** brand-new threats are caught only once the bundle updates or a heuristic or the ML model flags them. APK size and memory budgets have to accommodate the blocklists and models.
- **Accepted risk:** a lower zero-day detection rate than cloud-backed competitors. Mitigated by daily bundles plus heuristics and ML.

## Alternatives considered

- **A cloud verdict service:** better freshness, but needs a backend and leaks data. Deferred.
- **Hash-prefix lookups** (k-anonymity, in the style of Safe Browsing): a good privacy compromise for a future P2 feature, but it still needs a backend.
