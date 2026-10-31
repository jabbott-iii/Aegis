# ADR-0002: No TLS interception

- Status: Proposed
- Date: 2026-10-31
- Related: FR-NET-01, FR-ACS-*, [THREAT_MODEL](../THREAT_MODEL.md)

## Context

Inspecting full URLs, HTTP user agents (to identify AI crawlers, for example), or page content
over HTTPS would require Aegis to man-in-the-middle TLS. That means installing a user CA
certificate and terminating connections. Apps targeting API 24+ don't trust user CAs by default,
so this would break many apps anyway. It would also make Aegis a high-value attack target,
weaken the user's security posture (the [network checks](../features/network-protection.md#4-link-checker-qr-checker-and-network-checks)
warn users about exactly this setup), and conflict with the "don't weaken encryption"
principle.

## Decision

Aegis **never** installs a CA certificate, never terminates or decrypts TLS, and never inspects
HTTP payloads. Network protection works only on **DNS names** and **IP prefixes**.

## Consequences

- No URL-path blocking on HTTPS. Phishing protection works at the domain level, plus the link checker for shared URLs.
- No user-agent-based crawler detection. The AI Crawler Shield relies on the operators' published IP ranges.
- Aegis can honestly say "Aegis never reads your encrypted traffic".

## Alternatives considered

- **Opt-in TLS MITM:** rejected. It is a security regression, and supporting it is a substantial burden.
- **SNI inspection through a full-tunnel VPN:** gives hostnames for TLS connections that bypass DNS (DoH apps), but needs a userspace TCP/IP stack and a full tunnel, with a large battery cost. Encrypted Client Hello removes the signal anyway. Revisit only if DoH bypass becomes the dominant gap.
