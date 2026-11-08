# Requirements

What Aegis must do, as functional (FR-*) and non-functional (NFR-*)
requirements, each with an ID and a priority. Each functional area below
that has a spec names it, and the spec defines the area's acceptance
criteria in detail.

- **Priorities:**
  - **P0:** required for 1.0.
  - **P1:** should ship in 1.0.
  - **P2:** post-1.0.
  - **P3:** exploratory.

Status today: These requirements are a draft. Phase 0's build and
foundation modules exist. Spikes S1–S7 and everything from Phase 1 on have
not started (`docs/roadmap.md`).

## Users and context

| Persona | Needs |
| :- | :- |
| **Everyday user** | One clear status ("You're protected"), a small number of alerts that are worth acting on, and fixes that take one tap. |
| **Privacy-conscious user** | Control over which apps reach AI services, a block on AI crawlers, and evidence for every claim the app makes. |
| **At-risk user** (for example, someone targeted by stalkerware) | Detection that does not alert the abuser, and a safe, guided path forward. |
| **Technical user** | Raw evidence: hashes, certificate digests, the matched rule, the domains that were blocked. Also wants list provenance and overrides. |

**Platform constraints that shape every requirement:** Aegis is a non-root,
sandboxed app. It cannot hook the file system or other processes,
intercept installs before they happen, read call audio, or decrypt TLS.
Its "real-time" protection is therefore made up of install and update
scanning, DNS and route filtering, and notification analysis, and the UI
must describe it in exactly those terms.

## Functional requirements

### App and file scanning

Spec: `docs/features/malware-detection.md`.

| ID | Requirement | Pri |
| :- | :- | :- |
| FR-SCAN-01 | Keep an inventory of installed packages: version, signing-certificate SHA-256, installer source, requested and granted permissions, and privileged components. | P0 |
| FR-SCAN-02 | Scan new and updated apps automatically. A verdict is shown within 10 s of Aegis detecting the change (p95, 100 MB APK, mid-range device). | P0 |
| FR-SCAN-03 | On-demand full scan with progress, cancellation, and resume. | P0 |
| FR-SCAN-04 | Scheduled scan, daily by default, with constraints for battery-not-low and idle. | P0 |
| FR-SCAN-05 | Scan APK, APKS, XAPK, and ZIP files in shared storage, including Downloads, when the user grants All files access. Re-scan when new files arrive. | P1 |
| FR-SCAN-06 | Scan a single file chosen through the system picker or shared to Aegis, without needing storage permission. | P0 |
| FR-SCAN-07 | Every finding has a verdict (*Malicious*, *Stalkerware*, *PUA*, *Suspicious*), a severity, the detector or rule that fired, the evidence, and a plain-language explanation. | P0 |
| FR-SCAN-08 | Remediation actions: uninstall prompt, open app settings, guide to deactivate a device admin, or quarantine a file into app-private encrypted storage. | P0 |
| FR-SCAN-09 | *Trust this app* exceptions are pinned to package and signing certificate. A certificate change re-opens the finding. | P0 |
| FR-SCAN-10 | Scan history (90-day retention), with export as a local file that the user initiates. | P1 |

### Detection engine

Spec: `docs/features/malware-detection.md` ("Detection pipeline").

| ID | Requirement | Pri |
| :- | :- | :- |
| FR-DET-01 | IOC matching on APK hash, package name plus certificate, and certificate alone. | P0 |
| FR-DET-02 | Metadata heuristics: permission combinations, privileged components, installer source, debuggable flag, targetSdk. | P0 |
| FR-DET-03 | DEX and native static analysis in an isolated process: API references, dynamic code loading, embedded payloads, packers. | P1 |
| FR-DET-04 | An on-device ML classifier that can raise a finding to at most *Suspicious* unless another detector corroborates it. | P1 |
| FR-DET-05 | Stalkerware detection using the public stalkerware IOC set, with the safety flow. | P0 |
| FR-DET-06 | PUA and adware categories, which the user can turn off. | P1 |
| FR-DET-07 | Heuristic rules and ML models are delivered through signed intel bundles and can be updated without an app release. | P1 |

### Threat intelligence

Decision: ADR-0006. Sources: `docs/data-sources.md`.

| ID | Requirement | Pri |
| :- | :- | :- |
| FR-INTEL-01 | Bundles are signed with Ed25519, and the app rejects any bundle that fails signature or hash verification. | P0 |
| FR-INTEL-02 | Rollback protection (monotonic version) and expiry. An expired bundle stays in use and the UI warns about it. | P0 |
| FR-INTEL-03 | Background update check once a day, preferring unmetered networks, plus a manual "Update now". | P0 |
| FR-INTEL-04 | A baseline bundle ships in the APK, so that protection works offline from first launch. | P0 |
| FR-INTEL-05 | An *About › Data sources* screen with the attributions that source licenses require (for example, CC-BY). | P0 |

### Network protection: Aegis Shield

Spec: `docs/features/network-protection.md`.

