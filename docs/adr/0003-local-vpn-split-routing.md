# ADR-0003: Local VPN with split routing for network protection

- Status: Proposed (validate with spikes S1, S2, S3)
- Date: 2026-10-31
- Related: FR-NET-*, FR-ACS-*, [network protection spec](../features/network-protection.md), [AI Crawler Shield spec](../features/ai-crawler-shield.md)

## Context

A non-root app's only device-wide network hook is `VpnService`. There are two common designs:

1. **Full tunnel:** route all traffic into the TUN and forward it through a userspace TCP/IP stack. This gives full visibility of IP and port flows, but carries a large battery, latency, and complexity cost, along with a long bug tail (MTU, IPv6, QUIC, and so on).
2. **Split routing:** route only selected destinations into the TUN. The DNS-only variant is a well-established technique for DNS-filtering apps.

Aegis needs (a) DNS filtering and (b) the ability to drop traffic to specific prefixes (AI crawler ranges). Neither requires seeing other traffic.

## Decision

Use **split routing**. Only these destinations enter the TUN:

- the virtual DNS resolver addresses (IPv4 and IPv6), and
- the enabled blocked prefixes. Packets to these are answered with a synthesized TCP RST or ICMP unreachable, and dropped.

All other traffic bypasses the VPN. Per-app bypass uses `addDisallowedApplication`. Forwarded DNS goes upstream over DoH or DoT on protected sockets.

## Consequences

- **Easier:** near-zero overhead for regular traffic, a small and auditable packet-handling surface (DNS parser plus RST and ICMP synthesis), and no userspace TCP stack.
- **Harder:**
  - Aegis needs a minimal TCP responder for DNS-over-TCP fallback on the virtual resolver.
  - Route count limits must be measured (S2).
  - Behavior with lockdown mode and Private DNS must be characterized (S1).
- **Not achievable:** per-app IP blocking other than by bypassing the VPN entirely, visibility of non-DNS flows, and, unless S3 shows otherwise, inbound filtering.
- **Shared constraint:** Android allows one active VPN, so Aegis can't run alongside a commercial VPN (risk R4).

## Alternatives considered

- **Full tunnel** (for example, built on an existing tun2socks-style stack): more capability, but the cost and risk outweigh the benefit for Aegis's needs. Revisit if per-app IP firewalling becomes a requirement.
- **Asking users to set Private DNS** to a filtering resolver: no VPN needed, but it can't be configured programmatically, has no per-app attribution, and can't block IP prefixes.
- **A remote VPN server:** contradicts [ADR-0001](0001-offline-first-on-device-verdicts.md) and introduces a trusted third party.
