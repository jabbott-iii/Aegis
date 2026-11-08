# 00 — Overview

## Purpose

Aegis is an Android security app written in Kotlin. It gives standard
mobile antivirus protection and adds defenses against AI-era threats,
including an optional **AI Crawler Shield** that blocks known AI crawler
and agent networks. It covers five areas:

1. **Malware protection:** scans apps when they are installed or updated,
   on demand, and on a schedule. Detection uses signature and IOC matching
   (hash, package, signing certificate), manifest and DEX heuristics, and
   an on-device ML classifier. It covers stalkerware detection with a
   safety-first removal flow, and scans APK files in Downloads.
2. **Device security audit:** checks screen lock, patch age, developer
   options and USB debugging, user-installed CA certificates, risky
   permission grants, and apps holding accessibility, notification-listener
   or device-admin access.
3. **Network protection ("Aegis Shield"):** a local, on-device VPN that
   blocks malware, phishing, scam and stalkerware command-and-control
   domains at the DNS level. It attributes blocks to individual apps and
   offers a link checker and a QR checker. Traffic is never decrypted.
4. **AI threat defense:** flags fake AI-assistant apps, audits which apps
   have AI capabilities alongside screen or notification access, and alerts
   when an app gains screen-reading access. It also detects scam messages,
   scans for prompt injection and hidden text, checks media provenance
   (C2PA), and offers voice-clone scam safeguards.
5. **AI Crawler Shield (a toggle, off by default):** blocks traffic to and
   from the published network ranges of AI crawlers, AI search indexers and
   user-triggered AI fetchers. It can also control which apps may send data
   to AI services, and it explains how exposed the device actually is.
   `docs/features/ai-crawler-shield.md` says exactly what it can and cannot
   do.

