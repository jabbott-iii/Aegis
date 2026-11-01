---
paths:
  - "**/AndroidManifest.xml"
  - "**/src/main/res/xml/**"
---

# Manifest, permissions, and platform configuration

The source of truth is `docs/PERMISSIONS_AND_POLICY.md`. Read it before you touch these files.

- **Every `<uses-permission>`** must already appear in the permission inventory, with its feature, when it is requested, its Play requirement, and its fallback. Otherwise run `/add-permission` first.
- **Deliberately not requested:** `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, call-log permissions, location, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `PACKAGE_USAGE_STATS`, `READ_MEDIA_*`, and any AccessibilityService. The guard hook asks before adding these. Adding one requires an ADR change.
- **Exported components:** only these (NFR-SEC-03: each one has a permission, validates its input explicitly, or reads no intent data):
  - Exported in today's merged release manifest (PERMISSIONS_AND_POLICY § Exported components and merged manifest entries):
    - the launcher `MainActivity` (`MAIN`/`LAUNCHER`), which reads no intent data or extras
    - the library-merged `androidx.profileinstaller.ProfileInstallReceiver`, protected by `android.permission.DUMP`
  - Planned (ARCHITECTURE §8, Exported surface):
    - the share or VIEW intake activity, which validates MIME type and size and copies content into the sandbox. APK, DEX, and media content is parsed only in `:parser` (THREAT_MODEL B3). Shared text and links are analyzed in memory with size limits (THREAT_MODEL B2).
    - the VpnService, protected by `android.permission.BIND_VPN_SERVICE`
    - the NotificationListenerService, protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`

  Debug builds also merge the exported `PreviewActivity` (from `ui-tooling`) and `ComponentActivity` (the Compose test host). They must never reach release. Every component that becomes exported, whether planned or not, goes through `/add-permission` in the same change. That adds its row in that PERMISSIONS_AND_POLICY section, its disclosure and fallback, and its THREAT_MODEL B3 row (CLAUDE.md non-negotiable #11, THREAT_MODEL B4). Anything not in the list above also needs the user's agreement first. Libraries can merge components and permissions, so re-check the merged release manifest whenever a dependency changes.
- **Backup:** `android:allowBackup="false"`, plus `dataExtractionRules` and `fullBackupContent` that exclude every domain (NFR-PRIV-04).
- **Network Security Config:** cleartext disabled and system CAs only. Never `<certificates src="user"/>` and never `cleartextTrafficPermitted="true"` (NFR-SEC-04).
- **Foreground services:** every `<service>` that calls `startForeground` declares `android:foregroundServiceType`. The VPN type is decided by spike S1 (`systemExempted` vs. `specialUse`). A `specialUse` service needs the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` `<property>` with a reviewer-readable justification.
- **Processes:** `AegisVpnService` runs with `android:process=":shield"`. The parser service uses `android:process=":parser"` and `android:isolatedProcess="true"`, and is not exported.
- **SDK levels** come from the version catalog through the convention plugins, never from module manifests: minSdk 29 (the owner's choice, PLAN §6 #5), and compileSdk and targetSdk 37 (ADR-0007, still Proposed). Play requires targetSdk 36 or higher (PERMISSIONS_AND_POLICY §4), so never go below that.
- **Fixtures exception:** manifests under `testing/fixtures/` deliberately declare risky permissions and components so that they emulate malware profiles. They must stay benign. Never copy a fixture manifest into a production module.
