# Architecture decision records

Each ADR records one significant decision: its context, the decision
itself, its consequences, and the alternatives that were considered. New
ADRs are appended at the end using the template below; `/record-decision`
does this for you. Never edit an accepted ADR's decision. Supersede it with
a new ADR and set the old one's status to `Superseded by ADR-NNNN`.

A status is one of `Proposed`, `Accepted`, `Superseded by ADR-NNNN`, or
`Rejected`. An ADR is `Accepted` only when the owner made or approved the
decision. Every ADR below starts as `Proposed`. The owner marks it
`Accepted`, revises it, or rejects it after the Phase 0 spikes.

```markdown
## ADR-NNNN — <title>

- **Status:** Proposed | Accepted | Rejected | Superseded by ADR-NNNN
- **Date:** YYYY-MM-DD
- **Related:** <requirement IDs, specs>
- **Context:** <the forces at play; constraints such as platform, policy, privacy, cost>
- **Decision:** <what we will do, stated plainly>
- **Consequences:** <what becomes easier or harder, risks accepted, follow-up work>
- **Alternatives considered:** <each option — why it was not chosen>
- **Revisit when:** <a concrete trigger>
```

Status: all seven ADRs are `Proposed`. The owner chose the application ID
`io.github.jabbottiii.aegis` and minSdk 29 on 2026-11-01 (ADR-0007), but
has not accepted that ADR. Keep this index in step with the records below.

| ADR | Title | Status |
| :- | :- | :- |
| ADR-0001 | Offline-first, on-device verdicts | Proposed |
| ADR-0002 | No TLS interception | Proposed |
| ADR-0003 | Local VPN with split routing for network protection | Proposed (validate in S1–S3) |
| ADR-0004 | Do not use AccessibilityService | Proposed |
| ADR-0005 | LLMs excluded from the verdict path | Proposed |
| ADR-0006 | Signed, versioned threat-intel bundles with delegated signing | Proposed |
| ADR-0007 | Platform and build baseline (application ID, SDK levels, toolchain) | Proposed |

---

## ADR-0001 — Offline-first, on-device verdicts

- **Status:** Proposed
- **Date:** 2026-10-31
- **Related:** NFR-PRIV-01, FR-INTEL-04, `docs/privacy.md`
- **Context:**
  - Most commercial mobile antivirus products send app hashes, URLs, or
    samples to cloud services to get verdicts.
  - Cloud lookups improve detection of new threats, but they leak which
    apps and sites a user has, and they need a backend, API keys, and an
    ongoing operating cost.
  - Aegis is a privacy-focused project with no backend today.
  - Some useful cloud APIs also have restrictive terms. For example, Google
    Safe Browsing is limited to non-commercial use.
- **Decision:**
  - All verdicts are computed **on the device** from a signed intel bundle
    that is downloaded from static hosting.
  - A baseline bundle ships in the APK.
  - Cloud lookups (URL reputation, Play Integrity, hash reputation) are
    **optional, opt-in P2 features**, and each requires a backend proxy
    plus its own privacy review.
- **Consequences:**
  - **Easier:** strong privacy claims, no server to run, protection that
    works offline, and a simple Data safety form.
  - **Harder:** brand-new threats are caught only once the bundle updates
    or a heuristic or the ML model flags them. APK size and memory budgets
    have to accommodate the blocklists and models.
  - **Accepted risk:** a lower zero-day detection rate than cloud-backed
    competitors. Mitigated by daily bundles plus heuristics and ML.
- **Alternatives considered:**
  - **A cloud verdict service:** better freshness, but needs a backend and
    leaks data. Deferred.
  - **Hash-prefix lookups** (k-anonymity, in the style of Safe Browsing): a
    good privacy compromise for a future P2 feature, but it still needs a
    backend.
- **Revisit when:** Not recorded yet.

## ADR-0002 — No TLS interception

- **Status:** Proposed
- **Date:** 2026-10-31
- **Related:** FR-NET-01, FR-ACS-*, `docs/threat-model.md`
- **Context:**
  - Inspecting full URLs, HTTP user agents (to identify AI crawlers, for
    example), or page content over HTTPS would require Aegis to
    man-in-the-middle TLS. That means installing a user CA certificate and
    terminating connections.
  - Apps targeting API 24+ don't trust user CAs by default, so this would
    break many apps anyway.
  - It would also make Aegis a high-value attack target, and conflict with
    the "don't weaken encryption" principle.
  - It would weaken the user's security posture. The network checks warn
    users about exactly this setup (`docs/features/network-protection.md`,
    "Link checker, QR checker, and network checks").
