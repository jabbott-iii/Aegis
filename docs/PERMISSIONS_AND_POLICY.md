# Permissions and Google Play Policy

*Last updated: 2026-11-01 · Status: Draft. Re-check every Play policy reference before submission, because policies change.*

**Rule:** a PR that adds a permission, an exported component, or a sensitive API must update
this file in the same PR.

---

## 1. Permission inventory

| Permission / API | Feature | When requested | Play requirement | If unavailable |
|---|---|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Intel updates, DNS upstream, network checks | Install-time | None | — |
| `QUERY_ALL_PACKAGES` | App inventory and scanning (FR-SCAN-01) | Install-time | **Permissions Declaration Form.** Antivirus is a listed permitted use. | Core feature. Without it, scanning can't work, so it is required. |
| `REQUEST_DELETE_PACKAGES` | Uninstall prompt (FR-SCAN-08) | Install-time | Normal permission. The system shows a confirmation. | Deep link to app info instead |
| `POST_NOTIFICATIONS` (API 33+) | Alerts | In context, on the first protection feature enabled | Runtime | In-app alerts only, with a dashboard banner |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule work | Install-time | None | Work resumes on next app launch |
| `FOREGROUND_SERVICE` + type permission | Shield and long-running scan | Install-time | Type set in spike S1 and S4. **`specialUse`** needs a manifest `<property>` justification, which Play reviews. `systemExempted` applies to VPN apps configured through *Settings › VPN*. | — |
| `BIND_VPN_SERVICE` (service protection) + `VpnService.prepare()` consent | Shield and AI Crawler Shield | When the user enables the Shield | **VpnService declaration** (see below) | Scanner and AI features still work |
| `MANAGE_EXTERNAL_STORAGE` (All files access) | Download-folder scanning (FR-SCAN-05) | Opt-in from Settings, through the special-access screen | **Permissions Declaration Form.** "Anti-virus" is a listed permitted use (core purpose is scanning the device). | Single-file scan through the picker or share (FR-SCAN-06) |
| `BIND_NOTIFICATION_LISTENER_SERVICE` (user grants in Settings) | Scam detection (FR-AI-04), agent guard context | Opt-in | User Data policy: prominent disclosure and consent | Share-to-check text only |
| `CAMERA` | QR checker | When the QR scanner is opened | Runtime | Scan a QR code from an image (picker) |
| `ACCESS_WIFI_STATE` | Wi-Fi security type | Install-time | None | Shows "not available" |

### Deliberately **not** requested

| Permission | Why not |
|---|---|
| `READ_SMS`, `RECEIVE_SMS`, call log | Play limits these to default handlers and narrow exceptions. The anti-smishing exception requires "a track record of significant protection". The notification listener covers the use case instead. |
| `BIND_ACCESSIBILITY_SERVICE` | [ADR-0004](adr/0004-no-accessibility-service.md). Play's accessibility policy and Android 17's Advanced Protection Mode both restrict non-accessibility-tool use. It is also the top capability that malware abuses, and a security app shouldn't normalize it. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Restricted by Play. Aegis links to the battery settings with guidance instead. |
| Location (fine or coarse) | Only needed for Wi-Fi SSID and BSSID, which Aegis doesn't read |
| `PACKAGE_USAGE_STATS` | Not needed for any 1.0 feature |
| `READ_MEDIA_*` | Shared media arrives through the share sheet or the picker, which needs no permission |

### Exported components and merged manifest entries

What the merged **release** manifest declares today. The app's own manifest requests no permissions. The merged manifest has one library-added self permission (below).
Re-check `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`
whenever a dependency changes, because libraries can merge in components and permissions.