Aegis is a non-root, sandboxed app. It cannot hook the file system or other
processes, intercept installs before they happen, read call audio, or
decrypt TLS. Its "real-time" protection is therefore made up of install and
update scanning, DNS and route filtering, and notification analysis, and the
UI describes it in exactly those terms (`docs/requirements.md`, "Users and
context").

Five design principles shape it: on-device verdicts (ADR-0001), no TLS
interception (ADR-0002), honest claims, least privilege, and deterministic
verdicts (ADR-0005). `docs/01-architecture.md` ("Principles") explains
each.

**Status:** Phase 0 is in progress. The build, the foundation modules and
an app shell exist. The shell offers no protection yet. `docs/roadmap.md`
takes Aegis from an empty repository to a 1.0 release on Google Play, and
tracks what is done. The goals, risks and estimates here are a draft for
owner review (2026-11-01).

## Goals

Goals for 1.0:

1. **Detect malicious, stalkerware and potentially unwanted apps (PUA)**
   on install, on update, on demand, and on a schedule, with a low
   false-positive rate.
2. **Block known malicious domains device-wide** with an on-device DNS
   filter, without decrypting traffic (ADR-0002, ADR-0003).
3. **Defend against the AI-era threats that a non-root Android app can
   realistically address:** fake AI apps, AI-written scams, prompt
   injection, over-privileged AI agents, unverified synthetic media, and AI
   data egress.
4. **Provide an AI Crawler Shield toggle** that blocks known AI crawler and
   agent networks and describes its coverage honestly (FR-ACS-08).
5. **Pass Google Play review on the first submission,** with every
   sensitive permission declared and justified.

## Non-goals

None of these is in 1.0. `docs/requirements.md` ("Out of scope for 1.0")
lists most of them as requirements.

- Root-only features, such as an inbound firewall or `iptables` rules.
- TLS interception or content inspection of HTTPS (ADR-0002).
- Anti-theft, remote wipe, and app lock.
- A VPN to a remote server.
- Call recording or call-audio analysis (blocked by the platform).
- SMS permissions. Play's anti-smishing exception requires an established
  protection track record.
- An AccessibilityService (ADR-0004).
- iOS, Wear OS, TV, or enterprise MDM integration.
- A cloud backend that stores user data. Static hosting for signed intel
  bundles is in scope (open decision #4).
- Cloud upload of samples.

Root-only features, anti-theft, remote wipe and app lock, a remote VPN,
call recording, other platforms, and a user-data backend are revisited
after launch. SMS permissions, cloud upload of samples, and call-audio
analysis are out of scope (`docs/requirements.md`). An AccessibilityService
comes back only with a specific, high-value use case and a review of the
Play policy (ADR-0004), through a new or revised ADR that the owner
approves (`AGENTS.md` rule 2). The draft plan of 2026-11-01 also listed
TLS interception for revisiting after launch, but it is a non-negotiable
(`AGENTS.md` rule 1, ADR-0002). The owner decides which holds.

## Performance budgets

These are the numbers we test against. They are **targets, not
measurements**: nothing has been measured yet, and spikes S1, S4 and S5,
which measure some of them first, have not run. Budgets are measured on the
reference mid-range device in `docs/04-quality.md` ("Device matrix"). The
matrix names a class, not a model: a recent mid-range Pixel or Samsung
A-series on Android 16 or 17. Phase 7 tunes performance and battery against
these budgets, and the definition of done checks them on the reference
device (`AGENTS.md`, "Definition of done for a roadmap task").

| Budget | Target |
| :- | :- |
| Full scan | 150 installed apps in ≤ 2 min, with warm caches after the first scan (NFR-PERF-01) |
| New or updated app | A verdict within 10 s of Aegis detecting the change: p95, 100 MB APK, mid-range device (FR-SCAN-02) |
| APK scan throughput | p95 scan of a 100 MB APK ≤ 10 s on a mid-range device, peak memory ≤ 150 MB (S4 pass criterion) |
| DNS latency | The Shield adds ≤ 5 ms p95 to DNS resolution (NFR-PERF-02; also S1's pass criterion) |
| Cold start | ≤ 1.0 s to an interactive dashboard, with a baseline profile (NFR-PERF-03) |
| Scan memory | The scan worker's peak memory is ≤ 150 MB (NFR-PERF-04) |
| Shield memory | The Shield's steady-state memory is ≤ 60 MB (NFR-PERF-04) |
| Shield battery | Battery attributable to the Shield is ≤ 2% per day (NFR-BAT-01). The M2 Shield beta exit criterion requires the same on the device matrix |
| Scheduled scan battery | ≤ 1% per run (NFR-BAT-01) |
| Download size | ≤ 30 MB per ABI split, including the models (NFR-SIZE-01) |
| APK classifier model | ≤ 5 MB and ≤ 50 ms per app (S5 pass criterion) |
| Scam-text model | ≤ 10 MB and ≤ 30 ms per message (S5 pass criterion) |

## Assumptions

Revisit these if any turns out to be wrong.

- **Effort:** one developer working roughly full time, about 33 weeks in
  total. The phase estimates in "Scope estimate" are a planning assumption,
  not a commitment.
- **Hosting:** static hosting only, for the signed intel bundles. Play
  Integrity verdicts, Web Risk lookups and cloud reputation all need a
  minimal server, so they are P2 features (open decision #4).
- **Network design:** the Shield's local VPN with split routing (ADR-0003)
  is still to be validated by spikes S1, S2 and S3.
- **Decisions are proposals:** every design decision in these docs stays a
  proposal until its ADR in `docs/decisions.md` is `Accepted`. All seven
  ADRs are `Proposed`. The owner accepts, revises or rejects each after the
  Phase 0 spikes.

## Scope estimate

Efforts are rough estimates in weeks, for planning, not commitments. They
assume **one developer working roughly full time** (about 33 weeks in
total). Scale them for part-time work, and re-estimate after the Phase 0
spikes. Phases 5 and 6 can run in parallel if a second contributor joins.

| Phase | Focus | Rough effort | Exit milestone |
| :- | :- | :- | :- |
| 0 | Project scaffold, CI, core security plumbing, technical spikes S1–S7 | 3 wks | M0 Foundations ready |
| 1 | App inventory, IOC matching, metadata heuristics, scan UX, device audit | 5 wks | M1 (with P2) |
| 2 | Intel pipeline (CI), bundle format, signing, update client | 3 wks | M1 Scanner alpha |
| 3 | Local VPN, DNS filter, per-app attribution, link/QR checker, network checks | 5 wks | M2 (with P4) |
| 4 | AI Crawler Shield: range ingestion, routing, AI egress control, exposure check | 2.5 wks | M2 Shield beta |
| 5 | AI threat defense features | 6 wks | M3 AI defense beta |
| 6 | DEX static analysis, rules engine, ML classifier, false-positive (FP) corpus | 5 wks | Feeds M4 |
| 7 | MASVS review, performance and battery work, Play declarations, closed testing | 4 wks | M4 1.0 |

`docs/roadmap.md` shows how the phases and milestones depend on each
other, and breaks each phase into tasks.

## Top risks and mitigations

Other docs cite these risks by ID (R1–R12).

| ID | Risk | Likelihood | Impact | Mitigation |
| :- | :- | :- | :- | :- |
| R1 | Play rejects the VpnService, `QUERY_ALL_PACKAGES`, or All files access declarations | Med | High | Stay within the listed permitted uses (antivirus or device security). Provide separate prominent disclosures and demo videos. Ship working fallbacks so that a denied permission degrades the feature instead of breaking the app. |
| R2 | Play's VpnService policy requires encrypting data "to the VPN tunnel endpoint", and a reviewer may apply this to a local VPN | Med | Med | Aegis forwards DNS over an encrypted upstream (DoH/DoT). Document in the declaration that the tunnel terminates on the device. |
| R3 | False positives damage user trust | Med | High | IOC and strong heuristics are required for a *Malicious* verdict. ML on its own can yield at most *Suspicious*. Gate releases on the FP corpus, and make it easy for users to report FPs. |
| R4 | Only one VPN can run at a time, so users with a commercial VPN lose the Shield | High | Med | Detect the conflict and explain it. Scanning and the AI features work without the VPN. |
| R5 | OEM battery managers kill services | High | Med | Rely on the system-bound VPN service and WorkManager. Give per-OEM guidance. Do not request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play restricts it). |
| R6 | Data-source licensing blocks commercial use (abuse.ch commercial terms, Safe Browsing non-commercial terms) | Med | Med | Track terms per source in `docs/data-sources.md`. Decide commercial intent before M2 (open decision #2). Use Web Risk or a paid feed if monetizing. |
| R7 | Malware evades detection (obfuscation, LLM-mutated variants) | High | Med | Layer IOC, heuristic, and ML detection. Use features that are costly for an attacker to remove. Red-team the classifier with mutated fixtures in the lab (`docs/04-quality.md`, "Adversarial evaluation"). |
| R8 | Crawler IP lists contain broad shared-cloud ranges and cause collateral blocking | Med | Med | Prefix-breadth limits, canary allowlists, and churn gates in the pipeline. Per-prefix provenance. Users can see and override. |
| R9 | The AI Crawler Shield is perceived as promising more than Android allows | Med | Med | UI copy constraints in `docs/features/ai-crawler-shield.md`, the exposure check, and S3-gated claims (FR-ACS-08). |
| R10 | Stalkerware removal escalates risk to a victim | Low | High | Safety flow that warns before removal and links to support (`docs/features/device-security-audit.md`, "Stalkerware safety flow"). Never auto-remove. |
| R11 | Google's developer-verification requirement affects sideloaded distribution: in effect from Sept 30 2026 in Brazil, Indonesia, Singapore, and Thailand, and expanding globally in 2027 | High (if sideloading) | Med | Register as a verified developer before any non-Play distribution. |
| R12 | Scope creep from the breadth of the AI threat surface | High | Med | Strict P0/P1 priorities in `docs/requirements.md`. Any new AI feature needs a spec with an explicit statement of its limits. |

## Open decisions

The owner needs to decide these. Each one changes scope or architecture.
Other docs cite them by number (`docs/00-overview.md`, "Open decisions"
#9). #5 was decided on 2026-11-01; the rest are open.

1. **AI Crawler Shield scope.** The spec reads "block AI crawlers from
   reaching the device" as three things: (a) block traffic to and from
   published AI crawler and agent ranges, (b) optionally control app data
   flows to AI services, and (c) an exposure check. Confirm this reading or
   adjust it (`docs/features/ai-crawler-shield.md`, "Problem framing").
2. **Commercial or non-commercial.** This determines whether Google Safe
   Browsing (non-commercial only) or Web Risk is usable, and whether
   abuse.ch data requires a paid plan.
3. **Distribution.** Choose between Play only and Play plus GitHub Releases
   or F-Droid. F-Droid would require a `foss` flavor without Play Integrity
   or ML Kit.
4. **Backend.** The roadmap and architecture assume static hosting only.
   Play Integrity verdicts, Web Risk lookups, and cloud reputation all
   need a minimal server, so they are P2 features.
5. **minSdk 29 vs. 31.** **Decided on 2026-11-01:** the owner chose minSdk
   29 (ADR-0007, which is still `Proposed`). API 29 is the minimum for
   per-app VPN attribution (`getConnectionOwnerUid`). Moving to 31 removes
   Wi-Fi security-type and other compatibility branches but drops Android
   10 and 11 devices.
6. **Default DNS upstream.** The options are the network resolver, a
   security-focused DoH resolver (for example, Quad9), or a user choice at
   onboarding. Whichever is chosen must be disclosed.
7. **Project license.** This affects contributions and how third-party data
   is redistributed. No license is chosen yet, and there is no `LICENSE`
   file.
8. **NFR-MAINT-01 outside the pure modules.** `aegis.jvm.engine` gives the
   six pure modules explicit API mode and the ≥ 80% Kover gate.
   `engine:scanner`, `engine:ml`, `intel:client`, and a non-pure `ai:guard`
   would get neither. The options are an engine convention plugin for those
   modules, or narrowing NFR-MAINT-01 to the pure modules. Decide before the
   first of them is created.
9. **An unreadable `app.db`.** If the Keystore loses the database key, or
   the file is corrupt, `core:data` reports the database as unreadable and
   never deletes it (`docs/01-architecture.md`, "Data model"). On Android 10
   and 11 a failing Keystore can look like a lost key, so a single
   `KEY_LOST` mustn't trigger a reset: confirm it on a later launch first.
   The data can't be recovered either way. The options are to tell the user
   and reset on their confirmation, or to reset automatically and then tell
   them. Findings and scans can be rebuilt by rescanning, but trust
   decisions and quarantined files can't. Decide before Phase 1 stores
   anything in `app.db`.
