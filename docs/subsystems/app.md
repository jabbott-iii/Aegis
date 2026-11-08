# Subsystem: `app` — application shell, DI root, manifest

The Android application module: the `Application` class, the launcher
activity, the Hilt DI root, and the manifest that every library merges into
(`docs/01-architecture.md`, "Module layout"). It also holds Android adapters
that the pure modules can't, such as the logcat sink. Its namespace and
applicationId are the base package `io.github.jabbottiii.aegis` (ADR-0007).
Phase 0 built the shell that exists today: one screen that says plainly that
no protection runs yet, the DI bindings for the logger, the clock and the
dispatchers, and the backup, data-extraction and network security
configuration. There is no navigation graph yet. The dashboard
(`feature:dashboard`, `features.md`) replaces the shell screen in Phase 1.

## Contents

| Package or file | Provides |
| :- | :- |
| `AegisApplication` | The `@HiltAndroidApp` application class |
| `MainActivity` | The launcher activity (`@AndroidEntryPoint`). It enables edge-to-edge drawing and shows `AegisApp` inside `AegisTheme` |
| `ui` | `AegisApp`, the Phase 0 shell: the app name as a heading and one `StatusCard` titled "Protection" at `StatusLevel.OFF`, saying that this is an early development build and that app scanning, network protection, and AI threat checks aren't available yet. Previews in light, dark, and 200% font scale |
| `di` | `CommonModule` (the `AegisLogger` and the `Clock`) and `DispatchersModule` (the platform `AegisDispatchers`) |
| `logging` | `LogcatSink`, the logging facade's only Android adapter |
| `src/main/AndroidManifest.xml` | The application entry, the launcher activity, and the backup, data-extraction and network security settings |
| `res/xml` | `backup_rules.xml`, `data_extraction_rules.xml`, `network_security_config.xml` |
| `res/values`, `res/values-night` | The app name and shell strings, the window theme, the launcher icon background color |
| `proguard-rules.pro` | R8 rules for the release build (NFR-SEC-05) |

## DI bindings

Every binding is a `@Singleton` in `SingletonComponent`.

- **Logger:** `CommonModule` provides a `PolicyLogger` that writes to
  `LogcatSink`. It uses `LogPolicy.DEBUG` when `BuildConfig.DEBUG` is set
  and `LogPolicy.RELEASE` otherwise, so release builds log nothing at INFO
  or below and no free text (`core-common-and-model.md`).
- **Clock:** `CommonModule` provides `Clock.systemUTC()` as a
  `java.time.Clock`. Code takes the clock by injection, and tests use
  `MutableClock` (`testing.md`).
- **Dispatchers:** `DispatchersModule` binds `AegisDispatchers` to
  `Dispatchers.Default`, `Dispatchers.IO` and `Dispatchers.Main`. It is the
  one file that may name `Dispatchers.*`; `./gradlew checkSourceRules` fails
  on the name anywhere else.
- **Library modules** contribute their own Hilt modules: `SecurityModule`
  (`core-security.md`) and `DataModule` (`core-data.md`).

## Logcat sink

`LogcatSink` writes what a `LogRecord` already contains and adds nothing.
The policy has removed free text in release builds before a record gets
here.

- **Line format:** the event, then ` values=` and the values joined by
  commas if there are any, then ` error=` and the exception class name if
  there is one, then the debug text on a new line if the policy allowed it.
- **Levels:** `DEBUG`, `INFO`, `WARN` and `ERROR` go to `Log.d`, `Log.i`,
  `Log.w` and `Log.e`, with the tag `Aegis`.
- **The only exception to the logging rules.** It is the only file allowed
  to import `android.util.Log`: detekt's `ForbiddenImport` rule excludes it,
  and `checkSourceRules` exempts it from the logging patterns (`AGENTS.md`
  rule 8).
- **Defense in depth:** R8 removes any `Log.v`, `Log.d` or `Log.i` call
  that remains in a release build (`-assumenosideeffects` in
  `proguard-rules.pro`), on top of `LogPolicy.RELEASE`
  (`docs/privacy.md`, "Logging").

## Manifest and platform configuration

- **No permissions.** The app's own manifest requests none. Libraries merge
  in one signature-level self permission. Every new `<uses-permission>` and
  every exported component must appear in `docs/permissions-and-policy.md`
  first ("Exported components and merged manifest entries"), through
  `/add-permission` (`AGENTS.md` rule 11).