- **Decision:**
  - Aegis **never** installs a CA certificate, never terminates or decrypts
    TLS, and never inspects HTTP payloads.
  - Network protection works only on **DNS names** and **IP prefixes**.
- **Consequences:**
  - No URL-path blocking on HTTPS. Phishing protection works at the domain
    level, plus the link checker for shared URLs.
  - No user-agent-based crawler detection. The AI Crawler Shield relies on
    the operators' published IP ranges.
  - Aegis can honestly say "Aegis never reads your encrypted traffic".
- **Alternatives considered:**
  - **Opt-in TLS MITM:** rejected. It is a security regression, and
    supporting it is a substantial burden.
  - **SNI inspection through a full-tunnel VPN:** gives hostnames for TLS
    connections that bypass DNS (DoH apps), but needs a userspace TCP/IP
    stack and a full tunnel, with a large battery cost. Encrypted Client
    Hello removes the signal anyway. Revisit only if DoH bypass becomes the
    dominant gap.
- **Revisit when:** DoH bypass becomes the dominant gap. This reopens only
  the SNI-inspection alternative.

## ADR-0003 — Local VPN with split routing for network protection

- **Status:** Proposed (validate with spikes S1, S2, S3)
- **Date:** 2026-10-31
- **Related:** FR-NET-*, FR-ACS-*, `docs/features/network-protection.md`,
  `docs/features/ai-crawler-shield.md`
- **Context:**
  - A non-root app's only device-wide network hook is `VpnService`. There
    are two common designs:
    1. **Full tunnel:** route all traffic into the TUN and forward it
       through a userspace TCP/IP stack. This gives full visibility of IP
       and port flows, but carries a large battery, latency, and complexity
       cost, along with a long bug tail (MTU, IPv6, QUIC, and so on).
    2. **Split routing:** route only selected destinations into the TUN.
       The DNS-only variant is a well-established technique for
       DNS-filtering apps.
  - Aegis needs (a) DNS filtering and (b) the ability to drop traffic to
    specific prefixes (AI crawler ranges). Neither requires seeing other
    traffic.
- **Decision:**
  - Use **split routing**. Only these destinations enter the TUN:
    - the virtual DNS resolver addresses (IPv4 and IPv6), and
    - the enabled blocked prefixes. Packets to these are answered with a
      synthesized TCP RST or ICMP unreachable, and dropped.
  - All other traffic bypasses the VPN.
  - Per-app bypass uses `addDisallowedApplication`.
  - Forwarded DNS goes upstream over DoH or DoT on protected sockets.