| ID | Requirement | Pri |
| :- | :- | :- |
| FR-NET-01 | A local `VpnService` that filters DNS for every app, using split routing (ADR-0003). | P0 |
| FR-NET-02 | Blocklist categories (malware, phishing, scam, stalkerware C2) that can each be switched on or off. | P0 |
| FR-NET-03 | Per-app attribution of blocked lookups, with a 7-day aggregated log. | P1 |
| FR-NET-04 | Block notifications, rate-limited and grouped per app. | P0 |
| FR-NET-05 | Detect and explain conflicts: another VPN, Private DNS in strict mode, Always-on VPN or lockdown held by another app. | P0 |
| FR-NET-06 | A link checker reachable through the share sheet and through paste. | P0 |
| FR-NET-07 | A QR code checker that decodes on the device and checks URL, Wi-Fi, and payment payloads. | P1 |
| FR-NET-08 | Network checks: open or weak Wi-Fi security (API 31+), captive portal, user-installed CA certificates. | P1 |
| FR-NET-09 | Fail open, with a persistent alert, if the filter engine fails, so connectivity never breaks silently. | P0 |
| FR-NET-10 | Forwarded DNS uses an encrypted upstream (DoH or DoT), with the network resolver as a selectable alternative. | P0 |

### AI Crawler Shield

Spec: `docs/features/ai-crawler-shield.md`.

| ID | Requirement | Pri |
| :- | :- | :- |
| FR-ACS-01 | A single master toggle, **off by default**. Turning it on starts the Shield VPN after consent if it is not already running. | P0 |
| FR-ACS-02 | *Standard* level: drop traffic to and from the published IP ranges of AI training crawlers, AI search indexers, and user-triggered AI fetchers. | P0 |
| FR-ACS-03 | *Strict* level: also cover dual-use crawlers whose data feeds AI features (for example, Googlebot, Applebot, OAI-AdsBot), with a warning about collateral impact. | P1 |
| FR-ACS-04 | *AI service egress control*: per-app allow, block, or ask for DNS lookups of AI inference and API endpoints. Verified official AI apps are allowed by default. | P1 |
| FR-ACS-05 | Exposure check that reports the device's realistic reachability (global IPv6, open Wi-Fi, wireless debugging where readable). | P1 |
| FR-ACS-06 | Status panel: list version, number of prefixes active, blocked-connection counters per operator and per app. | P0 |
| FR-ACS-07 | Provenance view: the source URL, fetch date, and operator for each blocked prefix. Users can override a prefix. | P1 |
| FR-ACS-08 | UI copy states only the coverage verified by spike S3. "Inbound protection" claims are forbidden unless S3 passes. | P0 |
| FR-ACS-09 | Enforce the route cap from spike S2. Above the cap, Strict categories are dropped first and the user is told. | P0 |

### AI threat defense

Spec: `docs/features/ai-threat-defense.md`.

| ID | Requirement | Pri |
| :- | :- | :- |
| FR-AI-01 | AI app impersonation: flag apps whose label or icon resembles a registered AI assistant but whose package or certificate does not match the official app. | P0 |
| FR-AI-02 | AI access audit: list apps that embed AI SDKs or runtimes, together with their sensitive capabilities, and compute an explained exposure score. | P1 |
| FR-AI-03 | Agent guard: alert when an app newly gains an enabled accessibility service or notification-listener access. | P0 |
| FR-AI-04 | Scam and smishing detection on message notifications. This is opt-in, uses notification-listener access, and runs entirely on the device. | P1 |
| FR-AI-05 | Prompt-injection and hidden-text scanner for shared text, links, and QR payloads: Unicode tag characters, zero-width and bidi controls, and injection phrasing. | P1 |
| FR-AI-06 | Synthetic-media provenance check: C2PA manifest validation and IPTC digital-source-type metadata, with copy that explains its limits. | P2 |
| FR-AI-07 | Voice-clone scam safeguards: a family safe-word helper stored encrypted and a caller-verification checklist. | P1 |
| FR-AI-08 | AI egress monitor: a per-app summary of AI-service lookups. Shares its data with FR-ACS-04. | P1 |
| FR-AI-09 | Aegis's own sensitive screens use `FLAG_SECURE` and, on API 34+, `setAccessibilityDataSensitive`. | P0 |
| FR-AI-10 | Call screening for number reputation. Deferred until a data source with suitable terms exists. | P3 |

### Device security audit

Spec: `docs/features/device-security-audit.md`.

| ID | Requirement | Pri |
| :- | :- | :- |
| FR-AUD-01 | Posture checks: secure lock screen, security-patch age, OS support status, developer options, USB debugging, storage encryption, Advanced Protection status (where the API is available). | P0 |
| FR-AUD-02 | Privacy audit: apps with granted sensitive permissions, enabled accessibility services, notification listeners, device admins, and install-unknown-apps capability. | P0 |
| FR-AUD-03 | Each failed check has a recommendation and a deep link to the relevant system setting. | P0 |
| FR-AUD-04 | Play Integrity device and Play Protect verdicts. Needs a backend, so it is gated by open decision #4 (`docs/00-overview.md`, "Open decisions"). | P2 |

### App experience

