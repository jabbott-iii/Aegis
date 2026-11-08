# Feature: Network protection (Aegis Shield)

This spec is a draft, and nothing in it is built yet.

- **Requirements:** FR-NET-01 … FR-NET-10 (`docs/requirements.md`).
- **Decisions:** ADR-0003 (split routing) and ADR-0002 (no TLS
  interception), in `docs/decisions.md`.
- **Roadmap:** Phase 3 (`docs/roadmap.md`).

## Summary

The Shield is a **local, on-device VPN**. It never connects to a remote VPN
server. It exists to filter DNS lookups device-wide against signed
blocklists, and to drop traffic to opt-in blocked prefixes, which the AI
Crawler Shield (`ai-crawler-shield.md`) uses. It also covers the link
checker, the QR checker, and network posture checks.

## What it protects against, and its limits

| Protects | Doesn't protect |
| :- | :- |
| Lookups of known malware, phishing, scam, and stalkerware C2 domains from any app that uses the system resolver | Apps using their own DoH or DoT (for example, a browser's secure DNS), or hardcoded IPs |
| Connections to blocked prefixes (AI crawler ranges) | URL paths and content on HTTPS. Aegis does not decrypt traffic. |
| Per-app visibility into what was blocked | Traffic when another VPN is active (Android allows one VPN at a time) |
| | DNS while Private DNS is in strict mode. This is detected and explained (see "Private DNS"). |

## Design

### VPN interface configuration

```kotlin
// Illustrative, not final API
Builder()
    .setSession("Aegis Shield")
    .addAddress(TUN_V4, 32)              // e.g. 10.111.222.1
    .addAddress(TUN_V6, 128)             // ULA, e.g. fdaa:…::1
    .addDnsServer(VIRTUAL_DNS_V4)        // e.g. 10.111.222.2
    .addDnsServer(VIRTUAL_DNS_V6)
    .addRoute(VIRTUAL_DNS_V4, 32)        // only DNS enters the tunnel…
    .addRoute(VIRTUAL_DNS_V6, 128)
    .apply { enabledBlockedPrefixes.forEach { addRoute(it.address, it.length) } } // …plus opt-in prefixes
    .apply { bypassPackages.forEach { addDisallowedApplication(it) } }
    .setBlocking(true)
    .setMtu(1500)
    .establish()
```

- Aegis's own upstream sockets are excluded with `VpnService.protect()`.
- On API 29+, set `setMetered(false)` so that the system reflects the
  underlying network's metered state. Verify this in S1.
- On API 33+, `excludeRoute` is available if a future design needs it. The
  current design doesn't.

### DNS engine (`shield:dns`)

| Step | Detail |
| :- | :- |
| Parse | A minimal RFC 1035 parser for the header, a single question, and the EDNS0 OPT record. Reject malformed packets, compression loops, and oversized names. **Fuzzed.** |
| Attribute | `getConnectionOwnerUid(IPPROTO_UDP, src, dst)` (API 29+, available to the active VPN app). The result is cached for each 5-tuple for 30 s. The cost is measured in S1. |
| Match | Normalize the name (lowercase, trailing dot, IDNA/punycode form), then walk the parent labels (`a.b.example.com` → `b.example.com` → `example.com`) against category sets. Each set is a sorted array of 64-bit hashes searched by binary search, which takes about 8 MB per million domains. Then apply the per-app AI egress policy (`ai-crawler-shield.md`, "AI service egress control (DNS)"). |
| Block | Answer NXDOMAIN with a 60 s negative TTL (SOA in the authority section). Increment `BlockCounter`. Queue a notification. |
| Forward | Send through the configured upstream: DoH (OkHttp, HTTP/2) or DoT on a protected socket, falling back to the underlying network's resolvers. Answers are relayed unchanged. |
| TCP fallback | If an upstream answer is larger than the client's advertised UDP size, return it truncated (TC=1). The client then retries over TCP to the virtual resolver, so the engine includes a minimal TCP responder for port 53 on the virtual address only. Scope this in S1. |
| Fail-open | If the engine throws or the bundle can't load, forward without filtering, show a persistent "Shield degraded" notification, and record a metric. If the upstream is unreachable, return SERVFAIL. |

### Categories

| Category | Default | Source (see `docs/data-sources.md`) |
| :- | :- | :- |
| Malware | On | Curated feeds, subject to license |
| Phishing | On | Curated feeds, subject to license |
| Scam | On | Curated feeds plus lookalike rules |
| Stalkerware C2 | On | Stalkerware indicators (CC-BY 4.0) |
| AI service (egress control) | Off; governed by AI Crawler Shield | Curated config |

**Lookalike detection** runs on the device, after a lookup is allowed. It
flags IDN homographs and close edit distance to a bundled list of
high-value brands (banks, payment services, AI vendors). The result is
**advisory**: a notification with "Check link", never a block, because
false positives are likely.

### Notifications

There is one channel, *Blocked threats*. Notifications are grouped by app
and rate-limited to one per app per category every 30 minutes. Each one
has the actions *Details* and *Allow domain*. Allowing a domain requires
confirmation and records the decision. For stalkerware C2 blocks, the
notification text is deliberately generic: "Aegis blocked a risky
connection". This avoids alerting anyone looking over the victim's
shoulder (`device-security-audit.md`, "Stalkerware safety flow").

## Link checker, QR checker, and network checks

| Feature | Behavior |
| :- | :- |
| **Link checker** (FR-NET-06) | A share target and paste box. It extracts URLs from text, expands **nothing** over the network by default, and checks the host against the category sets, lookalike rules, and IDN rules. The verdict is Safe-to-known-lists, Risky, or Blocked, and the copy says "known lists" so it doesn't imply a guarantee. Optional opt-in online reputation is P2 and needs a backend (`docs/00-overview.md`, "Open decisions" #4). |
| **QR checker** (FR-NET-07) | CameraX with a bundled barcode model, decoded on the device. URL payloads go to the link checker. Wi-Fi payloads show the security type and SSID. Payment-style payloads (for example, `upi:` or crypto URIs) are flagged so the user verifies the payee. Decoded text also goes through the hidden-text scanner (`ai-threat-defense.md`, "AI-5. Prompt-injection and hidden-text scanner"). |
| **Network checks** (FR-NET-08) | Open, WEP, or otherwise weak Wi-Fi from `WifiInfo.getCurrentSecurityType()` on API 31+, with a "not available" state on 29–30. Captive portal from `NET_CAPABILITY_CAPTIVE_PORTAL`. User-installed CA certificates by enumerating `AndroidCAStore` aliases with the `user:` prefix: "A certificate installed on this device can let someone read encrypted traffic. If you didn't install it, remove it." SSID and BSSID are not read, so no location permission is needed. Verify on API 31+ that the security type isn't redacted when location access is absent. |

## Coexistence and settings interactions

### Another VPN

If the active network has `TRANSPORT_VPN` and Aegis isn't its owner, the
Shield shows *Unavailable: another VPN is active*. Starting the Shield
would disconnect the other VPN. Aegis explains that before calling
`VpnService.prepare()`.

### Always-on VPN and lockdown

Aegis recommends enabling **Always-on VPN** for Aegis so that the Shield
survives reboots and process death. Lockdown (*Block connections without
VPN*) interacts with split routing in ways spike S1 must document. Aegis
will not recommend lockdown until S1 confirms it is safe.

### Private DNS

On API 28+, `LinkProperties.isPrivateDnsActive()` and
`getPrivateDnsServerName()` show whether Private DNS is in strict mode. If
it is, explain: "Private DNS sends lookups directly to *<provider>*, so
Aegis can't filter them. Set Private DNS to *Automatic* to use Aegis's
encrypted DNS instead." Aegis then provides a deep link to network
settings. Prefix blocking still works in this mode.

## Play policy

Device security apps (for example, antivirus and firewall) are a permitted
VpnService use. Each of the following is required:

- the VpnService declaration in Play Console
- a demo video of 90 s or less
- a separate video of the disclosure and consent flow
- documentation of the VPN use in the store listing
- an **in-app prominent disclosure that is separate from other
  disclosures**

The Shield must not manipulate ad or monetization traffic. See
`docs/permissions-and-policy.md` ("VpnService").

The policy also requires apps to "encrypt the data from the device to the
VPN tunnel endpoint". Aegis's tunnel ends on the device, and forwarded DNS
uses an encrypted upstream (FR-NET-10). State both facts in the
declaration (risk R2 in
`docs/00-overview.md`, "Top risks and mitigations").

## Acceptance criteria (selected)

- A test domain in each category resolves to NXDOMAIN with the Shield on,
  and to its real answer with the Shield off or the category disabled.
- With the Shield on for 24 h of mixed use on each matrix device,
  connectivity is never lost, p95 added DNS latency is ≤ 5 ms, and battery
  attributed to the Shield is ≤ 2%.
- Killing the `:shield` process restores the Shield within 10 s when
  Always-on is enabled, and otherwise posts a "Shield stopped"
  notification.
- Injecting an engine fault results in fail-open and a persistent
  notification within 2 s.
- Each of the three coexistence states (other VPN, Private DNS strict,
  Always-on held by another app) shows its explanation.
