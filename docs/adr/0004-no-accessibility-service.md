# ADR-0004: Do not use AccessibilityService

- Status: Proposed
- Date: 2026-10-31
- Related: FR-AI-03, FR-AI-05, [PERMISSIONS_AND_POLICY](../PERMISSIONS_AND_POLICY.md)

## Context

An AccessibilityService could let Aegis read on-screen content, detect overlay attacks in real
time, or scan what AI agents are reading. The costs:

- Google Play restricts the Accessibility API for apps that aren't accessibility tools, and requires disclosure and review.
- Android 17's Advanced Protection Mode blocks accessibility access for non-tool apps. The high-risk users who most need protection would lose the feature.
- Accessibility is the capability banking trojans and stalkerware abuse most. Asking users to grant it to a security app teaches exactly the behavior Aegis warns them against (see [agent guard](../features/ai-threat-defense.md#ai-3-agent-guard)).
- Holding it would make Aegis a far more attractive target.

## Decision

Aegis does not declare or use an AccessibilityService. It uses public, non-privileged APIs for
visibility instead: the enabled-services lists, notification-listener access (opt-in), and
package metadata.

## Consequences

- No real-time screen-content scanning, so prompt-injection scanning is limited to content that passes through Aegis.
- No real-time overlay-attack detection. Aegis detects the *preconditions* instead: an app holding accessibility and overlay access.
- A clean Play review path and a consistent security message.

## Alternatives considered

- **An opt-in accessibility mode:** rejected for 1.0 for the reasons above. Revisit only with a specific, high-value use case and a review of the Play policy.
