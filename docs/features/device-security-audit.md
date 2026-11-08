# Feature: Device security and privacy audit

This spec is a draft, and nothing in it is built yet.

- **Requirements:** FR-AUD-01 … FR-AUD-04, FR-DET-05
  (`docs/requirements.md`).
- **Roadmap:** Phase 1 (`docs/roadmap.md`).

## Summary

The audit checks device configuration and the privileges that apps hold,
then turns the results into a short list of prioritized recommendations,
each with a deep link. It is **advisory**: Aegis cannot change system
settings itself.

## Posture checks (FR-AUD-01)

| Check | API | Fail condition | Recommendation |
| :- | :- | :- | :- |
| Secure lock screen | `KeyguardManager.isDeviceSecure()` | No PIN, pattern, or password | Set a screen lock (`Settings.ACTION_SECURITY_SETTINGS`) |
| Security patch age | `Build.VERSION.SECURITY_PATCH` | More than 90 days old (warning), more than 180 days (fail) | Check for system updates |
| OS support | `Build.VERSION.SDK_INT` against a support table in the bundle | The manufacturer no longer ships updates for this OS version (best effort) | Explain the risk and suggest upgrading if possible |
| Developer options | `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED` | Enabled | Turn off when not needed |
| USB debugging | `Settings.Global.ADB_ENABLED` | Enabled | Turn off |
| Wireless debugging | `Settings.Global` (verify readability) | Enabled | Turn off |
| Storage encryption | `DevicePolicyManager.getStorageEncryptionStatus()` | Not active (rare on API 29+) | Informational |
| User-installed CA certificates | `AndroidCAStore` aliases starting with `user:` | Present | Remove if unrecognized |
| Advanced Protection | The Android 16+ Advanced Protection API, where available (verify the permission requirements) | Off | Explain what it offers, especially for high-risk users |
| Play Integrity / Play Protect | Play Integrity `deviceIntegrity` and `playProtectVerdict` | — | **P2.** Tokens must be decoded on a server (`docs/00-overview.md`, "Open decisions" #4) |

Aegis does **not** try to detect root with brittle file probes in 1.0.
Those checks are easy to spoof and they produce support noise. Play
Integrity, once a backend exists, is the reliable signal.

## Privacy and privilege audit (FR-AUD-02)

For each user-installed app, list:

- **Privileged components:** the accessibility services, notification
  listeners, and device admins that are **enabled**. These are the
  capabilities stalkerware and banking trojans abuse most.
- **Sensitive grants:** granted sensitive runtime permissions, namely
  location (including background), microphone, camera, contacts, SMS, call
  log, body sensors, nearby devices, photos and media.
- **Special access:** special access *requested* in the manifest, namely
  `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`,
  `MANAGE_EXTERNAL_STORAGE`, `PACKAGE_USAGE_STATS`. A third-party app
  cannot reliably see whether another app has actually been *granted*
  special access, so these are labeled "can request".
- **Source and AI badge:** the installer source, plus an **AI capability
  badge** from the AI access audit
  (`ai-threat-defense.md`, "AI-2. AI access audit").

Sorting is by risk: privileged components first, then sideloaded apps,
then the number of sensitive grants.

## Stalkerware safety flow

Stalkerware is usually installed by someone with physical access to the
phone, often an intimate partner. Removing it can alert that person and
escalate the danger. The flow therefore never removes anything
automatically:

1. **Neutral notification text.** The notification says "Aegis found a
   security issue that needs your attention", and never "stalkerware" in
   the lock-screen-visible text.
2. **Safety screen first.** "This app may let someone monitor your phone.
   If you think someone you know installed it, removing it may alert them.
   Consider your safety before acting."
3. **Options:**
   - *Learn more and get support* (links to established victim-support
     resources; the list is maintained in the bundle so it can be
     localized)
   - *Save evidence* (a local report with the package, certificate,
     install date, and privileges, exported only when the user chooses)
   - *Remove now*
   - *Decide later*
4. **Remove now:** deactivate the device admin if one is present (guided),
   revoke accessibility and notification access (guided), then uninstall.
5. **Afterwards,** Aegis offers to suggest **changing the device PIN and
   account passwords**.

The IOC data comes from the public *stalkerware-indicators* project
(CC-BY 4.0; attribution is in *About › Data sources*). It provides package
names, certificates, and C2 domains and IPs.

## Scoring and presentation

- **Summary:** each check has a weight. The dashboard summarizes results as
  *Good*, *Needs attention*, or *At risk*. There is deliberately no
  percentage score, because scores invite users to game them rather than
  understand the issue.
- **Limit:** no more than three recommendations are shown at once, ordered
  by impact divided by effort.
- **Deep links:** every recommendation deep-links to the exact settings
  screen where an intent exists, and otherwise gives step-by-step text with
  OEM variants.

## Acceptance criteria (selected)

- Every posture check renders a pass, fail, or not-available state on API
  29, 31, 34, 36, and 37 emulators.
- Enabling a fixture accessibility service changes the audit within one app
  resume, and triggers an agent guard alert
  (`ai-threat-defense.md`, "AI-3. Agent guard").
- The stalkerware fixture never produces a lock-screen notification
  containing the word "stalkerware", and the uninstall action is reachable
  only through the safety screen.
