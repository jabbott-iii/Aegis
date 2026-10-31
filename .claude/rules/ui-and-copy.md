---
paths:
  - "feature/**"
  - "core/ui/**"
  - "**/src/main/res/values*/strings.xml"
---

# UI, notifications, and user-facing copy

- **Honest claims.** Describe what Aegis does in platform terms: "scans apps after install", "blocks known dangerous domains", "blocks connections to known AI crawler networks".
  - Forbidden: "100% protection", "guaranteed", "unhackable", "invisible to AI", "AI can't access your data", "real-time file protection", "blocks inbound crawlers" (until S3 passes), and "real" or "fake" verdicts on media.
- **Verdict wording** uses *Malicious*, *Stalkerware*, *PUA*, *Suspicious*, *Unscannable*, and Clean. Every finding screen shows the evidence and an explanation (FR-SCAN-07).
- **Stalkerware:** lock-screen-visible notification text must be neutral ("Aegis found a security issue that needs your attention"). Removal is reachable only through the safety screen (device-security-audit §4).
- **Notifications:** one channel per category, grouped and deduplicated, with no more than 3 non-critical alerts per day by default (FR-UX-03). Block notifications are rate-limited to one per app per category every 30 minutes.
- **Disclosures** come before every sensitive system prompt. The VPN disclosure is its own screen and must be separate from the others (Play requirement). Use the copy from PERMISSIONS_AND_POLICY §2.
- **Sensitive screens** (safe word, evidence export, findings detail) set `FLAG_SECURE`, and on API 34+ call `setAccessibilityDataSensitive` (FR-AI-09).
- **Accessibility:** support TalkBack labels, touch targets of 48 dp or more, and 200% font scale. Never convey status by color alone (NFR-A11Y-01).
- **State:** ViewModels expose `StateFlow<UiState>`. Every UI state has a Compose preview, and every string lives in resources.
- **No WebView** that loads remote content (NFR-SEC-03).
