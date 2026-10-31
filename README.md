# Aegis

Aegis is a planned Android security app, written in Kotlin. It gives you standard
mobile antivirus protection and adds defenses against AI-era threats, including an optional
**AI Crawler Shield** that blocks known AI crawler and agent networks.

> **Status: planning.** The repository has documentation and no application code yet.
> Each design decision below is a proposal and stays one until an ADR in
> [`docs/adr/`](docs/adr/README.md) marks it *Accepted*.

## What Aegis will do

| Area | Capabilities (planned) |
|---|---|
| **Malware protection** | Scans apps when they are installed or updated, on demand, and on a schedule. Detection uses signature/IOC matching (hash, package, signing certificate), manifest and DEX heuristics, and an on-device ML classifier. Covers stalkerware detection with a safety-first removal flow, and scans APK files in Downloads. |
| **Device security audit** | Checks screen lock, patch age, developer options and USB debugging, user-installed CA certificates, risky permission grants, and apps holding accessibility, notification-listener, or device-admin access. |
| **Network protection ("Aegis Shield")** | A local, on-device VPN that blocks malware, phishing, scam, and stalkerware command-and-control domains at the DNS level. It attributes blocks to individual apps and offers a link checker and a QR checker. Traffic is never decrypted. |
| **AI threat defense** | Flags fake AI-assistant apps, audits which apps have AI capabilities alongside screen or notification access, and alerts when an app gains screen-reading access. Also detects scam messages, scans for prompt injection and hidden text, checks media provenance (C2PA), and offers voice-clone scam safeguards. |
| **AI Crawler Shield** (toggle, off by default) | Blocks traffic to and from the published network ranges of AI crawlers, AI search indexers, and user-triggered AI fetchers. It can also control which apps may send data to AI services, and it explains how exposed the device actually is. See the [feature spec](docs/features/ai-crawler-shield.md) for exactly what it can and cannot do. |

## Design principles

1. **On-device first.** Verdicts are computed locally. By default the only network traffic
   is the download of signed threat-intelligence updates ([ADR-0001](docs/adr/0001-offline-first-on-device-verdicts.md)).
2. **No TLS interception.** Aegis never installs a CA certificate and never decrypts traffic ([ADR-0002](docs/adr/0002-no-tls-interception.md)).
3. **Honest claims.** The UI states only protections that have been verified on real devices. Platform limits are explained to the user, not hidden.
4. **Least privilege.** Every permission maps to a feature, is requested only when that feature is enabled, and comes with a working fallback ([permissions](docs/PERMISSIONS_AND_POLICY.md)).
5. **Deterministic verdicts.** LLMs can help explain a result but never decide one ([ADR-0005](docs/adr/0005-llms-excluded-from-verdict-path.md)).

## Documentation

| Document | Purpose |
|---|---|
| [Implementation plan](docs/PLAN.md) | Phases, milestones, spikes, risks, and open decisions |
| [Requirements](docs/REQUIREMENTS.md) | Functional and non-functional requirements with IDs and priorities |
| [Architecture](docs/ARCHITECTURE.md) | Modules, components, data flows, background execution, and tech stack |
| [Threat model](docs/THREAT_MODEL.md) | Threats Aegis defends against, threats to Aegis itself, and residual gaps |
| Feature specs | [Malware detection](docs/features/malware-detection.md) · [Device security audit](docs/features/device-security-audit.md) · [Network protection](docs/features/network-protection.md) · [AI Crawler Shield](docs/features/ai-crawler-shield.md) · [AI threat defense](docs/features/ai-threat-defense.md) |
| [Data sources](docs/DATA_SOURCES.md) | Threat-intel and crawler-range sources, with license and terms status |
| [Permissions & Play policy](docs/PERMISSIONS_AND_POLICY.md) | Each permission's justification, Play declarations, and distribution rules |
| [Privacy](docs/PRIVACY.md) | Data inventory, what leaves the device, retention, and Data safety mapping |
| [Testing](docs/TESTING.md) | Test strategy, detection-quality metrics, malware-sample handling, and the device matrix |
| [Development](docs/DEVELOPMENT.md) | Planned project layout, conventions, CI gates, and secrets handling |
| [ADRs](docs/adr/README.md) | Architecture decision records |
| [Security policy](SECURITY.md) | How to report vulnerabilities |

## Planned platform baseline

- **Language/UI:** Kotlin, Jetpack Compose, Material 3
- **minSdk:** 29 (Android 10) proposed. See [PLAN § Open decisions](docs/PLAN.md#6-open-decisions).
- **targetSdk:** at least 36. Since Aug 31 2026, Google Play has required new apps and updates to target Android 16 (API 36). Android 17 (API 37) is stable, so plan to target 37 at launch.

## License

No license has been chosen. Decide on one before accepting outside contributions or bundling
third-party data, because some data sources carry attribution or commercial-use terms
(see [DATA_SOURCES.md](docs/DATA_SOURCES.md)).
