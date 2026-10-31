# Feature Spec: AI Threat Defense

*Last updated: 2026-10-31 · Status: Draft*
Requirements: FR-AI-01 … FR-AI-10 · ADR: [0004 no AccessibilityService](../adr/0004-no-accessibility-service.md), [0005 LLMs excluded from verdicts](../adr/0005-llms-excluded-from-verdict-path.md) · Phase 5

---

## 1. Scope

"AI threats" here means threats that **generative AI creates or amplifies** and that a non-root
Android app can do something useful about. Each item states plainly what Aegis cannot do.

| ID | Capability | Threat | Pri |
|---|---|---|---|
| AI-1 | AI app impersonation | Fake or trojanized ChatGPT, Claude, Gemini, and similar apps | P0 |
| AI-2 | AI access audit | AI-enabled apps with sensitive device access | P1 |
| AI-3 | Agent guard | Apps newly gaining screen-reading or notification-reading capability | P0 |
| AI-4 | Scam and smishing detection | LLM-written, personalized scams | P1 |
| AI-5 | Prompt-injection and hidden-text scanner | Instructions planted for AI agents | P1 |
| AI-6 | Synthetic-media provenance | Deepfake images and video | P2 |
| AI-7 | Voice-clone safeguards | Cloned-voice "family emergency" calls | P1 |
| AI-8 | AI egress monitor | Apps sending data to AI services | P1 (shared with [AI Crawler Shield](ai-crawler-shield.md#54-ai-service-egress-control-dns)) |
| AI-9 | Model robustness | Adversarial evasion of Aegis's own ML | P0 (engineering requirement) |

Design rule: **all verdicts are deterministic** (rules, IOCs, and classifiers with fixed
thresholds). Optional LLM features may *explain* a finding but never decide one, and
untrusted content is never allowed to act as instructions ([ADR-0005](../adr/0005-llms-excluded-from-verdict-path.md)).

---

## AI-1. AI app impersonation

**Threat.** Malicious clones use the branding of popular AI assistants (names such as "ChatGPT
Pro AI" with lookalike icons). They are distributed through sideloading, third-party stores, and
ads, and they carry fleeceware, adware, or banking trojans.

**Design.**

- The **official AI app registry** lives in the bundle as `ai_registry.pb`. Each entry has the brand, official package names, signing certificate SHA-256 values, and the expected installer(s). Examples: `com.openai.chatgpt`, `com.anthropic.claude`, `com.google.android.apps.bard`. **Every entry must be verified** against the store listing and the certificate observed on a clean install before it ships.
- **Lookalike detection.** An app is flagged when its label or package name contains brand tokens (after confusable-character folding, edit distance ≤ 2, and token matching), **or** its launcher-icon perceptual hash (pHash) is within the distance threshold of a reference hash, **and** its package and certificate don't match a registry entry.
  - Reference icons are **not** shipped. Only perceptual hashes are, which avoids redistributing brand assets.
- **Verdicts:**
  - The official package name with a non-official certificate gives *Malicious (repackaged)*.
  - A lookalike that is sideloaded and requests sensitive permissions gives *Suspicious*.
  - A lookalike from Play with no sensitive permissions gives an informational note ("Not the official *Brand* app").

**Acceptance.** Fixture apps named "ChatGPT AI Chat" (sideloaded, requesting SMS) and
`com.openai.chatgpt` signed with a test key both produce the expected verdicts. Official apps
installed from Play produce no finding.

## AI-2. AI access audit

**Threat.** Apps that embed cloud AI clients **and** hold screen, notification, file, or
microphone access can send personal content to AI services. Most of these apps aren't malware.
This is a privacy exposure that users usually can't see.

**Design.**

- **AI SDK fingerprints** (`ai_sdk_fingerprints.pb`) are DEX type prefixes and native library names, detected in the static stage. The initial candidates below must each be **verified against real artifacts** before shipping:

  | Kind | Candidate fingerprints |
  |---|---|
  | Cloud AI clients | `com.openai.` (OpenAI Java SDK) · `com.aallam.openai.` (community Kotlin client) · `com.anthropic.` (Anthropic Java SDK) · `com.google.ai.client.generativeai.` (Google AI client SDK) · `com.google.firebase.ai.` / `com.google.firebase.vertexai.` (Firebase AI) |
  | On-device runtimes | `org.tensorflow.lite.` / LiteRT · `ai.onnxruntime.` · `com.google.mediapipe.tasks.genai.` · `com.google.mlkit.genai.` · native `libllama.so`, `libonnxruntime.so` |

- **Exposure score** (low, medium, or high) combines:
  - (a) whether a cloud AI client is present, which weighs more than an on-device runtime, because on-device inference keeps data local and is generally *privacy-positive*
  - (b) enabled accessibility service or notification listener
  - (c) granted microphone, camera, contacts, SMS, or photos permissions
  - (d) All files access requested
  - (e) observed AI egress from AI-8, if the Shield is on

  Every factor is listed in the explanation.
- **Not a malware verdict.** It shows in the Audit and AI tabs with "Review access" deep links.

## AI-3. Agent guard

**Threat.** AI agents and "screen assistants", and malware posing as them, need accessibility
or notification-listener access to read the screen and act on it. Granting that access is the
single highest-risk step a user takes.

**Design.**

- Snapshot the enabled accessibility services (`AccessibilityManager.getEnabledAccessibilityServiceList`) and the enabled notification listeners (`NotificationManagerCompat.getEnabledListenerPackages`). Compare against the previous snapshot:
  - on app resume
  - after any package change
  - on a periodic job (every 15 min)
  - from an `AccessibilityManager` state listener while the process is alive
- On a **new** grant, alert: "*App* can now read everything on your screen and act on your behalf." The alert offers *Keep* or *Review (deep link to the setting)*. Severity rises if the app is sideloaded, is flagged by AI-1 or the scanner, or is unknown to the registry.
- The alert is suppressed for apps that declare `isAccessibilityTool` and come from Play (screen readers and similar). They still appear in the audit.
- **Context.** Android 17's Advanced Protection Mode blocks accessibility access for non-accessibility-tool apps. When APM is available, Aegis recommends it to high-risk users.

## AI-4. Scam and smishing detection

**Threat.** LLMs let scammers write fluent, personalized messages at scale: delivery-fee scams,
bank or government impersonation, romance and investment ("pig-butchering") scams, and job scams.

**Design decision: detect the scam, not the author.** Detectors that try to tell AI-written text
from human writing are unreliable and easy to evade. Aegis classifies **scam intent and
indicators** whoever wrote the message.

- **Input (opt-in).** A `NotificationListenerService` reads notifications from messaging, SMS, and email apps on a user-editable list, behind its own prominent disclosure. Users can also share text to Aegis.
  - Aegis does **not** request SMS permissions. Play's anti-smishing exception requires an established protection track record, which a new app does not have.
  - On Android 15+, OTP-bearing notifications are hidden from untrusted listeners. Aegis doesn't need OTPs, and that is fine.
- **Pipeline (in memory only, never persisted):**
  1. Extract URLs and pass them to the link checker (blocklists, lookalike, IDN).
  2. Rules: urgency combined with a payment request, gift cards, crypto addresses, "reply STOP" bait, mismatched sender and brand, and phone-number callbacks.
  3. An on-device text classifier (LiteRT, ≤ 10 MB, multilingual later) that outputs a scam probability.
- **Output.** For high confidence, post a heads-up notification: "This message looks like a scam: asks you to pay a fee through a link to an unknown site." It offers *Details*, *Not a scam*, and *Block link domain*. Aegis can't delete or hide another app's messages and doesn't try to.
- **Privacy.** No message text is stored. A verdict record keeps only the app, the time, the verdict, and the rule IDs.
- **Spike S7** validates delivery reliability per OEM.

## AI-5. Prompt-injection and hidden-text scanner

**Threat.** Content written to manipulate AI agents: "ignore previous instructions…", hidden
text in pages or messages, and Unicode tricks that are invisible to people but read by models.
It targets on-device assistants that read the screen, notifications, or links.

**Design (analyzes content that passes through Aegis: shares, QR payloads, link checks, and scanned notifications).**

- **Hidden-character detection (high precision):**
  - Unicode Tags block **U+E0000–U+E007F** (used for "ASCII smuggling")
  - zero-width characters **U+200B, U+200C, U+200D, U+2060, U+FEFF**
  - bidi controls **U+202A–U+202E, U+2066–U+2069**
  - mixed-script confusables

  Decoded tag-character payloads are shown to the user.
- **Injection phrasing:** a rule set of instruction-override patterns, role-play jailbreak markers, tool-invocation and exfiltration phrasing ("send the contents of…", "open this URL with the conversation"), and markdown or image-link exfiltration patterns. A small classifier can be added later if the rules underperform.
- **Output.** "This text contains hidden instructions that could manipulate an AI assistant." The finding offers *Show hidden text* and *Copy clean text*, where the clean text has the hidden characters stripped.
- **Limit.** Aegis cannot see what another app's agent is reading in real time without an AccessibilityService, which it deliberately avoids ([ADR-0004](../adr/0004-no-accessibility-service.md)).

## AI-6. Synthetic-media provenance check

**Threat.** Deepfake images and video used for scams, fake evidence, and harassment.

**Design (on images or video the user shares to Aegis):**

- **C2PA Content Credentials:** read and validate them with `c2pa-android` (a Kotlin wrapper over the C2PA C API; minSdk 28). Show the signer, the claim generator, any AI-generation assertions, and the edit history.
- **IPTC digital source type** in XMP: `trainedAlgorithmicMedia` and `compositeWithTrainedAlgorithmicMedia` indicate generative-AI involvement.
- **Copy rules.** Provenance is **evidence, not proof**. "No Content Credentials found" never means "real". Credentials and metadata can be stripped. The UI never says "real" or "fake".
- **Classifier-based deepfake detection is out of scope for 1.0.** Accuracy degrades badly on generators a model hasn't seen. Revisit only with an evaluated model and the label "experimental".
- Parsing runs in `:parser` with size limits.

## AI-7. Voice-clone safeguards

**Threat.** Cloned voices of relatives or executives, used to demand urgent payment.

**Reality.** Third-party Android apps cannot access call audio, and Play bans call recording
through accessibility. Real-time voice-deepfake detection during calls is therefore **not
possible** for Aegis.

**Design (advisory):**

- **Family safe word.** Aegis helps the user pick a verification phrase and shares an explainer with family through the share sheet, *without* the phrase itself. The phrase is stored encrypted on a `FLAG_SECURE` screen.
- **Verify caller checklist.** A quick-access tile or shortcut: hang up and call back on a known number, ask for the safe word, and never pay through gift cards, crypto, or wire under time pressure.
- **Future (P3).** A `CallScreeningService` (the call-screening role) for number reputation, once a data source with suitable terms exists.

## AI-8. AI egress monitor

This uses the Shield DNS attribution data: for each app, which AI services it looked up and how
often, over 7 days, shown in the AI tab with links to AI-2 and to the egress control in [AI Crawler Shield](ai-crawler-shield.md#54-ai-service-egress-control-dns).
It requires the Shield to be on. Apps that proxy AI traffic through their own backend aren't visible.

## AI-9. Robustness of Aegis's own models

- **Precedence.** ML alone yields at most *Suspicious* ([malware detection §3.6](malware-detection.md#36-verdict-aggregation)).
- **Features.** Prefer capability-bearing features that are costly to remove.
- **No oracle.** Raw scores are not shown. Verdict thresholds are bucketed, which limits how useful Aegis is as an evasion oracle.
- **Integrity.** Models are signed and versioned in the bundle and can be rolled back.
- **Evaluation.** An adversarial suite runs in the lab before every model release: obfuscated, repackaged, and LLM-mutated variants of known samples ([TESTING](../TESTING.md#adversarial-evaluation)).

## Optional: on-device explanation (P2)

If an on-device model is available (for example, through the Android ML Kit GenAI APIs on
supported devices), Aegis *may* turn structured evidence into a friendlier explanation. The
constraints:

- (a) Input is limited to Aegis-generated evidence fields, never raw message, page, or file content.
- (b) The output is display-only.
- (c) A deterministic template is always the fallback.