| Entry | Comes from | Exported | Protection | Why |
|---|---|---|---|---|
| `MainActivity` (`MAIN`/`LAUNCHER`) | `app` | Yes (the platform requires it for the launcher) | None needed. It reads no intent data or extras. | Launcher entry point |
| `androidx.profileinstaller.ProfileInstallReceiver` | `androidx.profileinstaller`, through Compose | Yes | `android.permission.DUMP` (signature\|privileged\|development): held by the system, privileged system apps, and the adb shell. Ordinary apps can't get it. | Installs baseline profiles (NFR-PERF-03) |
| `androidx.startup.InitializationProvider` | `androidx.startup` | No | — | Library initializers |
| `androidx.room3.MultiInstanceInvalidationService` (`directBootAware`) | `androidx.room3:room3-runtime`, through `core:data` | No | — | Lets one process see another's writes to a shared Room database. It's idle unless a database enables multi-instance invalidation, which none does yet. |
| `<permission>` `io.github.jabbottiii.aegis.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | — | `signature` | Protects receivers registered with `RECEIVER_NOT_EXPORTED` on API < 33 |
| `<uses-permission>` for that same permission | `androidx.core` | — | Only apps signed with Aegis's key can hold it | Aegis grants it to itself so that it can send to those receivers |

Debug builds also merge in two exported activities that never reach a release build:
- `androidx.compose.ui.tooling.PreviewActivity` (from `ui-tooling`, `debugImplementation`). It has no protection, and it runs the composable that an intent extra names.
- `androidx.activity.ComponentActivity` (from `ui-test-manifest`). Compose UI tests use it as their host.

Don't hand debug builds to testers outside the team.

## 2. Prominent disclosures

Play requires in-app disclosures for sensitive data access. Each one is a **separate screen**,
shown immediately before the system prompt, describing what is accessed, why, and that it stays
on the device.

| Disclosure | Shown before | Must state |
|---|---|---|
| App inventory | First scan (onboarding) | Aegis reads the list of installed apps and their permissions to detect threats. The list is processed on the device and not shared. |
| **VPN (separate, required by Play)** | `VpnService.prepare()` | Aegis creates a local VPN on this device to filter DNS and block listed networks. Traffic isn't sent to a VPN server, decrypted, or used for ads. DNS lookups are forwarded to *<upstream>* over an encrypted connection. |
| All files access | The special-access settings screen | Aegis scans files such as downloaded APKs for malware. Files are scanned on the device and never uploaded. |
| Notification access | The notification-listener settings screen | Aegis checks message notifications for scams on the device. Message text is never stored or uploaded. |

## 3. VpnService

Summary of the Play VpnService policy as it applies to Aegis (re-verify before submission):

- **Eligible category:** "Device security apps (for example, anti-virus, mobile device management, firewall)".
- **Prohibited:** collecting personal or sensitive data without prominent disclosure and consent, and redirecting or manipulating other apps' traffic for monetization. Aegis has no ads and no traffic monetization.
- **Requirements:**
  - Document the VPN use in the Play listing.
  - Submit the Play Console **VpnService declaration form** with a demo video of 90 s or less and a separate video of the disclosure and consent flow.
  - Re-submit the form whenever VpnService usage changes.
- **Encryption clause:** the policy says VPN apps "must encrypt the data from the device to the VPN tunnel endpoint". Aegis's tunnel ends on the device, and forwarded DNS uses DoH or DoT. Say this explicitly in the declaration (risk R2).

## 4. Target SDK and platform

- Since **Aug 31 2026**, new apps and updates on Play must target **Android 16 (API 36)** or higher. Extensions to Nov 1 2026 were available on request.
- **Android 17 (API 37)** reached stable in June 2026. Plan to target 37 at launch and to track its behavior changes in S1 and S7.
- Android 17 relevant changes to track:
  - Advanced Protection Mode blocks accessibility access for non-tools. This doesn't affect Aegis, by design.
  - Most apps that aren't the intended recipient or the default SMS handler now get OTP SMS messages only after a delay. This doesn't affect Aegis, which reads no SMS.
  - A system-managed per-app VPN bypass screen. This may supersede Aegis's own bypass picker on API 37+, so evaluate it in S1.

## 5. Distribution

| Channel | Requirements |
|---|---|
| **Google Play** (primary) | Developer account, Play App Signing, Data safety form, privacy policy URL, content rating, the permission and VpnService declarations above. New **personal** accounts must complete a closed test (a minimum number of testers over a minimum period) before production. Confirm the current numbers in Play Console. |
| **Outside Play** (GitHub Releases, F-Droid) | Google's **developer verification** requirement for apps installed on certified Android devices takes effect on **Sept 30 2026 in Brazil, Indonesia, Singapore, and Thailand**, and expands globally in **2027**. Apps from unregistered developers need an "advanced" sideload flow (with a mandatory waiting period) or ADB. Register before any non-Play release. F-Droid would also need a `foss` flavor without proprietary SDKs (open decision #3). |

## 6. Data safety form (draft mapping)

Under Play's definitions, data processed only on the device isn't "collected". The draft answers:

| Data type | Collected? | Shared? | Notes |
|---|---|---|---|
| Installed apps | No | No | Processed on the device |
| Messages (notification text) | No | No | In memory only, never persisted |
| Web browsing (DNS lookups) | **Decide at submission** | No | Lookups are forwarded to the DNS upstream the user selected, as part of providing the service, and are processed only in memory. Play may count forwarding to a third party as collection, in which case the answer is likely "collected, processed ephemerally, required for the feature". Confirm against Play's current definitions. Aggregated counters stay on the device. |
| Files and docs | No | No | Scanned on the device |
| Crash logs | No (1.0) | No | If opt-in crash reporting is added later, update this |

Security practices: data is encrypted in transit (bundle downloads use HTTPS) · no account
system, so there is no account deletion flow · committed to the Play Families policy: **N/A**
(not targeted at children).