| ID | Requirement | Pri |
| :- | :- | :- |
| FR-UX-01 | Onboarding that requests nothing up front. Each permission is requested in context, behind its own prominent disclosure. | P0 |
| FR-UX-02 | A dashboard with one overall status and a per-module status (Scanner, Shield, AI Crawler Shield, AI defense, Audit). | P0 |
| FR-UX-03 | A notification policy: one channel per category, deduplication, and no more than 3 non-critical alerts per day by default. | P0 |
| FR-UX-04 | Light and dark themes, dynamic color, and support for 200% font scaling. | P0 |
| FR-UX-05 | In-app "Report a false positive" that produces a local report the user can choose to send (by email or share sheet). Nothing is uploaded automatically. | P1 |

## Non-functional requirements

Budgets are targets measured on the reference mid-range device defined in
`docs/04-quality.md` ("Device matrix").

| ID | Requirement |
| :- | :- |
| NFR-PERF-01 | Full scan of 150 installed apps takes ≤ 2 min, with warm caches after the first scan. |
| NFR-PERF-02 | The Shield adds ≤ 5 ms p95 to DNS resolution. |
| NFR-PERF-03 | Cold start to an interactive dashboard takes ≤ 1.0 s (with a baseline profile). |
| NFR-PERF-04 | The scan worker's peak memory is ≤ 150 MB. The Shield's steady-state memory is ≤ 60 MB. |
| NFR-BAT-01 | Battery attributable to the Shield is ≤ 2% per day. Scheduled scans are ≤ 1% per run. |
| NFR-SIZE-01 | Download size is ≤ 30 MB per ABI split, including the models. |
| NFR-PRIV-01 | By default, the only traffic Aegis originates is intel downloads and the DNS upstream. Neither carries device or user identifiers. |
| NFR-PRIV-02 | No third-party analytics, advertising, or attribution SDKs. Crash reporting, if added, is opt-in. |
| NFR-PRIV-03 | Notification content analyzed for scams is processed in memory and never persisted. Only verdict metadata is stored. |
| NFR-PRIV-04 | All app data is excluded from cloud backup and device transfer. |
| NFR-SEC-01 | The intel signature is verified before any parsing of bundle contents. |
| NFR-SEC-02 | Untrusted inputs (APKs, archives, DNS packets, QR payloads, media) are parsed with size and time limits. APK, DEX, and media parsing runs in an `isolatedProcess` service. |
| NFR-SEC-03 | No exported component without a permission or explicit input validation. No `WebView` loads remote content. |
| NFR-SEC-04 | Cleartext traffic is disabled (Network Security Config). Only system CAs are trusted, and user CAs are not trusted for Aegis's own connections. |
| NFR-SEC-05 | Release builds are R8-minified, `debuggable=false`, and signed with Play App Signing. Upload keys are kept outside the repo. |
| NFR-SEC-06 | Targets: OWASP MASVS (all groups including MASVS-PRIVACY) and the MASVS-RESILIENCE controls relevant to anti-tamper. |
| NFR-SEC-07 | Dependencies are pinned with Gradle dependency verification. A license allowlist is enforced in CI. |
| NFR-REL-01 | The Shield recovers after a process death and after reboot (via Always-on VPN when the user enables it). |
| NFR-REL-02 | An intel update failure never removes existing protection (last-good bundle is retained). |
| NFR-REL-03 | Crash-free sessions ≥ 99.5% in the closed test. |
| NFR-COMP-01 | minSdk 29, targetSdk 37, compileSdk 37 (the owner's choice, 2026-11-01, ADR-0007). targetSdk never falls below Play's current minimum. |
| NFR-COMP-02 | ABIs: arm64-v8a (primary), armeabi-v7a, and x86_64 (emulators and Chromebooks). |
| NFR-A11Y-01 | TalkBack is fully supported, touch targets are ≥ 48 dp, and status is never conveyed by color alone. |
| NFR-I18N-01 | All user-facing strings are externalized and the layouts are RTL-safe. English ships first. |
| NFR-MAINT-01 | Engine modules use Kotlin explicit API mode and ≥ 80% line coverage. Parsers have fuzz harnesses. |

## Out of scope for 1.0

- Root features
- TLS interception
- Anti-theft and remote wipe
- App lock
- Remote VPN servers
- Call recording or call-audio analysis (blocked by the platform)
- SMS permissions (Play's anti-smishing exception requires an established
  protection track record)
- AccessibilityService (ADR-0004)
- Cloud upload of samples

## Glossary

| Term | Meaning |
| :- | :- |
| **IOC** | Indicator of compromise: a hash, package and certificate pair, domain, or IP linked to a threat. |
| **PUA** | Potentially unwanted application, such as aggressive adware or fleeceware. |
| **Shield** | Aegis's local, on-device VPN used for DNS filtering and prefix blocking. Nothing is sent to a remote VPN server. |
| **Intel bundle** | A signed, versioned package of blocklists, IOCs, rules, and models. |
| **AI crawler** | An automated client run by an AI operator that fetches content for training, search indexing, or user-triggered answers. |
| **Split routing** | Only specific destination addresses are routed into the VPN interface. All other traffic bypasses it. |
