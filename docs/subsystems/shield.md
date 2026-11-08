# Subsystem: `shield:vpn` and `shield:dns` — local VPN and DNS engine

The Shield is a local, on-device VPN. It never connects to a remote VPN
server. `shield:vpn` is the Android side: the `VpnService`, TUN I/O, the
route builder, and coexistence detection. `shield:dns` is the DNS engine:
the wire parser, the domain matcher, the encrypted upstream (DoH or DoT),
and UID attribution. `shield:dns` is one of the six pure Kotlin/JVM modules
and must apply `aegis.jvm.engine`. Both run in the `:shield` process, which
the system binds through `BIND_VPN_SERVICE`; the VPN survives UI crashes
and stays small. Behavior is specified in
`docs/features/network-protection.md` and
`docs/features/ai-crawler-shield.md`. Nothing is built yet. Phase 3 builds
the Shield; Phase 4 adds the AI Crawler Shield's prefix routes and the AI
service egress control. Spike S1 blocks both phases, and S2 blocks
Phase 4.

## Contents

| Module or component | Provides |
| :- | :- |
| `shield:vpn` `AegisVpnService` | Builds the split-routed TUN, runs the packet loop, and handles revocation and coexistence |
| `shield:vpn` `RouteBuilder` | Computes routes for the virtual DNS address and the enabled blocked prefixes, enforcing the route cap |
| `shield:dns` `DnsEngine` | Parses DNS messages, matches them against the category sets and the per-app egress policy, then answers NXDOMAIN or forwards the query upstream |
| `shield:dns` `UidAttributor` | Maps a flow to an app UID with `getConnectionOwnerUid`, using a short-lived cache |

`shield:dns` is pure, but `getConnectionOwnerUid` and
`VpnService.protect()` are Android APIs. Platform calls like these sit
behind an interface whose adapter lives in `shield:vpn`
(`.claude/rules/kotlin-engine.md`).

## Split routing

- **Only two kinds of destination enter the tunnel** (ADR-0003): the
  virtual DNS resolver addresses (IPv4 and IPv6), and each enabled crawler
  prefix. All other traffic bypasses the VPN, so the battery and latency
  cost stays close to zero.
- **Aegis's own upstream sockets** are excluded with
  `VpnService.protect()`.
- **On API 29+**, `setMetered(false)` makes the system reflect the
  underlying network's metered state (verify in S1). `excludeRoute`
  (API 33+) is available, but the current design doesn't need it.
- **The interface configuration sketch** is in
  `docs/features/network-protection.md` ("VPN interface configuration").
- `.claude/rules/shield-vpn.md` applies to `shield/**` and
  `feature/shield/**`.

## Packet paths

