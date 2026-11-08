# Subsystem: `feature:*` — screens

The UI modules, one per area of the app, each with its Compose screens and
ViewModels. A feature module depends on `core:*` plus the engine and shield
APIs it needs, and never on another feature module. `checkModuleGraph`
fails if a feature module declares or reaches another one
(`docs/01-architecture.md`, "Dependency rules"). Every screen builds on the
design system in `core:ui` (`core-ui.md`). Nothing is built yet. Today the
`app` module shows a Phase 0 shell (`app.md`), which `feature:dashboard`
replaces in Phase 1.

## Contents

| Module | Provides |
| :- | :- |
| `feature:dashboard` | One overall status and a status per module: Scanner, Shield, AI Crawler Shield, AI defense, Audit (FR-UX-02). It replaces the Phase 0 shell |
| `feature:scan` | The scanning screens of Phase 1's scan UX: scan progress, findings with their evidence and plain-language explanations, and remediation (`docs/features/malware-detection.md`, "Remediation") |
| `feature:shield` | The Shield and AI Crawler Shield screens. `.claude/rules/shield-vpn.md` covers `feature/shield/**` |
| `feature:ai` | The AI tab. The AI access audit (AI-2) and the AI egress monitor (AI-8) show there |
| `feature:audit` | The Audit tab: the posture checks, the privacy and privilege audit, and their recommendations (`docs/features/device-security-audit.md`). The AI access audit also shows there |
| `feature:onboarding` | Onboarding that requests nothing up front (FR-UX-01) |
| `feature:settings` | Settings. `docs/privacy.md` ("User controls") names *Settings › Privacy › Delete local data* |

The module list is fixed in `docs/01-architecture.md` ("Module layout").
Add a module with `/new-module` when its phase starts.

**The audit's checks.** `AuditService` runs the device posture and privacy
checks in the main process (`docs/01-architecture.md`, "Components"); the
architecture doesn't say which module holds it. The audit is advisory:
Aegis can't change system settings itself, so each failed check has a
recommendation and a deep link to the relevant setting (FR-AUD-01 to
FR-AUD-03). Play Integrity and Play Protect verdicts (FR-AUD-04, P2) need a
backend (`docs/00-overview.md`, "Open decisions" #4).

## Screens without a module yet

The specs name these screens but don't say which module holds them:

- **The stalkerware safety flow,** the only way to the uninstall action
  for a stalkerware finding (`docs/features/device-security-audit.md`,
  "Stalkerware safety flow").
- **The link checker** (share target and paste box, FR-NET-06) and **the QR
  checker** (FR-NET-07), in `docs/features/network-protection.md` ("Link
  checker, QR checker, and network checks").
- **The voice-clone safeguards** (AI-7): the family safe-word helper on a
  `FLAG_SECURE` screen, and the verify-caller checklist.
- **Scan history** (FR-SCAN-10): 90-day retention, with export as a local
  file that the user initiates.
- **About › Data sources** (FR-INTEL-05), with the attributions that source
  licenses require.
- **"Report a false positive"** (FR-UX-05), which produces a local report
  the user can choose to send. Nothing is uploaded automatically.
- **Open-source licenses** (Phase 7), generated from licensee's
  `artifacts.json`.

## Rules for every screen

- **Honest claims** (`AGENTS.md` rule 10). UI copy states only protection that
  has been verified. Aegis's "real-time" protection is install and update
  scanning, DNS and route filtering, and notification analysis, and the UI
  describes it in exactly those terms (`docs/requirements.md`, "Users and
  context"). "Inbound" crawler blocking stays forbidden until spike S3
  passes (FR-ACS-08), and a copy review checklist runs on every UI change.
  The wording rules are in `.claude/rules/ui-and-copy.md`.
- **Verdicts with evidence.** Every finding shows its verdict, severity,
  the detector or rule that fired, the evidence, and a plain-language
  explanation (FR-SCAN-07).
- **Permissions in context.** Each permission is requested where it's
  needed, behind its own prominent disclosure (FR-UX-01,
  `docs/permissions-and-policy.md`, "Prominent disclosures").
- **Notifications** (FR-UX-03): one channel per category, deduplication,
  and no more than 3 non-critical alerts per day by default.
- **Stalkerware** (`AGENTS.md` rule 13): notifications use neutral wording,
  and removal happens only through the safety flow. Never auto-remove.
- **Audit presentation:** the dashboard summarizes the audit as *Good*,
  *Needs attention* or *At risk*, with deliberately no percentage score,
  and shows no more than three recommendations at once
  (`docs/features/device-security-audit.md`, "Scoring and presentation").
- **Sensitive screens** use `FLAG_SECURE` and, on API 34+,
  `setAccessibilityDataSensitive` (FR-AI-09).
- **No `WebView` loads remote content** (NFR-SEC-03).
- **Accessibility** (NFR-A11Y-01): TalkBack is fully supported, touch
  targets are at least 48 dp, and status is never conveyed by color alone.
  Light and dark themes, dynamic color, and 200% font scaling (FR-UX-04).
- **Strings** are all externalized, and layouts are RTL-safe. English ships
  first (NFR-I18N-01).
- **State:** state is hoisted, ViewModels expose `StateFlow<UiState>`, and
  every screen state has a preview (`CONTRIBUTING.md`, "Coding
  conventions").

## When they arrive

- **Phase 1:** the scan UX (dashboard status, scan progress, findings,
  remediation), the stalkerware safety flow, the device security audit v1,
  and the single-file scan.
- **Phase 3:** the link checker, the QR scanner, the network checks, and
  the Shield's prominent-disclosure screen.
- **Phase 4:** the AI Crawler Shield's master toggle, its levels, the
  status panel, the provenance view and the exposure check
  (`docs/features/ai-crawler-shield.md`, "User experience").
- **Phase 5:** the AI threat defense features.
- **Phase 7:** the Open-source licenses screen.

## Tests

- **Every state has a screenshot or semantic test.** UI modules aren't
  coverage-gated (`docs/04-quality.md`, "Test layers").
- **Instrumented tests** cover permission flows and uninstall prompts.
- **Macrobenchmark:** cold start (NFR-PERF-03: at most 1.0 s to an
  interactive dashboard, with a baseline profile) and dashboard jank.
- **A TalkBack pass** per milestone (`docs/roadmap.md`, "Cross-cutting
  workstreams").
