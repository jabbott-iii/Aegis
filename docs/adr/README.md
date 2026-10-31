# Architecture Decision Records

Each ADR records one significant decision: its context, the decision itself, its consequences,
and the alternatives that were considered. The status is one of *Proposed*, *Accepted*,
*Superseded by ADR-NNNN*, or *Rejected*. Every ADR below starts as **Proposed**. The owner marks
it Accepted, revises it, or rejects it after the Phase 0 spikes.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-offline-first-on-device-verdicts.md) | Offline-first, on-device verdicts | Proposed |
| [0002](0002-no-tls-interception.md) | No TLS interception | Proposed |
| [0003](0003-local-vpn-split-routing.md) | Local VPN with split routing for network protection | Proposed (validate in S1–S3) |
| [0004](0004-no-accessibility-service.md) | Do not use AccessibilityService | Proposed |
| [0005](0005-llms-excluded-from-verdict-path.md) | LLMs excluded from the verdict path | Proposed |
| [0006](0006-signed-threat-intel-updates.md) | Signed, versioned threat-intel bundles with delegated signing | Proposed |

## Template

```markdown
# ADR-NNNN: Title

- Status: Proposed
- Date: YYYY-MM-DD
- Related: requirement IDs, specs

## Context
What forces are at play? What constraints (platform, policy, privacy, cost)?

## Decision
What we will do, stated plainly.

## Consequences
What becomes easier or harder. Risks accepted. Follow-up work.

## Alternatives considered
Each option, and why it was not chosen.
```
