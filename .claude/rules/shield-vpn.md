---
paths:
  - "shield/**"
  - "feature/shield/**"
---

# Shield (VpnService) and AI Crawler Shield rules

Specs: `docs/features/network-protection.md`, `docs/features/ai-crawler-shield.md`, ADR-0003.

- **Split routing only.** Route only the virtual DNS resolver addresses and the enabled blocked prefixes into the TUN. Never add `0.0.0.0/0` or `::/0` routes, and never build a userspace TCP/IP stack. The only exception is the minimal port-53 TCP responder on the virtual resolver.
- **Protect upstream sockets** with `VpnService.protect()` before connecting, or traffic loops back into the tunnel.
- **Fail open.** If the filter engine throws or the bundle fails to load, forward DNS unfiltered and post the persistent "Shield degraded" notification (FR-NET-09). An unreachable upstream gets SERVFAIL, never a silent drop.
- **Blocked prefixes:** answer TCP SYN with a synthesized RST and other packets with ICMP/ICMPv6 unreachable (admin-prohibited). Checksums must be correct and unit-tested.
- **Attribution:** call `getConnectionOwnerUid` (API 29+, active VPN app only) with a 30 s cache per 5-tuple. Measure its cost (S1) before calling it on hot paths.
- **Coexistence:** detect another active VPN, Private DNS in strict mode (`LinkProperties.isPrivateDnsActive` / `getPrivateDnsServerName`), and Always-on settings held by another app, and explain each to the user. Never call `VpnService.prepare()` without first telling the user that it disconnects their other VPN.
- **Route cap:** a provisional 2,000 prefixes until S2 sets the real number. Apply categories in the spec's priority order and report *Partial* when some are dropped.
- **Rebuild debouncing:** interface rebuilds happen at most once every 5 min unless the user triggered them.
- **No traffic content.** Never inspect HTTP payloads, SNI, or user agents (ADR-0002). Store aggregated `BlockCounter` rows only (7 days), never query logs.
- **Gated claims.** Strings and UI for crawler blocking must follow the copy constraints in ai-crawler-shield §2. "Inbound" claims stay forbidden until spike S3 passes on the whole device matrix.
- **Testing:** use a debug-only bundle with test prefixes and test domains. Never point tests at real third-party crawler addresses.