- **Consequences:**
  - **Easier:** near-zero overhead for regular traffic, a small and
    auditable packet-handling surface (DNS parser plus RST and ICMP
    synthesis), and no userspace TCP stack.
  - **Harder:**
    - Aegis needs a minimal TCP responder for DNS-over-TCP fallback on the
      virtual resolver.
    - Route count limits must be measured (S2).
    - Behavior with lockdown mode and Private DNS must be characterized
      (S1).
  - **Not achievable:** per-app IP blocking other than by bypassing the VPN
    entirely, visibility of non-DNS flows, and, unless S3 shows otherwise,
    inbound filtering.
  - **Shared constraint:** Android allows one active VPN, so Aegis can't
    run alongside a commercial VPN (risk R4 in `docs/00-overview.md`, "Top
    risks and mitigations").
- **Alternatives considered:**
  - **Full tunnel** (for example, built on an existing tun2socks-style
    stack): more capability, but the cost and risk outweigh the benefit for
    Aegis's needs. Revisit if per-app IP firewalling becomes a requirement.
  - **Asking users to set Private DNS** to a filtering resolver: no VPN
    needed, but it can't be configured programmatically, has no per-app
    attribution, and can't block IP prefixes.
  - **A remote VPN server:** contradicts ADR-0001 and introduces a trusted
    third party.
- **Revisit when:**
  - Spikes S1, S2, and S3 report. The status says this decision is to be
    validated with them.
  - Per-app IP firewalling becomes a requirement. That reopens the full
    tunnel.

## ADR-0004 — Do not use AccessibilityService

- **Status:** Proposed
- **Date:** 2026-10-31
- **Related:** FR-AI-03, FR-AI-05, `docs/permissions-and-policy.md`
- **Context:** An AccessibilityService could let Aegis read on-screen
  content, detect overlay attacks in real time, or scan what AI agents are
  reading. The costs:
  - Google Play restricts the Accessibility API for apps that aren't
    accessibility tools, and requires disclosure and review.
  - Android 17's Advanced Protection Mode blocks accessibility access for
    non-tool apps. The high-risk users who most need protection would lose
    the feature.
  - Accessibility is the capability banking trojans and stalkerware abuse
    most. Asking users to grant it to a security app teaches exactly the
    behavior Aegis warns them against (see
    `docs/features/ai-threat-defense.md`, "AI-3. Agent guard").
  - Holding it would make Aegis a far more attractive target.
- **Decision:**
  - Aegis does not declare or use an AccessibilityService.
  - It uses public, non-privileged APIs for visibility instead: the
    enabled-services lists, notification-listener access (opt-in), and
    package metadata.
- **Consequences:**
  - No real-time screen-content scanning, so prompt-injection scanning is
    limited to content that passes through Aegis.
  - No real-time overlay-attack detection. Aegis detects the
    *preconditions* instead: an app holding accessibility and overlay
    access.
  - A clean Play review path and a consistent security message.
- **Alternatives considered:** an opt-in accessibility mode. Rejected for
  1.0 for the reasons above. Revisit only with a specific, high-value use
  case and a review of the Play policy.
- **Revisit when:** a specific, high-value use case for accessibility comes
  up, together with a review of the Play policy.

## ADR-0005 — LLMs excluded from the verdict path

- **Status:** Proposed
- **Date:** 2026-10-31
- **Related:** FR-AI-*, FR-DET-*, `docs/threat-model.md` ("B3. Threats and
  mitigations")
- **Context:**
  - It is tempting to use an LLM (on-device or cloud) to judge whether a
    message is a scam, a page is malicious, or text contains a prompt
    injection.
  - But the content Aegis analyzes is **attacker-controlled by
    definition**. An LLM that reads it can be prompt-injected into
    returning "safe".
  - LLM outputs are also non-deterministic, hard to test against fixed
    gates, and costly to run on the device.
- **Decision:**
  - Verdicts come only from **deterministic components**: IOC matching,
    declarative rules, and ML classifiers with fixed, versioned thresholds.
  - An LLM may be used **only** to produce user-facing explanations, and
    only under these constraints:
    1. Its input is limited to Aegis-generated structured evidence, such as
       rule IDs, field names, and categories. Raw untrusted content is
       never part of the input.
    2. Its output is display-only and never parsed for decisions or
       actions.
    3. A deterministic template is always available as the fallback.
- **Consequences:**
  - Verdicts are reproducible and testable against the detection gates
    (`docs/04-quality.md`, "Detection quality").
  - Aegis can't be talked out of a detection by the content it is
    scanning.
  - Explanations may be less fluent when no on-device model is available.
- **Alternatives considered:**
  - **An LLM as a secondary signal:** possible later as a *weak* signal
    that cannot reduce severity, only raise a finding to human review. Not
    in 1.0.
  - **Cloud LLM classification:** rejected. It is also a privacy issue
    under ADR-0001.
- **Revisit when:** Not recorded yet.

## ADR-0006 — Signed, versioned threat-intel bundles with delegated signing

- **Status:** Proposed
- **Date:** 2026-10-31
- **Related:** FR-INTEL-01 … 05, FR-DET-07, `docs/01-architecture.md`
  ("Key flows", "Intel update")
- **Context:**
  - The intel bundle controls what Aegis blocks and flags. Anyone who can
    forge a bundle can blind Aegis or make it block legitimate services, so
    its integrity has to be at least as strong as the app's own.
  - Bundles should update daily, ideally from automated CI, but a signing
    key held in CI is a high-value secret.
- **Decision:**
  - **Format:**
    - `manifest.json`: version (monotonic integer), createdAt, validUntil,
      minAppVersion, minEngineVersion, and a list of parts (name, SHA-256,
      size).
    - `manifest.sig`: an Ed25519 signature over the exact manifest bytes.
    - The parts themselves, in protobuf or binary formats.
    - The app verifies the signature **before** parsing anything else, then
      verifies each part's hash.
  - **Two-tier keys:** a small subset of the ideas in The Update Framework.
    - An **offline root key** (Ed25519, held on a hardware token by the
      maintainer). Its public key is **pinned in the app**, with two slots
      to allow rotation in a later app release.
    - The root signs a **delegation record** for an **online signing key**,
      valid for 90 days or less. The record contains the key ID, the public
      key, notBefore, notAfter, and the allowed bundle channels.
    - CI signs bundles with the online key inside a protected GitHub
      Actions environment that has required reviewers. Each bundle ships
      with the current delegation record.
    - The app checks two things: that the root signature over the
      delegation is valid and inside its time window, and that the online
      signature over the manifest is valid.
  - **Freshness and rollback:**
    - Reject `version ≤ installed`.
    - Accept bundles past `validUntil` only if they are newer than the
      installed one. Expired protection keeps running, with a warning in
      the UI.
  - **Swap:** write to staging, verify, then rename atomically. Keep
    `previous` for local rollback.
  - **Revocation:**
    - A compromised online key is limited by the delegation expiry. The
      root issues a new delegation, and the old key ID goes on a
      `revokedKeyIds` list inside the next root-signed delegation record.
    - A compromised root key requires an app update that rotates the pinned
      slot.
- **Consequences:**
  - Daily releases can be fully automated without the root key ever
    entering CI.
  - The bundle host (GitHub Releases or a CDN) is not trusted for
    integrity, only for availability. HTTPS is still used, for privacy and
    defense in depth.
  - Implementation is more complex than a single key, because Aegis has to
    handle delegation parsing, expiry, and revocation lists. All of that
    gets unit tests and fuzzing.
- **Alternatives considered:**
  - **A single key in CI:** simplest, but a CI compromise could forge
    bundles indefinitely.
  - **A single offline key with manual signing:** secure, but daily manual
    signing isn't sustainable for one maintainer.
  - **Full TUF or Sigstore:** a stronger, standard model, but heavier than
    a single-publisher bundle needs today. Revisit if Aegis ever adds more
    publishers or channels.
- **Revisit when:** Aegis adds more publishers or channels. That reopens
  full TUF or Sigstore.

## ADR-0007 — Platform and build baseline

- **Status:** Proposed
- **Date:** 2026-11-01
- **Related:** NFR-COMP-01, NFR-SEC-07, `docs/00-overview.md` ("Open
  decisions" #5), `docs/permissions-and-policy.md` ("Target SDK and
  platform"), `CONTRIBUTING.md` ("Development setup"),
  `docs/03-platforms.md` ("Toolchain")
- **Context:** The Phase 0 scaffold has to fix several values that are
  expensive or impossible to change later:
  - **Application ID:** once an app is uploaded to Google Play, its
    application ID can't change. A new ID means a new store listing. The
    owner doesn't currently own a domain for the project. The owner chose
    the ID and minSdk on 2026-11-01.
  - **minSdk:** open decision #5 (`docs/00-overview.md`, "Open decisions").
    API 29 is the lowest level where the active VPN app can call
    `ConnectivityManager.getConnectionOwnerUid` for per-app attribution
    (`docs/01-architecture.md`, "DNS lookup through the Shield";
    `docs/features/network-protection.md`). Raising minSdk to 31 removes
    Android 10 and 11 compatibility branches, such as reading the Wi-Fi
    security type, but drops those devices.
  - **targetSdk:** since Aug 31 2026, Play has required new apps and
    updates to target API 36 or higher (`docs/permissions-and-policy.md`,
    "Target SDK and platform"). Android 17 (API 37) has been stable since
    June 2026, and spikes S1 and S7 need to observe its behavior.
  - **Build toolchain:**
    - AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the
      `org.jetbrains.kotlin.android` plugin is a deprecated path.
    - The latest stable detekt (1.23.8) runs an embedded Kotlin 2.0.21
      compiler. That compiler's IntelliJ `JavaVersion` parser rejects
      JDK 25 version strings, so detekt fails to start on JDK 25. detekt
      2.x is still alpha.
    - Android Studio bundles a JDK 21 (JBR).
    - D8 and R8 accept Java 17 bytecode, and engine modules ship inside the
      APK, so they mustn't use JDK APIs newer than the ones Android
      provides.
  - **Supply chain** (NFR-SEC-07, `docs/threat-model.md`, "B3. Threats and
    mitigations"): dependencies must be pinned and verified, and the build
    should download nothing that a person hasn't reviewed.
- **Decision:**
  1. The application ID and base package are
     **`io.github.jabbottiii.aegis`**. Module packages are
     `io.github.jabbottiii.aegis.<area>.<name>`. Android library namespaces
     come from the Gradle path, and `:app` uses the base package.
  2. **minSdk 29. compileSdk 37 and targetSdk 37**
     (`platforms;android-37.0`). Only the convention plugins read these
     values, from `gradle/libs.versions.toml`.
  3. The **Gradle daemon runs on JDK 21**, selected by
     `gradle/gradle-daemon-jvm.properties`. Gradle never downloads a JDK,
     so a JDK 21 must be installed where Gradle can find it. All modules
     compile to **Java 17 bytecode**, and pure JVM modules compile with
     `-Xjdk-release=17`, so they can use only the JDK 17 API.
  4. **AGP 9 with built-in Kotlin**, KSP for annotation processing, and
     convention plugins in `build-logic/`. Every version lives in
     `gradle/libs.versions.toml`. **Strict SHA-256 dependency
     verification** applies to every build, and repository content filters
     limit each repository to the groups it must serve.
  5. Versions are the latest stable releases at scaffold time. Tools stay
     on stable releases: no alpha toolchain until it is needed and
     reviewed.
- **Consequences:**
  - Per-app VPN attribution is available on every supported device.
    Android 10 and 11 still need their compatibility branches, and CI runs
    instrumented tests on API 29 and API 37 emulators.
  - The application ID ties the listing to the owner's GitHub namespace.
    Moving to a product domain later means a new listing.
  - Targeting API 37 brings Android 17 behavior changes in now, while S1
    and S7 can still study them. Espresso is pinned to 3.7.0 because the
    version Compose pulls in (3.5) breaks on API 37. Each year's Play
    target deadline needs a follow-up.
  - Developers need a JDK 21 that Gradle can find: on `JAVA_HOME` or
    `PATH`, in an OS-standard location, or pointed at explicitly (Android
    Studio's JBR). Local builds and CI run the same JDK.
  - Under AGP 9, detekt 1.23 has no Android variant tasks, and CI's plain
    `detekt` has no type resolution. Rules that need type resolution don't
    run. `checkSourceRules` covers the critical ones: logging, dispatchers,
    `GlobalScope`, `runBlocking`, and engine `!!`. Follow-up: move to
    detekt 2.x once it is stable, then re-check the JDK 21 constraint.
  - Every dependency change regenerates `gradle/verification-metadata.xml`,
    and someone reviews the diff. Dependabot PRs fail until that happens,
    which is intended. The macOS and Windows `aapt2` hashes are added by
    hand for each AGP version.
- **Alternatives considered:**
  - **`app.aegissecurity` or another product-domain ID:** it reads better,
    but it implies owning the domain, so the owner didn't choose it.
  - **minSdk 31:** fewer compatibility branches, but it drops Android 10
    and 11. The owner didn't choose it.
  - **minSdk 26 to 28:** these lose `getConnectionOwnerUid` (API 29), so
    per-app attribution for the Shield would be impossible.
  - **targetSdk 36:** it meets today's Play minimum, but Android 17 is
    stable, and finding its behavior changes in Phase 0 is cheaper than
    finding them at launch.
  - **Daemon on JDK 25 with detekt 2.0 alpha:** rejected. It puts alpha
    tooling on the main quality gate.
  - **Gradle toolchain auto-provisioning (foojay resolver):** rejected for
    now. It adds a plugin and a JDK download at build time that nobody
    reviews.
  - **`org.jetbrains.kotlin.android` with `android.builtInKotlin=false`:**
    rejected. AGP 9 deprecates that path.
- **Revisit when:**
  - detekt 2.x is stable: move to it, then re-check the JDK 21 daemon
    constraint.
  - Each year's Play target API deadline: the targetSdk needs a follow-up.
