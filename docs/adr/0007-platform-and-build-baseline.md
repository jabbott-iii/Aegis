# ADR-0007: Platform and build baseline

- Status: Proposed
- Date: 2026-11-01
- Related: NFR-COMP-01, NFR-SEC-07, [PLAN §6 #5](../PLAN.md#6-open-decisions), [PERMISSIONS_AND_POLICY §4](../PERMISSIONS_AND_POLICY.md#4-target-sdk-and-platform), [DEVELOPMENT § Prerequisites](../DEVELOPMENT.md#prerequisites)

## Context

The Phase 0 scaffold has to fix several values that are expensive or impossible to change later:

- **Application ID.** Once an app is uploaded to Google Play, its application ID can't change. A new ID means a new store listing. The owner doesn't currently own a domain for the project. The owner chose the ID and minSdk on 2026-11-01.
- **minSdk** is open decision #5 in PLAN §6. API 29 is the lowest level where the active VPN app can call `ConnectivityManager.getConnectionOwnerUid` for per-app attribution (ARCHITECTURE §5.2, network-protection spec). Raising minSdk to 31 removes Android 10 and 11 compatibility branches, such as reading the Wi-Fi security type, but drops those devices.
- **targetSdk.** Since Aug 31 2026, Play has required new apps and updates to target API 36 or higher (PERMISSIONS_AND_POLICY §4). Android 17 (API 37) has been stable since June 2026, and spikes S1 and S7 need to observe its behavior.
- **Build toolchain.** AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the `org.jetbrains.kotlin.android` plugin is a deprecated path. The latest stable detekt (1.23.8) runs an embedded Kotlin 2.0.21 compiler. That compiler's IntelliJ `JavaVersion` parser rejects JDK 25 version strings, so detekt fails to start on JDK 25. detekt 2.x is still alpha. Android Studio bundles a JDK 21 (JBR). D8 and R8 accept Java 17 bytecode, and engine modules ship inside the APK, so they mustn't use JDK APIs newer than the ones Android provides.
- **Supply chain** (NFR-SEC-07, THREAT_MODEL B3): dependencies must be pinned and verified, and the build should download nothing that a person hasn't reviewed.

## Decision

1. The application ID and base package are **`io.github.jabbottiii.aegis`**. Module packages are `io.github.jabbottiii.aegis.<area>.<name>`. Android library namespaces come from the Gradle path, and `:app` uses the base package.
2. **minSdk 29. compileSdk 37 and targetSdk 37** (`platforms;android-37.0`). Only the convention plugins read these values, from `gradle/libs.versions.toml`.
3. The **Gradle daemon runs on JDK 21**, selected by `gradle/gradle-daemon-jvm.properties`. Gradle never downloads a JDK, so a JDK 21 must be installed where Gradle can find it. All modules compile to **Java 17 bytecode**, and pure JVM modules compile with `-Xjdk-release=17`, so they can use only the JDK 17 API.
4. **AGP 9 with built-in Kotlin**, KSP for annotation processing, and convention plugins in `build-logic/`. Every version lives in `gradle/libs.versions.toml`. **Strict SHA-256 dependency verification** applies to every build, and repository content filters limit each repository to the groups it must serve.
5. Versions are the latest stable releases at scaffold time. Tools stay on stable releases: no alpha toolchain until it is needed and reviewed.

## Consequences

- Per-app VPN attribution is available on every supported device. Android 10 and 11 still need their compatibility branches, and CI runs instrumented tests on API 29 and API 37 emulators.
- The application ID ties the listing to the owner's GitHub namespace. Moving to a product domain later means a new listing.
- Targeting API 37 brings Android 17 behavior changes in now, while S1 and S7 can still study them. Espresso is pinned to 3.7.0 because the version Compose pulls in (3.5) breaks on API 37. Each year's Play target deadline needs a follow-up.
- Developers need a JDK 21 that Gradle can find: on `JAVA_HOME` or `PATH`, in an OS-standard location, or pointed at explicitly (Android Studio's JBR). Local builds and CI run the same JDK.
- Under AGP 9, detekt 1.23 has no Android variant tasks, and CI's plain `detekt` has no type resolution. Rules that need type resolution don't run. `checkSourceRules` covers the critical ones: logging, dispatchers, `GlobalScope`, `runBlocking`, and engine `!!`. Follow-up: move to detekt 2.x once it is stable, then re-check the JDK 21 constraint.
- Every dependency change regenerates `gradle/verification-metadata.xml`, and someone reviews the diff. Dependabot PRs fail until that happens, which is intended. The macOS and Windows `aapt2` hashes are added by hand for each AGP version.

## Alternatives considered

- **`app.aegissecurity` or another product-domain ID.** It reads better, but it implies owning the domain, so the owner didn't choose it.
- **minSdk 31.** Fewer compatibility branches, but it drops Android 10 and 11. The owner didn't choose it.
- **minSdk 26 to 28.** These lose `getConnectionOwnerUid` (API 29), so per-app attribution for the Shield would be impossible.
- **targetSdk 36.** It meets today's Play minimum, but Android 17 is stable, and finding its behavior changes in Phase 0 is cheaper than finding them at launch.
- **Daemon on JDK 25 with detekt 2.0 alpha.** Rejected: it puts alpha tooling on the main quality gate.
- **Gradle toolchain auto-provisioning (foojay resolver).** Rejected for now: it adds a plugin and a JDK download at build time that nobody reviews.
- **`org.jetbrains.kotlin.android` with `android.builtInKotlin=false`.** Rejected: AGP 9 deprecates that path.
