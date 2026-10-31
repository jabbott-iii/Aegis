---
paths:
  - "**/AndroidManifest.xml"
  - "**/src/main/res/xml/**"
---

# Manifest, permissions, and platform configuration

The source of truth is `docs/PERMISSIONS_AND_POLICY.md`. Read it before you touch these files.

- **Every `<uses-permission>`** must already appear in the permission inventory, with its feature, when it is requested, its Play requirement, and its fallback. Otherwise run `/add-permission` first.
- **Deliberately not requested:** `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, call-log permissions, location, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `PACKAGE_USAGE_STATS`, `READ_MEDIA_*`, and any AccessibilityService. The guard hook asks before adding these. Adding one requires an ADR change.
- **Exported components:** only the share or VIEW intake activity, the VpnService (protected by `android.permission.BIND_VPN_SERVICE`), and the NotificationListenerService (protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`). Anything else exported needs a THREAT_MODEL update.
- **Backup:** `android:allowBackup="false"`, plus `dataExtractionRules` and `fullBackupContent` that exclude every domain (NFR-PRIV-04).
- **Network Security Config:** cleartext disabled and system CAs only. Never `<certificates src="user"/>` and never `cleartextTrafficPermitted="true"` (NFR-SEC-04).
- **Foreground services:** every `<service>` that calls `startForeground` declares `android:foregroundServiceType`. The VPN type is decided by spike S1 (`systemExempted` vs. `specialUse`). A `specialUse` service needs the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` `<property>` with a reviewer-readable justification.
- **Processes:** `AegisVpnService` runs with `android:process=":shield"`. The parser service uses `android:process=":parser"` and `android:isolatedProcess="true"`, and is not exported.
- **SDK levels** come from convention plugins, never from module manifests: minSdk 29 (proposed), targetSdk at least 36.
- **Fixtures exception:** manifests under `testing/fixtures/` deliberately declare risky permissions and components so that they emulate malware profiles. They must stay benign. Never copy a fixture manifest into a production module.
