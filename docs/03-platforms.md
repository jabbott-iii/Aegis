# 03 — Platforms

What Aegis needs from Android, what changes from one API level to the
next, and what we still have to learn about OEM devices. Aegis runs on one
platform, Android, from API 29 to API 37, as a non-root, sandboxed app.

Each row cites the doc that states it. Platform behavior that no doc
states is not listed here. Items marked **verify** are what the specs
believe, not yet checked on a device; the spike named, or the task that
implements the feature, replaces each with the observed fact. No spike has
started yet (`docs/roadmap.md`, "Phase 0 — Foundations and technical
spikes").

## Targets

| Item | Value | Source |
| :- | :- | :- |
| Application ID and base package | `io.github.jabbottiii.aegis` | ADR-0007 |
| Module packages | `io.github.jabbottiii.aegis.<area>.<name>`. Android library namespaces come from the Gradle path; `:app` uses the base package | ADR-0007 |
| minSdk | 29 (Android 10) | ADR-0007, NFR-COMP-01 |
| compileSdk | 37 (`platforms;android-37.0`) | ADR-0007, NFR-COMP-01 |
| targetSdk | 37 (Android 17). It never falls below Play's current minimum (API 36 since Aug 31 2026) | ADR-0007, NFR-COMP-01, `docs/permissions-and-policy.md` ("Target SDK and platform") |
| ABIs | arm64-v8a (primary), armeabi-v7a, and x86_64 (emulators and Chromebooks) | NFR-COMP-02 |
| Bytecode | Java 17 for every module | ADR-0007 |
| Devices we test on | See `docs/04-quality.md` ("Device matrix") | — |

The SDK levels live in `gradle/libs.versions.toml` (`sdk-min`,
`sdk-compile`, `sdk-target`). Only the convention plugins read them; module
build files and module manifests never set them.

The owner chose the package and minSdk 29 on 2026-11-01 (open decision #5
in `docs/00-overview.md`, "Open decisions"). ADR-0007, which records those
choices with compileSdk and targetSdk 37, is still `Proposed`.

**Why these values** (ADR-0007):

- **Application ID:** once an app is uploaded to Google Play, its
  application ID can't change; a new ID means a new store listing. The
  owner doesn't own a domain for the project. `app.aegissecurity` or
  another product-domain ID reads better but implies owning the domain, so
  the owner didn't choose it. The ID ties the listing to the owner's GitHub
  namespace, and moving to a product domain later means a new listing.
- **minSdk 29:** the lowest level where the active VPN app can call
  `ConnectivityManager.getConnectionOwnerUid` for per-app attribution, so
  per-app VPN attribution works on every supported device.
  - minSdk 26 to 28 would lose `getConnectionOwnerUid`, so per-app
    attribution for the Shield would be impossible.
  - minSdk 31 would remove the Android 10 and 11 compatibility branches,
    such as reading the Wi-Fi security type, but drops those devices. The
    owner didn't choose it.
  - Android 10 and 11 still need their compatibility branches, so CI is
    configured to run instrumented tests on API 29 and API 37 emulators
    (`docs/04-quality.md`, "CI (GitHub Actions)").
- **targetSdk 37:** since Aug 31 2026, Play requires new apps and updates
  to target API 36 or higher. Android 17 (API 37) has been stable since
  June 2026, and spikes S1 and S7 need to observe its behavior. Targeting
  37 brings its behavior changes in now, while they can still be studied.
  targetSdk 36 would meet today's Play minimum, but finding Android 17's
  changes in Phase 0 is cheaper than finding them at launch. Each year's
  Play target deadline needs a follow-up.

## Toolchain

| Tool | Version or rule | Notes | Source |
| :- | :- | :- | :- |
| JDK for the Gradle daemon | 21 | Selected by `gradle/gradle-daemon-jvm.properties`. detekt 1.23 can't start on JDK 25. Gradle never downloads a JDK | ADR-0007 |
| Compiled code | Java 17 bytecode; pure JVM modules compile with `-Xjdk-release=17` | D8 and R8 accept Java 17 bytecode. Pure JVM modules can use only the JDK 17 API. Engine modules ship inside the APK, so they mustn't use JDK APIs newer than the ones Android provides | ADR-0007 |
| Gradle | 9.8.2, Kotlin DSL | CI's `setup-gradle` validates `gradle-wrapper.jar` against Gradle's published checksums | `gradle/wrapper/gradle-wrapper.properties`, `.github/workflows/ci.yml` |
| Android Gradle Plugin | 9.5.0, with built-in Kotlin | AGP 9 compiles Kotlin itself, so Android modules don't apply `org.jetbrains.kotlin.android` | ADR-0007, `gradle/libs.versions.toml` |
| Kotlin | 2.4.21 (K2) | — | `gradle/libs.versions.toml` |
| Annotation processing | KSP | Hilt runs on it (the `aegis.hilt` convention plugin) | ADR-0007, `docs/01-architecture.md` ("Convention plugins") |
| detekt | 1.23.8, on its embedded Kotlin 2.0.21 compiler | That compiler's IntelliJ `JavaVersion` parser rejects JDK 25 version strings. detekt 2.x is still alpha. Under AGP 9, detekt 1.23 has no Android variant tasks | ADR-0007 |
| Android Studio | Latest stable | Bundles a JDK 21 (JBR), which counts only if you point Gradle at it | ADR-0007, `CONTRIBUTING.md` ("Development setup") |
| Android SDK | The compileSdk platform, `platforms;android-37.0` | Set `ANDROID_HOME`, or `sdk.dir` in the gitignored `local.properties` | `CONTRIBUTING.md` ("Development setup") |
| Device | An emulator or device running API 29 or newer | Needed for `connectedDebugAndroidTest` | `CONTRIBUTING.md` ("Development setup") |

The table's versions were read from the catalog and the wrapper on
2026-11-07. `gradle/libs.versions.toml` stays the source of truth.

**Prerequisites** (also in `CONTRIBUTING.md`, "Development setup"):

- **A JDK 21 Gradle can find.** Gradle looks at `JAVA_HOME` or the `java`
  on `PATH`, at OS-standard locations such as `/usr/lib/jvm`, and at
  SDKMAN!, asdf, and Gradle-provisioned toolchains. Android Studio's
  bundled JBR counts only if you point at it, for example with
  `export JAVA_HOME=/path/to/android-studio/jbr` or with
  `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`.
  Local builds and CI run the same JDK (CI installs Temurin 21).
- **The Android SDK** with `platforms;android-37.0`, found through
  `ANDROID_HOME` or `sdk.dir` in `local.properties`.
- **`.gitignore`** already excludes `local.properties`, keystores (`*.jks`,
  `*.keystore`), `google-services.json`, and build outputs. Keep it that
  way.

**Rules** (ADR-0007):

- **Stable releases only.** Versions are the latest stable releases at
  scaffold time. No alpha toolchain until it is needed and reviewed.
- **Every version in the catalog.** Every version lives in
  `gradle/libs.versions.toml`, and convention plugins in `build-logic/`
  apply them (`docs/01-architecture.md`, "Convention plugins").
- **Strict SHA-256 dependency verification** applies to every build, and
  repository content filters limit each repository to the groups it must
  serve. The build should download nothing a person hasn't reviewed
  (NFR-SEC-07). `docs/02-dependency-policy.md` ("Dependency verification")
  says how to regenerate the metadata.
- **Not used**, and why:
  - A daemon on JDK 25 with detekt 2.0 alpha: it would put alpha tooling
    on the main quality gate.
  - Gradle toolchain auto-provisioning (the foojay resolver): it adds a
    plugin and a JDK download at build time that nobody reviews.
  - `org.jetbrains.kotlin.android` with `android.builtInKotlin=false`: AGP 9
    deprecates that path.
- **Follow-up:** move to detekt 2.x once it is stable, then re-check the
  JDK 21 constraint.

## What Aegis needs from Android

| Need | Why | Android API or mechanism | Source |
| :- | :- | :- | :- |
| App inventory | Scanning needs every installed package (FR-SCAN-01) | `QUERY_ALL_PACKAGES` (install-time; Play's Permissions Declaration Form lists antivirus as a permitted use). `PackageInfo` with `GET_SIGNING_CERTIFICATES` (API 28+) and `GET_PERMISSIONS`, plus services, receivers, and activities. All signers and the rotation history from `signingInfo`. Granted permissions from `requestedPermissionsFlags & REQUESTED_PERMISSION_GRANTED` | `docs/features/malware-detection.md` ("Detection pipeline"), `docs/permissions-and-policy.md` ("Permission inventory") |
| Change detection | Scan new and updated apps (FR-SCAN-02) | `getChangedPackages(seq)` with a stored sequence number: on app start, every 15 min through WorkManager, and from a runtime-registered receiver while `:shield` is alive. Manifest-declared receivers for implicit package broadcasts aren't dependable on API 26+. **Verify** each action against the exemption list during implementation | `docs/01-architecture.md` ("Background execution"), `docs/roadmap.md` ("Phase 1 — Core scanner and device audit") |
| Installer source | Heuristics and the audit use it | `getInstallSourceInfo()` on API 30+, `getInstallerPackageName()` on API 29 | `docs/features/malware-detection.md` ("Detection pipeline") |
| Enabled privileged components | Heuristics, the privilege audit, and the agent guard | Services declaring `BIND_ACCESSIBILITY_SERVICE`, `BIND_NOTIFICATION_LISTENER_SERVICE`, or `BIND_DEVICE_ADMIN`, and which are enabled: `AccessibilityManager.getEnabledAccessibilityServiceList`, `NotificationManagerCompat.getEnabledListenerPackages`, `DevicePolicyManager.getActiveAdmins`, and an `AccessibilityManager` state listener while the process is alive. Apps that declare `isAccessibilityTool` and come from Play don't trigger the agent-guard alert | `docs/features/malware-detection.md` ("Detection pipeline"), `docs/features/ai-threat-defense.md` ("AI-3. Agent guard") |
| Special access held by other apps | The privilege audit (FR-AUD-02) | Only what the manifest requests: `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `MANAGE_EXTERNAL_STORAGE`, `PACKAGE_USAGE_STATS`. A third-party app can't reliably see whether another app was *granted* special access, so the audit labels these "can request" | `docs/features/device-security-audit.md` ("Privacy and privilege audit (FR-AUD-02)") |
| Uninstall | Remediation (FR-SCAN-08) | `PackageInstaller.uninstall` with `REQUEST_DELETE_PACKAGES`, a normal permission; the system shows a confirmation. Fallback: a deep link to app info. An active device admin is deactivated first, through a guided deep link | `docs/features/malware-detection.md` ("Remediation"), `docs/permissions-and-policy.md` ("Permission inventory") |
| Isolated parsing | Hostile APK, DEX, and media input (NFR-SEC-02) | A non-exported service with `android:process=":parser"` and `android:isolatedProcess="true"`: no permissions, no network access, no file-system access. Input arrives as `ParcelFileDescriptor`s over AIDL. At most 2 analyses run at once, reduced to 1 in thermal throttling or battery saver | `docs/01-architecture.md` ("Processes"), `docs/features/malware-detection.md` ("Performance") |
| Device-wide network hook | Shield DNS filter and prefix blocking (FR-NET-01) | `VpnService`, a non-root app's only device-wide network hook, in `:shield`. The system binds it through `BIND_VPN_SERVICE`; the user consents through `VpnService.prepare()`. `VpnService.protect()` keeps Aegis's own upstream sockets out of the tunnel. `addDisallowedApplication` bypasses an app | ADR-0003, `docs/features/network-protection.md` ("Design") |
| Per-app attribution | Blocked lookups per app (FR-NET-03) | `ConnectivityManager.getConnectionOwnerUid(IPPROTO_UDP, src, dst)`, API 29+, available to the active VPN app. Cached per 5-tuple for 30 s; its cost is measured in S1 | `docs/features/network-protection.md` ("Design") |
| Coexistence detection | Explain conflicts (FR-NET-05) | Another VPN: the active network has `TRANSPORT_VPN` and Aegis isn't its owner. Private DNS: `LinkProperties.isPrivateDnsActive()` and `getPrivateDnsServerName()` (API 28+) show strict mode | `docs/features/network-protection.md` ("Coexistence and settings interactions") |
| Network checks and exposure check | FR-NET-08, FR-ACS-05 | `WifiInfo.getCurrentSecurityType()` (API 31+) with `ACCESS_WIFI_STATE`. `NET_CAPABILITY_CAPTIVE_PORTAL`. `AndroidCAStore` aliases with the `user:` prefix. `ConnectivityManager.getLinkProperties(activeNetwork).linkAddresses` for a global IPv6 address (scope global, not ULA or link-local). SSID and BSSID are not read, so no location permission is needed | `docs/features/network-protection.md` ("Link checker, QR checker, and network checks"), `docs/features/ai-crawler-shield.md` ("Design") |
| Posture checks | FR-AUD-01 | `KeyguardManager.isDeviceSecure()`, `Build.VERSION.SECURITY_PATCH`, `Build.VERSION.SDK_INT` against a support table in the bundle, `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`, `Settings.Global.ADB_ENABLED`, wireless debugging from `Settings.Global` (**verify** readability), `DevicePolicyManager.getStorageEncryptionStatus()`, the Advanced Protection API (Android 16+, **verify** its permission requirements). Play Integrity `deviceIntegrity` and `playProtectVerdict` are P2: tokens must be decoded on a server (open decision #4) | `docs/features/device-security-audit.md` ("Posture checks (FR-AUD-01)") |
| Notification access | Scam detection (FR-AI-04) | A `NotificationListenerService`, protected by `BIND_NOTIFICATION_LISTENER_SERVICE`, granted by the user in Settings (opt-in) | `docs/features/ai-threat-defense.md` ("AI-4. Scam and smishing detection"), `docs/permissions-and-policy.md` ("Permission inventory") |
| Posting notifications | Alerts | `POST_NOTIFICATIONS`, a runtime permission on API 33+, asked in context. Fallback: in-app alerts with a dashboard banner | `docs/permissions-and-policy.md` ("Permission inventory") |
| Files | FR-SCAN-05, FR-SCAN-06 | `MANAGE_EXTERNAL_STORAGE` (All files access, opt-in through the special-access screen). `FileObserver` on `Download/` while Aegis is alive. The system picker (`ACTION_OPEN_DOCUMENT`) or share-to-Aegis, which need no permission | `docs/features/malware-detection.md` ("Triggers"), `docs/permissions-and-policy.md` ("Permission inventory") |
| Camera | QR checker (FR-NET-07) | `CAMERA`, a runtime permission asked when the QR scanner opens. CameraX with a bundled barcode model. Fallback: scan a QR code from an image | `docs/permissions-and-policy.md` ("Permission inventory"), `docs/features/network-protection.md` ("Link checker, QR checker, and network checks") |
| Key storage | Database keys (`core:security`) | A non-exportable AES-256-GCM key in the Android Keystore, used through Tink's `AndroidKeystore` AEAD. StrongBox when the device's StrongBox can make one, otherwise the default Keystore, which is the TEE on most devices; the security level isn't checked. Some devices create keys they can't use, most often in StrongBox, so a new key must pass an encrypt/decrypt round trip or it's deleted | `KeystoreKeyWrapper.kt` (KDoc), `docs/01-architecture.md` ("Data model") |
| Locked-device access | Scheduled scans and the Shield open their databases while the screen is locked | The Keystore key requires no user authentication and no unlocked device. The databases live in credential-encrypted storage, so nothing can read them before the first unlock anyway | `KeystoreKeyWrapper.kt` (KDoc) |
| Backup exclusion | NFR-PRIV-04 | `android:allowBackup="false"`, plus `dataExtractionRules` and `fullBackupContent` that exclude every domain. Secrets live under `noBackupFilesDir` | `.claude/rules/android-manifest-and-permissions.md`, `docs/privacy.md` ("Data inventory") |
| Sensitive screens | FR-AI-09 | `FLAG_SECURE`, and `setAccessibilityDataSensitive` on API 34+ | `docs/requirements.md` ("Functional requirements") |
| Own network traffic | NFR-SEC-04 | The Network Security Config disables cleartext traffic and trusts only system CAs | `docs/requirements.md` ("Non-functional requirements") |
| Media provenance | FR-AI-06 | `c2pa-android`, a Kotlin wrapper over the C2PA C API, minSdk 28. Parsing runs in `:parser` | `docs/features/ai-threat-defense.md` ("AI-6. Synthetic-media provenance check") |
| On-device explanation (P2, optional) | Friendlier wording of a finding | The Android ML Kit GenAI APIs, on supported devices only. A deterministic template is always the fallback | `docs/features/ai-threat-defense.md` ("Optional: on-device explanation (P2)") |
| Number reputation (P3) | FR-AI-10 | A `CallScreeningService` (the call-screening role), once a data source with suitable terms exists | `docs/features/ai-threat-defense.md` ("AI-7. Voice-clone safeguards") |

### Background work and foreground services

What runs in the background, and how: `docs/01-architecture.md`
("Background execution"). The agent guard's periodic re-check runs every
15 min (`docs/features/ai-threat-defense.md`, "AI-3. Agent guard"). After
a reboot, the Shield returns through Always-on VPN if the user has enabled
it (NFR-REL-01).

- **Foreground service types:** `FOREGROUND_SERVICE` plus the type
  permission, install-time, for the Shield and the long-running scan. The
  types are set in S1 and S4. Every `<service>` that calls
  `startForeground` declares `android:foregroundServiceType`. A
  `specialUse` service needs the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
  `<property>` with a reviewer-readable justification, which Play
  reviews (`docs/permissions-and-policy.md`, "Permission inventory";
  `.claude/rules/android-manifest-and-permissions.md`).
- **No battery-optimization exemption.** Aegis does not request
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which Play restricts. It opens
  the system battery settings with guidance for each OEM instead
  (`docs/01-architecture.md`, "Background execution").

### Limits Android sets

What a non-root, sandboxed app cannot do. Because of these limits,
Aegis's "real-time" protection is install and update scanning, DNS and
route filtering, and notification analysis, and the UI must describe it in
exactly those terms (`docs/requirements.md`, "Users and context").

- **No hook before install.** Android gives third-party apps no hook to
  scan a package before it is installed. Aegis can catch an APK *file*
  before the user opens it (with All files access); otherwise it scans
  immediately after install (`docs/features/malware-detection.md`,
  "Triggers").
- **No hooks into the file system or other processes,** no reading of call
  audio, and no TLS decryption (`docs/requirements.md`, "Users and
  context"). Play also bans call recording through accessibility
  (`docs/features/ai-threat-defense.md`, "AI-7. Voice-clone safeguards").
- **User CAs aren't trusted by default.** Apps targeting API 24+ don't
  trust user CAs by default, so TLS interception would break many apps
  anyway (ADR-0002).
- **One VPN at a time.** Android allows one active VPN, so Aegis can't run
  alongside a commercial VPN (risk R4). Starting the Shield disconnects
  another VPN, so Aegis explains that before calling
  `VpnService.prepare()` (ADR-0003;
  `docs/features/network-protection.md`, "Coexistence and settings
  interactions").
- **Routes are device-wide.** A per-app override can't be expressed with
  routes; the app goes into the disallowed-applications set, which also
  removes it from DNS filtering (`docs/features/ai-crawler-shield.md`,
  "Design").
- **Rebuilding the VPN interface** briefly re-establishes the tunnel, so
  rebuilds are debounced to at most once every 5 min unless the user
  triggered the update (`docs/features/ai-crawler-shield.md`, "Edge
  cases").
- **Private DNS can't be set by an app.** It can't be configured
  programmatically (ADR-0003). In strict mode, lookups go directly to the
  provider, so Aegis can't filter them; prefix blocking still works,
  because routes are IP-based, but DNS egress control doesn't
  (`docs/features/network-protection.md`, "Coexistence and settings
  interactions"; `docs/features/ai-crawler-shield.md`, "Edge cases").
- **No listening-port inventory.** `/proc/net` has been restricted for
  apps since Android 10, so Aegis can't enumerate other apps' listening
  ports, and the exposure check never claims "no services running"
  (`docs/features/ai-crawler-shield.md`, "Design").
- **No control over other apps' messages.** Aegis can't delete or hide
  another app's messages and doesn't try to
  (`docs/features/ai-threat-defense.md`, "AI-4. Scam and smishing
  detection").
- **No system settings changes.** The audit is advisory: Aegis cannot
  change system settings itself (`docs/features/device-security-audit.md`,
  "Summary").

## Behavior by API level

Ordered by API level. Levels below minSdk are listed when a source states
them; they apply to every supported device.

| API level | What changes for Aegis | Source |
| :- | :- | :- |
| 24+ (targeting) | Apps targeting API 24 or higher don't trust user CAs by default. That is one reason Aegis never intercepts TLS: it would break many apps | ADR-0002 |
| 26+ | Manifest-declared receivers for implicit package broadcasts aren't dependable, so change detection uses `getChangedPackages(seq)` and a runtime-registered receiver | `docs/01-architecture.md` ("Background execution") |
| 28+ | `GET_SIGNING_CERTIFICATES` is available for reading signers | `docs/features/malware-detection.md` ("Detection pipeline") |
| 28+ | `LinkProperties.isPrivateDnsActive()` and `getPrivateDnsServerName()` show whether Private DNS is in strict mode | `docs/features/network-protection.md` ("Coexistence and settings interactions") |
| 28+ | `c2pa-android` needs minSdk 28 | `docs/features/ai-threat-defense.md` ("AI-6. Synthetic-media provenance check") |
| 29 (Android 10), minSdk | `getConnectionOwnerUid` is available to the active VPN app. It's the lowest level with per-app attribution | `docs/features/network-protection.md` ("Design"), ADR-0007 |
| 29+ | The Shield sets `setMetered(false)` so the system reflects the underlying network's metered state. **Verify (S1)** | `docs/features/network-protection.md` ("Design") |
| 29+ (Android 10+) | `/proc/net` is restricted for apps, so other apps' listening ports can't be enumerated | `docs/features/ai-crawler-shield.md` ("Design") |
| 29+ | Storage encryption that isn't active is rare; the audit shows it as informational | `docs/features/device-security-audit.md` ("Posture checks (FR-AUD-01)") |
| 29 | The installer comes from `getInstallerPackageName()` | `docs/features/malware-detection.md` ("Detection pipeline") |
| 29–30 | The Wi-Fi security type isn't available; network checks show a "not available" state | `docs/features/network-protection.md` ("Link checker, QR checker, and network checks") |
| 29–30 (Android 10–11) | `KeyStore.getKey` checks the alias the same way `containsAlias` does, so a failing Keystore looks like a missing key. A `KEY_LOST` result may be a failing Keystore: confirm it on a later launch before acting on it (open decision #9). This is why the wrapping key is created only while no secret is stored: a stored secret is the proof that the key exists | `KeystoreKeyWrapper.kt` and `SecretStore.kt` (KDoc), `docs/01-architecture.md` ("Data model") |
| 29 | DEX allows spaces in names only from API 30. Instrumented tests (`src/androidTest`) therefore use camelCase sentence names, such as `launchShowsAppNameAndHonestStatus`; JVM tests (`src/test`) use backtick sentence names | `.claude/rules/tests-and-fixtures.md` |
| 30 (Android 11) | The installer comes from `getInstallSourceInfo()` | `docs/features/malware-detection.md` ("Detection pipeline") |
| 30+ (Android 11+) | The Download folder can't be granted through a SAF tree, so automatic scanning of Downloads needs All files access | `docs/threat-model.md` ("A2. Classic threats") |
| 31 (Android 12) | `WifiInfo.getCurrentSecurityType()` reports open, WEP, or otherwise weak Wi-Fi. **Verify** that the security type isn't redacted when location access is absent | `docs/features/network-protection.md` ("Link checker, QR checker, and network checks") |
| 31+ (Android 12+) | `KeyStore.getKey` returns null only for a missing key and throws otherwise (per AOSP's keystore2 `AndroidKeyStoreSpi`), so a missing key can be told apart from a failing Keystore. Aegis doesn't use Tink's `hasKey`, which is `containsAlias` and answers false on any Keystore error | `KeystoreKeyWrapper.kt` (KDoc) |
| Below 33 | `androidx.core` merges a `signature` permission, `io.github.jabbottiii.aegis.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, that protects receivers registered with `RECEIVER_NOT_EXPORTED`; Aegis grants it to itself | `docs/permissions-and-policy.md` ("Exported components and merged manifest entries") |
| 33 (Android 13) | `POST_NOTIFICATIONS` is a runtime permission | `docs/permissions-and-policy.md` ("Permission inventory") |
| 33+ | `excludeRoute` is available if a future design needs it. The current design doesn't | `docs/features/network-protection.md` ("Design") |
| 34 (Android 14) | Under the Android 14 FGS rules, `systemExempted` applies to VPN apps configured through *Settings › VPN*. S1 decides between it and `specialUse` | `docs/01-architecture.md` ("Background execution") |
| 34+ | `setAccessibilityDataSensitive` is available, and Aegis's sensitive screens call it | FR-AI-09 |
| 35+ (Android 15+) | OTP-bearing notifications are hidden from untrusted listeners. Aegis doesn't need OTPs, so that is fine. S7 studies this sensitive-notification redaction | `docs/features/ai-threat-defense.md` ("AI-4. Scam and smishing detection"), `docs/threat-model.md` ("A3. AI-specific threats"), `docs/roadmap.md` (S7) |
| 36 (Android 16) | Since Aug 31 2026, new apps and updates on Play must target API 36 or higher. Extensions to Nov 1 2026 were available on request | `docs/permissions-and-policy.md` ("Target SDK and platform") |
| 36+ (Android 16+) | The Advanced Protection API, where available, reports whether Advanced Protection is on. **Verify** its permission requirements | `docs/features/device-security-audit.md` ("Posture checks (FR-AUD-01)") |
| 37 (Android 17) | Stable since June 2026. Aegis's compileSdk and targetSdk. S1 and S7 track its behavior changes | `docs/permissions-and-policy.md` ("Target SDK and platform"), ADR-0007 |
| 37 | Advanced Protection Mode blocks accessibility access for apps that aren't accessibility tools. This doesn't affect Aegis, by design (ADR-0004). When APM is available, Aegis recommends it to high-risk users | `docs/permissions-and-policy.md` ("Target SDK and platform"), `docs/features/ai-threat-defense.md` ("AI-3. Agent guard") |
| 37 | Most apps that aren't the intended recipient or the default SMS handler get OTP SMS messages only after a delay. This doesn't affect Aegis, which reads no SMS | `docs/permissions-and-policy.md` ("Target SDK and platform") |
| 37 | A system-managed per-app VPN bypass screen. It may supersede Aegis's own bypass picker on API 37+. **Verify (S1)** | `docs/permissions-and-policy.md` ("Target SDK and platform") |
| 37 | `InputManager.getInstance` was removed, which breaks Espresso 3.5, the version Compose's UI test library pulls in. Espresso is pinned to 3.7.0 | `gradle/libs.versions.toml`, ADR-0007 |
| 37 | Emulator system images are published as `android-37.0` | `.github/workflows/ci.yml` |

## OEM behavior

These are questions, not findings. Each spike below is time-boxed and not
started; its write-up becomes an ADR or a spec update, and its row in
`docs/roadmap.md` ("Phase 0 — Foundations and technical spikes") holds the
task. Until a spike passes, the working assumption stated here holds.

| Question | Devices | Spike | Pass criteria | Until then |
| :- | :- | :- | :- | :- |
| Does a split-routed `VpnService` intercept DNS reliably, including the TCP fallback, and what does it cost in latency and battery? | 3 OEMs | S1 | p95 added DNS latency ≤ 5 ms. No connectivity loss across 24 h on 3 OEMs. A documented behavior matrix | — |
| How does the Shield behave with *Private DNS* and with *Always-on VPN* and lockdown (*Block connections without VPN*)? | 3 OEMs | S1 | Documented in the behavior matrix | Aegis recommends Always-on VPN for Aegis, but won't recommend lockdown until S1 confirms it is safe. If lockdown interferes with split routing, Aegis warns before it is enabled |
| Which foreground-service type applies to the Shield: `systemExempted` or `specialUse`? | — | S1 | The type is chosen and documented | — |
| How expensive is per-packet `getConnectionOwnerUid`? | — | S1 | Cost measured | A 30 s cache per 5-tuple; don't call it on hot paths before S1 |
| Does Android 17's system-managed per-app VPN bypass screen supersede Aegis's own bypass picker? | API 37 | S1 | Evaluated | — |
| How many routes can the VPN install (1k, 5k, 10k prefixes) on low-end and OEM devices, and what are the apply time and memory cost? | Low-end and OEM devices | S2 | A safe route cap chosen and documented | A provisional cap of 2,000 prefixes |
| When a crawler prefix is routed into the TUN, are **replies** from on-device listening sockets captured? Android's routing marks accepted sockets based on the network the incoming packet arrived on, so replies may well bypass the VPN | Pixel, Samsung, and Xiaomi with Android 13, 15, and 17 | S3 | Capture is reproducible on every device in the matrix | Inbound blocking is not provided, and the UI makes no "inbound" claim (FR-ACS-08) |
| Does the notification listener survive background kills, and how does the Android 15+ sensitive-notification redaction behave? | Pixel, Samsung, and Xiaomi | S7 | Delivery rate ≥ 99% over 48 h on each OEM, or a documented mitigation | — |

Sources: `docs/roadmap.md` ("Phase 0 — Foundations and technical
spikes"), `docs/features/network-protection.md` ("Coexistence and settings
interactions"), `docs/features/ai-crawler-shield.md` ("Design", "Inbound
traffic: spike S3", "Edge cases"), `.claude/rules/shield-vpn.md`.

S4, S5, and S6 measure Aegis's own cost rather than OEM behavior: APK and
DEX throughput inside an `isolatedProcess` service on a mid-range device,
LiteRT model size and latency, and `c2pa-android`'s size per ABI. They are
in the same roadmap phase.

What we already know about OEMs, and plan for:

- **Battery managers kill services** (risk R5, high likelihood). Aegis
  relies on the system-bound VPN service and WorkManager, and gives
  per-OEM guidance to the battery settings instead of requesting an
  exemption (`docs/00-overview.md`, "Top risks and mitigations"). The
  device matrix includes Xiaomi (HyperOS) and Samsung (One UI) for this
  (`docs/04-quality.md`, "Device matrix").
- **Settings screens differ.** Every audit recommendation deep-links to the
  exact settings screen where an intent exists, and otherwise gives
  step-by-step text with OEM variants
  (`docs/features/device-security-audit.md`, "Scoring and presentation").
- **OS support differs by manufacturer.** The OS-support check compares
  `Build.VERSION.SDK_INT` with a support table in the bundle, best effort
  (`docs/features/device-security-audit.md`, "Posture checks
  (FR-AUD-01)").
- **System apps differ.** The benign corpus includes system apps from at
  least three OEM images (`docs/04-quality.md`, "Detection quality").
- **StrongBox varies.** Some devices create keys they can't use, most
  often in StrongBox; the round-trip check above handles it
  (`KeystoreKeyWrapper.kt`).

## Play policy constraints

Google Play shapes what Aegis may request and how. The full detail is in
`docs/permissions-and-policy.md`. Policies change, so re-check every Play
policy reference before submission.

- **Target API level.** Since Aug 31 2026, new apps and updates must target
  Android 16 (API 36) or higher; extensions to Nov 1 2026 were available on
  request. targetSdk never falls below Play's current minimum
  (NFR-COMP-01), and each year's deadline needs a follow-up (ADR-0007).
- **Declarations.**
  - `QUERY_ALL_PACKAGES` and `MANAGE_EXTERNAL_STORAGE` each need the
    Permissions Declaration Form; antivirus is a listed permitted use for
    both.
  - `VpnService` needs the Play Console VpnService declaration form, with
    a demo video of 90 s or less and a separate video of the disclosure and
    consent flow, re-submitted whenever VpnService usage changes. Device
    security apps (for example, anti-virus, mobile device management,
    firewall) are an eligible category. The store listing must document
    the VPN use. The policy prohibits collecting personal or sensitive data
    without prominent disclosure and consent, and redirecting or
    manipulating other apps' traffic for monetization; the Shield never
    manipulates ad or monetization traffic
    (`docs/permissions-and-policy.md`, "VpnService";
    `docs/features/network-protection.md`, "Play policy").
  - A `specialUse` foreground service needs a manifest `<property>`
    justification, which Play reviews.
- **The VPN encryption clause.** VPN apps "must encrypt the data from the
  device to the VPN tunnel endpoint". Aegis's tunnel ends on the device,
  and forwarded DNS uses DoH or DoT; the declaration says so explicitly
  (risk R2).
- **Prominent disclosures.** Each is a separate screen shown immediately
  before the system prompt. The VPN disclosure must be separate from the
  others (`docs/permissions-and-policy.md`, "Prominent disclosures").
- **Restricted, so not requested** (`docs/permissions-and-policy.md`,
  "Deliberately not requested"):
  - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  - `READ_SMS`, `RECEIVE_SMS`, and the call log: Play limits these to
    default handlers and narrow exceptions, and the anti-smishing exception
    requires "a track record of significant protection".
  - `BIND_ACCESSIBILITY_SERVICE`: Play's accessibility policy restricts
    non-accessibility-tool use (ADR-0004).
- **Distribution.** New **personal** developer accounts must complete a
  closed test (a minimum number of testers over a minimum period; confirm
  the current numbers in Play Console) before production. Outside Play,
  Google's developer verification for apps installed on certified Android
  devices takes effect on Sept 30 2026 in Brazil, Indonesia, Singapore,
  and Thailand, and expands globally in 2027. Apps from unregistered
  developers need an "advanced" sideload flow (with a mandatory waiting
  period) or ADB, so Aegis registers before any non-Play release (risk
  R11; `docs/permissions-and-policy.md`, "Distribution").