The flowchart is in `docs/features/ai-crawler-shield.md` ("Enforcement
through split routing").

- **To the virtual DNS address:** `DnsEngine` (below).
- **To an enabled crawler prefix:** drop with a fast failure. A TCP SYN
  gets a synthesized RST, so apps fail fast instead of waiting for a
  timeout. UDP and other protocols get ICMP or ICMPv6 destination
  unreachable (administratively prohibited). Each drop increments
  `BlockCounter(day, uid, category=ai_crawler, operator)`, with the UID
  from `getConnectionOwnerUid`, sampled and cached to limit the cost.
- **Anything else:** drop, and record an "unexpected route" metric.
- **Overrides never reach this loop.** A per-prefix override removes the
  prefix from the route set at the next rebuild (within 5 s). A per-app
  override adds the app to the VPN's disallowed applications
  (`addDisallowedApplication`), so it bypasses the tunnel completely,
  which also removes it from DNS filtering. The UI says so.

## DNS engine (`shield:dns`)

The full table is in `docs/features/network-protection.md` ("DNS engine
(`shield:dns`)"). The sequence is in `docs/01-architecture.md` ("DNS lookup
through the Shield").

1. **Parse:** a minimal RFC 1035 parser for the header, a single question,
   and the EDNS0 OPT record. Malformed packets, compression loops and
   oversized names are rejected. It's fuzzed.
2. **Attribute:** `getConnectionOwnerUid(IPPROTO_UDP, src, dst)` (API 29+,
   available to the active VPN app), cached per 5-tuple. S1 measures the
   cost.
3. **Match:** normalize the name (lowercase, trailing dot, IDNA/punycode
   form), then walk the parent labels against the category sets. Each set
   is a sorted array of 64-bit hashes searched by binary search. Then apply
   the per-app AI egress policy.
4. **Block:** answer NXDOMAIN with a negative TTL and an SOA in the
   authority section, increment `BlockCounter`, and queue a notification.
5. **Forward:** send through the configured upstream, DoH (OkHttp,
   HTTP/2) or DoT on a protected socket, falling back to the underlying
   network's resolvers. Answers are relayed unchanged.
6. **TCP fallback:** an answer larger than the client's advertised UDP size
   is returned truncated (TC=1), and the client retries over TCP. A minimal
   TCP responder serves port 53 on the virtual address only. S1 scopes it.
7. **Fail open:** if the engine throws or the bundle can't load, forward
   without filtering, show a persistent "Shield degraded" notification, and
   record a metric (`AGENTS.md` rule 9, FR-NET-09). If the upstream is
   unreachable, return SERVFAIL.

**Categories** (malware, phishing, scam, stalkerware C2, and the AI service
category that the AI Crawler Shield governs) and on-device lookalike
detection are in `docs/features/network-protection.md` ("Categories").

## AI Crawler Shield

- **Route set:** `RouteBuilder` applies the compiled prefixes from
  `crawler_ranges.pb` (`intel.md`).
- **Route cap** (FR-ACS-09): spike S2 sets the safe number of routes. Until
  then the provisional cap is 2,000 prefixes. Over the cap, prefixes apply
  in this order: Standard training crawlers, Standard user-triggered
  fetchers, Standard search indexers, then Strict. The rest is reported as
  *Partial: N ranges not applied*.
- **Rebuilds:** rebuilding the VPN interface briefly re-establishes the
  tunnel, so prefix changes from a bundle update are debounced and applied
  at most once every 5 min, unless the user triggered the update.
- **AI service egress control** is a DNS policy,
  `AiEgressPolicy(packageName, certSha256, mode)` with mode allow, block or
  ask (`docs/features/ai-crawler-shield.md`, "AI service egress control
  (DNS)"). An unknown app defaults to ask; verified official AI apps are
  allowed by default. In ask mode the first lookup returns NXDOMAIN and
  posts a prompt.

## Coexistence

The Shield detects and explains each conflict (FR-NET-05,
`docs/features/network-protection.md`, "Coexistence and settings
interactions"):

- **Another VPN** is active: Android allows one VPN at a time, and starting
  the Shield would disconnect it. Aegis explains this before calling
  `VpnService.prepare()`.
- **Always-on VPN and lockdown:** Aegis recommends Always-on VPN for Aegis,
  so the Shield survives reboots and process death (NFR-REL-01). It won't
  recommend lockdown until S1 confirms that it's safe with split routing.
- **Private DNS in strict mode:** Aegis can't filter those lookups and
  explains how to switch to *Automatic*. Prefix blocking still works
  (routes are IP-based); egress control doesn't (it's DNS-based).

## Data

- **`shield.db`** holds `BlockCounter` aggregates only, never full query
  logs, for 7 days (FR-NET-03, `core-data.md`). The main process reads the
  counters for the status panels (FR-ACS-06). Its key is
  `SecretId.SHIELD_DATABASE`, which `core:security` already defines.
- **Settings** come from the multi-process DataStore that Phase 0 built
  (`core-data.md`, "Settings store").
- **Intel parts** are memory-mapped read-only and reloaded when
  `intel:client` says so (`intel.md`).

## Limits

| Limit | Value | Source |
| :- | :- | :- |
| DNS name | At most 255 bytes | `.claude/rules/untrusted-input-parsers.md` |
| DNS label | At most 63 bytes | `.claude/rules/untrusted-input-parsers.md` |
| Compression pointers | A hop cap, with loop detection | `.claude/rules/untrusted-input-parsers.md` |
| UID cache | 30 s per 5-tuple | `docs/features/network-protection.md` ("DNS engine (`shield:dns`)") |
| Negative TTL of a block | 60 s | `docs/features/network-protection.md` ("DNS engine (`shield:dns`)") |
| Category set memory | About 8 MB per million domains | `docs/features/network-protection.md` ("DNS engine (`shield:dns`)") |
| MTU | 1500 in the spec's illustrative sketch; not final | `docs/features/network-protection.md` ("VPN interface configuration") |
| Route cap | Set by S2; provisionally 2,000 prefixes | `docs/features/ai-crawler-shield.md` ("Route cap") |
| Prefix override | Applied within 5 s | `docs/features/ai-crawler-shield.md` ("Enforcement through split routing") |
| Bundle-driven rebuild | At most once every 5 min unless the user triggered it | `docs/features/ai-crawler-shield.md` ("Edge cases") |
| Block notifications | One per app per category every 30 minutes | `docs/features/network-protection.md` ("Notifications") |
| Egress *ask* prompts | One per app per hour | `docs/features/ai-crawler-shield.md` ("AI service egress control (DNS)") |
| Added DNS latency | At most 5 ms p95 | NFR-PERF-02, S1 |
| Steady-state memory | At most 60 MB | NFR-PERF-04 |
| Battery | At most 2% per day | NFR-BAT-01 |

## Spikes

- **S1** (blocks Phases 3 and 4): whether a split-routed `VpnService`
  intercepts DNS reliably, including the TCP fallback; the latency and
  battery cost; which foreground-service type applies; the behavior with
  Private DNS and with Always-on or *Block connections without VPN*; and
  the cost of per-packet `getConnectionOwnerUid`. Pass: p95 added DNS
  latency of 5 ms or less, no connectivity loss across 24 h on 3 OEMs, and
  a documented behavior matrix.
- **S2** (blocks Phase 4): how many routes the VPN can install (1k, 5k and
  10k prefixes) on low-end and OEM devices, with the apply time and memory
  cost. Pass: a safe route cap, chosen and documented.
- **S3** (blocks Phase 4's UI copy): whether replies from on-device
  listening sockets to a routed crawler prefix are captured. The working
  assumption is that inbound blocking is not provided. "Inbound" claims
  stay forbidden until S3 passes (`AGENTS.md` rule 10, FR-ACS-08;
  `docs/features/ai-crawler-shield.md`, "Inbound traffic: spike S3").

## Rules

- **No TLS interception:** no CA installation, no decryption, and no HTTP
  payload or user-agent inspection (`AGENTS.md` rule 1, ADR-0002).
- **Forwarded DNS is one of only two kinds of traffic** that leave the
  device, the other being signed bundle downloads (`AGENTS.md` rule 7,
  NFR-PRIV-01). The default upstream is open decision #6.
- **Fail open with a visible alert,** never a silent loss of connectivity
  (`AGENTS.md` rule 9).
- **Play's VpnService policy:** `docs/permissions-and-policy.md`
  ("VpnService"). The Shield must not manipulate ad or monetization
  traffic.
- `checkSourceRules` fails on `!!` under `shield/`.

## Tests

- **The Shield test plan** (`docs/04-quality.md`, "Feature test plans"): a
  24-hour soak per matrix device, category NXDOMAIN tests, the TCP DNS
  fallback, fail-open fault injection, the coexistence states, and
  process-death recovery.
- **AI Crawler Shield** (`docs/features/ai-crawler-shield.md`, "Testing"):
  unit tests for aggregation, breadth and canary validation, route-cap
  ordering, RST and ICMP synthesis (checksums), and DNS policy decisions;
  an instrumented fixture app that connects to an address inside a
  test-only prefix injected through a debug bundle, and asserts fast
  failure; override behavior; and reboot with Always-on.
- **Fuzzing:** the DNS parser (Jazzer).
- **Acceptance** (`docs/features/network-protection.md`, "Acceptance
  criteria (selected)"): among others, killing `:shield` restores the Shield
  within 10 s when Always-on is enabled and otherwise posts a "Shield
  stopped" notification, and an injected engine fault fails open with a
  persistent notification within 2 s.
- `shield:dns` is held to the ≥ 80% coverage gate by `aegis.jvm.engine`.

## Open questions

- **The default DNS upstream** (`docs/00-overview.md`, "Open decisions"
  #6): the network resolver, a security-focused DoH resolver, or a user
  choice at onboarding. Whichever is chosen must be disclosed.
- **Lockdown, Private DNS and the foreground-service type** wait on S1.
- **The route cap** waits on S2.
