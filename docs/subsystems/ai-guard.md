# Subsystem: `ai:guard` — AI threat detectors

Detectors for threats that generative AI creates or amplifies and that a
non-root Android app can do something useful about: AI app impersonation,
AI SDK fingerprints, the agent guard, the scam and prompt-injection
detectors, and C2PA provenance checks (`docs/01-architecture.md`, "Module
layout"). It runs in the main process, and its impersonation and AI SDK
checks are a stage of the detector pipeline (`engine-scanner.md`).
Behavior is specified in `docs/features/ai-threat-defense.md`. Whether
`ai:guard` can be pure Kotlin/JVM is still open: the architecture keeps it
pure wherever possible, but it isn't one of the six modules the build
checks as pure. Nothing is built yet; Phase 5 builds it.

## Contents

| Detector or check | Provides | Spec |
| :- | :- | :- |
| Impersonation | Flags apps whose label or icon resembles a registered AI assistant but whose package or certificate doesn't match the official app (FR-AI-01) | "AI-1. AI app impersonation" |
| AI SDK fingerprints | Detects embedded cloud AI clients and on-device runtimes, for the AI access audit's exposure score (FR-AI-02) | "AI-2. AI access audit" |
| Agent guard | Alerts when an app newly gains an enabled accessibility service or notification-listener access (FR-AI-03) | "AI-3. Agent guard" |
| Scam detector | Scam and smishing detection on message notifications and shared text, on the device and opt-in (FR-AI-04) | "AI-4. Scam and smishing detection" |
| Injection detector | Hidden characters and injection phrasing in shared text, links and QR payloads (FR-AI-05) | "AI-5. Prompt-injection and hidden-text scanner" |
| C2PA | Content Credentials validation and IPTC digital-source-type metadata for shared media (FR-AI-06) | "AI-6. Synthetic-media provenance check" |

The spec names in the last column are sections of
`docs/features/ai-threat-defense.md`. The architecture doesn't place the
voice-clone safeguards (AI-7) or the AI egress monitor (AI-8, which reads
the Shield's DNS attribution data) in `ai:guard`; `features.md` covers
their screens. Model robustness (AI-9) belongs to `engine:ml`
(`engine-detection.md`).

**Phase 5's order:** impersonation; then the AI access audit and the agent
guard; scam and smishing detection; the prompt-injection and hidden-text
scanner; synthetic-media provenance; and last the voice-clone safeguards.

## Detectors

Each item names what the detector reads and what it produces. The rules
and thresholds are in the spec section.

- **Impersonation (AI-1).**
  - Reads the official AI app registry, `ai_registry.pb` in the bundle:
    brand, official package names, signing-certificate SHA-256 values, and
    expected installers. Every entry is verified against the store listing
    and a clean install's certificate before it ships.
  - Lookalikes: brand tokens in the label or package name (after
    confusable-character folding, edit distance ≤ 2, and token matching),
    or a launcher-icon perceptual hash within the threshold of a reference
    hash, together with a package and certificate that match no registry
    entry. Only perceptual hashes ship, never reference icons.
  - Produces *Malicious (repackaged)* for the official package name with a
    non-official certificate, *Suspicious* for a sideloaded lookalike with
    sensitive permissions, and an informational note for a lookalike from
    Play without them.
- **AI SDK fingerprints (AI-2).**
  - Reads `ai_sdk_fingerprints.pb`: DEX type prefixes and native library
    names, matched against the static stage's output (`engine-apk.md`).
    Each candidate fingerprint is verified against real artifacts before it
    ships.
  - Produces an explained exposure score (low, medium or high). It's not a
    malware verdict, and shows in the Audit and AI tabs.
- **Agent guard (AI-3).**
  - Snapshots the enabled accessibility services and notification
    listeners, and compares them with the previous snapshot on app resume,
    after any package change, on a periodic job every 15 min, and from an
    `AccessibilityManager` state listener while the process is alive.
  - Produces an alert on a new grant, with higher severity if the app is
    sideloaded, flagged by AI-1 or the scanner, or unknown to the registry.
    The alert is suppressed for apps from Play that declare
    `isAccessibilityTool`; they still appear in the audit.
  - The architecture's warning about an unknown accessibility service
    (`docs/01-architecture.md`, "Security architecture of Aegis itself")
    probably comes from this alert. The docs don't say so.
- **Scam detector (AI-4).**
  - Reads notifications from a user-editable list of messaging, SMS and
    email apps, through an opt-in `NotificationListenerService` behind its
    own prominent disclosure, plus text shared to Aegis. Aegis requests no
    SMS permissions.
  - Runs in memory only: URLs go to the link checker, then the rules, then
    an on-device LiteRT text classifier that outputs a scam probability.
  - Produces a heads-up notification for high confidence. A verdict record
    keeps only the app, the time, the verdict and the rule IDs.
- **Injection detector (AI-5).**
  - Reads content that passes through Aegis: shares, QR payloads, link
    checks and scanned notifications.
  - Detects Unicode tag characters (U+E0000–U+E007F), zero-width characters
    (U+200B, U+200C, U+200D, U+2060, U+FEFF), bidi controls (U+202A–U+202E,
    U+2066–U+2069), mixed-script confusables, and injection phrasing from a
    rule set.
  - Produces a finding with *Show hidden text* and *Copy clean text*.
    Decoded tag-character payloads are shown to the user.
- **C2PA (AI-6).**
  - Reads images or video shared to Aegis, with `c2pa-android` (a Kotlin
    wrapper over the C2PA C API, minSdk 28), and the IPTC digital source
    type in XMP (`trainedAlgorithmicMedia`,
    `compositeWithTrainedAlgorithmicMedia`). Parsing runs in `:parser` with
    size limits.
  - Produces evidence, not proof: the UI never says "real" or "fake".
    Classifier-based deepfake detection is out of scope for 1.0.

## Rules

- **Deterministic verdicts.** Every verdict comes from rules, IOCs, or
  classifiers with fixed thresholds. Optional LLM features may explain a
  finding but never decide one, and untrusted content is never allowed to
  act as instructions (`AGENTS.md` rule 3, ADR-0005).
- **The optional on-device explanation** (P2) takes only Aegis-generated
  evidence fields, never raw message, page or file content. Its output is
  display-only, and a deterministic template is always the fallback.
- **No AccessibilityService** (`AGENTS.md` rule 2, ADR-0004). So Aegis
  can't see what another app's agent is reading in real time.
- **Untrusted input** (`.claude/rules/untrusted-input-parsers.md`, which
  covers `ai/guard/**`):
  - Shared text, notifications and QR codes are analyzed only in memory,
    and never written to disk, logs or the database (NFR-PRIV-03).
  - Normalize Unicode for matching, but keep the original string for
    hidden-character detection.
  - Never fetch a URL being checked, and never render one in a `WebView`.
  - The hidden-text scanner gets a Jazzer fuzz harness.
- **Engine conventions** (`.claude/rules/kotlin-engine.md`): no `!!`
  (`checkSourceRules` enforces it under `ai/`), an injected clock and
  dispatchers, and evidence of at most 256 characters with no raw content.
  If `ai:guard` isn't pure, the build gives it no explicit API mode or
  coverage gate until open decision #8 is settled.

## Limits

| Limit | Value | Source |
| :- | :- | :- |
| Lookalike label distance | Edit distance ≤ 2 | AI-1 |
| Agent guard periodic check | Every 15 min | AI-3 |
| Scam text model | At most 10 MB and 30 ms per message | AI-4, spike S5 |
| Notification-listener delivery | ≥ 99% over 48 h on each of Pixel, Samsung and Xiaomi, or a documented mitigation | Spike S7 |
| `c2pa-android` APK size | The per-ABI delta is documented by spike S6 | Spike S6 |
| Download size | At most 30 MB per ABI split, models included | NFR-SIZE-01 |

## Tests

From `docs/04-quality.md` ("Feature test plans") and the spec's
acceptance criteria:

- **AI-1:** lookalike fixtures, the official package signed with a test
  key, and confusable-character folding cases. Acceptance: fixture apps
  named "ChatGPT AI Chat" (sideloaded, requesting SMS) and
  `com.openai.chatgpt` signed with a test key produce the expected
  verdicts, and official apps installed from Play produce no finding.
- **AI-3:** enable a fixture accessibility service and a notification
  listener, then assert the alert; assert the suppression for
  `isAccessibilityTool` apps.
- **AI-4:** a labeled corpus of scam and benign messages, with precision
  and recall per release, and a database inspection test that no text is
  persisted.
- **AI-5:** Unicode tag, zero-width and bidi fixtures, and a fuzzed text
  input.
- **Prompt injection against the verdict:** injection fixtures in tests
  (`docs/threat-model.md`, "B3. Threats and mitigations").

## Open questions

- **Can `ai:guard` be pure Kotlin/JVM?** If not, NFR-MAINT-01 for it is
  open decision #8 (`docs/00-overview.md`, "Open decisions").
- **Call screening** (FR-AI-10, P3) waits for a number-reputation data
  source with suitable terms.
