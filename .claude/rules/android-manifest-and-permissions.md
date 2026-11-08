---
paths:
  - "**/AndroidManifest.xml"
  - "**/src/main/res/xml/**"
---

# Rules for the manifest, permissions, and platform configuration

The source of truth is `docs/permissions-and-policy.md`. Read it before you
touch these files.

- **Every `<uses-permission>` is in the inventory first.** It must already
  appear in `docs/permissions-and-policy.md` ("Permission inventory"), with
  its feature, when it is requested, its Play requirement, and its fallback.
  Otherwise run `/add-permission` first.
- **Deliberately not requested:** `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`,
  call-log permissions, location, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
  `PACKAGE_USAGE_STATS`, `READ_MEDIA_*`, and any AccessibilityService
  (`docs/permissions-and-policy.md`, "Deliberately not requested").
  - The guard hook asks before adding these.
  - Adding one requires an ADR change (`docs/decisions.md`).
- **Exported components: only these.** Under NFR-SEC-03, each one has a
  permission, validates its input explicitly, or reads no intent data.
  - **Exported in today's merged release manifest**
    (`docs/permissions-and-policy.md`, "Exported components and merged
    manifest entries"):
    - the launcher `MainActivity` (`MAIN`/`LAUNCHER`), which reads no intent
      data or extras
    - the library-merged `androidx.profileinstaller.ProfileInstallReceiver`,
      protected by `android.permission.DUMP`
  - **Planned** (`docs/01-architecture.md`, "Security architecture of Aegis
    itself"):
    - the share or VIEW intake activity, which validates MIME type and size
      and copies content into the sandbox. APK, DEX, and media content is
      parsed only in `:parser` (`docs/threat-model.md` B3). Shared text and
      links are analyzed in memory with size limits
      (`docs/threat-model.md` B2).
    - the VpnService, protected by `android.permission.BIND_VPN_SERVICE`
    - the NotificationListenerService, protected by
      `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`
  - **Debug builds** also merge the exported `PreviewActivity` (from
    `ui-tooling`) and `ComponentActivity` (the Compose test host). They must
    never reach release.
  - **Every component that becomes exported,** whether planned or not, goes
    through `/add-permission` in the same change. That adds its row in that
    section of `docs/permissions-and-policy.md`, its disclosure and
    fallback, and its `docs/threat-model.md` B3 row (`AGENTS.md` rule 11,
    `docs/threat-model.md` B4).
  - **Anything not in the list above** also needs the user's agreement
    first.
  - **Libraries can merge components and permissions,** so re-check the
    merged release manifest whenever a dependency changes.
- **Backup is off.** `android:allowBackup="false"`, plus
  `dataExtractionRules` and `fullBackupContent` that exclude every domain
  (NFR-PRIV-04).
- **Network Security Config:** cleartext disabled and system CAs only.
  Never `<certificates src="user"/>` and never
  `cleartextTrafficPermitted="true"` (NFR-SEC-04).
- **Foreground services declare a type.** Every `<service>` that calls
  `startForeground` declares `android:foregroundServiceType`.
  - The VPN type is decided by spike S1 (`systemExempted` vs.
    `specialUse`).
  - A `specialUse` service needs the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
    `<property>` with a reviewer-readable justification.
- **Processes:** `AegisVpnService` runs with `android:process=":shield"`.
  The parser service uses `android:process=":parser"` and
  `android:isolatedProcess="true"`, and is not exported.
- **SDK levels come from the version catalog** through the convention
  plugins, never from module manifests.
  - minSdk 29, the owner's choice (`docs/00-overview.md`, "Open decisions"
    #5).
  - compileSdk and targetSdk 37 (ADR-0007, still Proposed).
  - Play requires targetSdk 36 or higher (`docs/permissions-and-policy.md`,
    "Target SDK and platform"), so never go below that.
- **Fixtures exception:** manifests under `testing/fixtures/` deliberately
  declare risky permissions and components so that they emulate malware
  profiles.
  - They must stay benign.
  - Never copy a fixture manifest into a production module.