- **Exported surface.** `MainActivity` is the launcher entry point. The
  platform requires it to be exported, and it accepts no data or extras.
  Release builds also export the library-merged `ProfileInstallReceiver`,
  which `android.permission.DUMP` guards. Debug builds also export Compose's
  `PreviewActivity` and the UI-test host activity
  (`docs/01-architecture.md`, "Security architecture of Aegis itself").
- **No backup or transfer** (NFR-PRIV-04):
  - `android:allowBackup="false"`.
  - `fullBackupContent` (`backup_rules.xml`, Android 10 and 11) excludes
    every domain: `root`, `file`, `database`, `sharedpref`, `external`,
    `device_root`, `device_file`, `device_database` and
    `device_sharedpref`.
  - `dataExtractionRules` (`data_extraction_rules.xml`, Android 12+)
    excludes the same domains from both cloud backup and device-to-device
    transfer. `allowBackup="false"` alone doesn't stop device-to-device
    transfer on Android 12+, so every domain is excluded explicitly.
- **Network Security Config** (`network_security_config.xml`, NFR-SEC-04,
  ADR-0002): cleartext traffic is not permitted, and the only trust anchors
  are the system CAs. User-installed CAs are never trusted for Aegis's own
  connections, in any build type.
- **Layout and theme:** `supportsRtl="true"` (NFR-I18N-01). `Theme.Aegis`
  is a window theme only (the platform's Material light theme without an
  action bar, and its dark variant under `values-night`); Compose draws the
  UI with `AegisTheme` from `core:ui`.
- **Launcher icon:** an adaptive icon with a monochrome layer. The
  foreground is the Material Icons "shield" (Apache-2.0), scaled into the
  adaptive-icon safe zone, on a `#FF1F5E8C` background.

## Build

- **Plugins:** `aegis.android.application`, `aegis.android.compose` and
  `aegis.hilt`. `aegis.android.application` refuses any module but `:app`.
- **What the application plugin sets:**
  - The namespace and applicationId `io.github.jabbottiii.aegis`, and the
    SDK levels from `gradle/libs.versions.toml` (minSdk 29, compileSdk and
    targetSdk 37).
  - `versionCode` and `versionName` from `aegis.versionCode` and
    `aegis.versionName` in `gradle.properties`, and `BuildConfig`.
  - Release builds are R8-minified, with resource shrinking and
    `proguard-rules.pro`.
  - Release signing comes from the `AEGIS_SIGNING_STORE_FILE`,
    `AEGIS_SIGNING_STORE_PASSWORD`, `AEGIS_SIGNING_KEY_ALIAS` and
    `AEGIS_SIGNING_KEY_PASSWORD` environment variables. Without the store
    file the release build is unsigned; with it, a missing one of the other
    three fails the build. Key material never lives in the repo
    (`docs/05-release.md`, "Signing and secrets").
  - Lint checks dependencies too (`checkDependencies`).
- **Dependencies:** `core:common`, `core:data` and `core:ui`, plus AndroidX
  `core-ktx`, `activity-compose` and `kotlinx-coroutines-android`.

## Planned

- **Navigation graph** across the feature modules
  (`docs/01-architecture.md`, "Module layout").
- **Share and VIEW entry points.** Activities that accept share or VIEW
  intents validate the MIME type and size, and copy content into the
  sandbox before parsing (`docs/01-architecture.md`, "Security architecture
  of Aegis itself"). The untrusted-input rules
  (`.claude/rules/untrusted-input-parsers.md`) cover
  `app/src/main/**/share/**`. The specs that take shared input include the
  single-file scan (FR-SCAN-06), the link checker (FR-NET-06), shared text
  (AI-4, AI-5) and shared media (AI-6).
- **Android adapters.** Platform calls that the pure modules need sit behind
  interfaces whose adapters live in `engine:scanner`, `shield:vpn` or `app`
  (`.claude/rules/kotlin-engine.md`).
- **Baseline profile** in Phase 7, for the cold-start budget (NFR-PERF-03:
  ≤ 1.0 s to an interactive dashboard).

## Tests

- `MainActivitySmokeTest` (instrumented): launching shows the app name, the
  status label "Off", and the shell description. The shell runs no
  protection, so its status must read "Off", never a protected state.
- Instrumented test names can't contain spaces: DEX allows them only from
  API 30, and minSdk is 29.
- CI is configured to run the instrumented smoke tests on API 29 and API 37
  emulators (`docs/04-quality.md`, "CI (GitHub Actions)"). They haven't
  completed on GitHub yet, because every CI run so far failed dependency
  verification (`docs/roadmap.md`, P0.3). Locally they passed on API 37
  only.
