# 02 — Dependency policy

Aegis builds on the Android platform, AndroidX and other open-source
libraries. Dependencies are allowed under the rule below. This file says
what the rule covers, what to check before adding a dependency, and how the
build enforces it. Third-party data has its own record in
`docs/data-sources.md`.

## The rule

> **Every dependency is declared in the version catalog, pinned by
> dependency verification, under a license on the allowlist, and free of
> analytics, ads and attribution code** (NFR-SEC-07, NFR-PRIV-02). Prefer
> platform or AndroidX APIs to a new library.

1. **Version catalog only.** Every dependency and plugin is declared in
   `gradle/libs.versions.toml`, with its version. Module build files never
   write inline `"group:artifact:version"` strings, and never declare file
   dependencies, which bypass the catalog and dependency verification.
2. **Convention plugins own the build settings.** The plugins in
   `build-logic/` own the SDK levels, the Kotlin and Compose settings, lint,
   detekt and test options. Module build files apply plugins and declare
   dependencies, nothing more. The SDK levels are catalog versions that only
   the convention plugins read (`docs/01-architecture.md`, "Convention
   plugins").
3. **Pinned and verified.** Gradle dependency verification checks every
   artifact on every Gradle run ("Dependency verification").
4. **Allowed licenses only** ("License allowlist").
5. **No tracking SDKs, and no SDK that makes network calls Aegis doesn't
   control** ("Forbidden dependencies").
6. **Restricted libraries stay in their modules** ("Restricted libraries"
   below).
7. **Never weaken a gate to get a build green.**
   - Never disable dependency verification or the license check.
   - Never add `-x test`, `lint { abortOnError = false }`, detekt
     baselines, or `@Suppress` to get a change through without the user's
     explicit approval.
   - The quality gates aren't configurable per module: never add
     baselines, suppressions or `ignoreFailures` in `QualityConventions.kt`.

### Restricted libraries

Some library groups may be used only by the modules listed for them,
directly or transitively, outside tests. The list is
`ModuleGraphRules.RESTRICTED_GROUPS` in `ModuleGraph.kt`
(`build-logic/convention/`), and `checkModuleGraph` enforces it.

| Library group | Allowed only in | Why |
| :- | :- | :- |
| `com.google.crypto.tink` | `:core:security` | Only `core:security` touches the Keystore and Tink |
| `com.squareup.okhttp3` | `:intel:client`, `:shield:dns` | Only `intel:client` does network I/O for bundles. `shield:dns` owns the DoH upstream |

- On a compile classpath, a restricted group shows up only if some module
  exposes it as `api`, and that leak is itself a violation. On a runtime
  classpath, reaching the group through an allowed module is fine.
- `intel:client` (Phase 2) and `shield:dns` (Phase 3) don't exist yet. The
  rule is in place before them.
- The pure Kotlin/JVM modules (`engine:apk`, `engine:signatures`,
  `engine:heuristics`, `shield:dns`, `core:model`, `core:common`) use no
  Android library outside tests: no group under `androidx`, `com.android`,
  `com.google.android` or `org.robolectric`, and no Android module.
- `feature:*` modules never depend on each other.

The full module rules are in `docs/01-architecture.md` ("Dependency
rules"). Keep the module-graph check passing.

## What counts as a dependency

| Kind | How the policy applies |
| :- | :- |
| A library, shipped or test-only, including a BOM | Declared in the catalog's `[libraries]` and checked by dependency verification. Shipped libraries are also checked against the license allowlist |
| A Gradle plugin | Declared in the catalog and verified like any library. The plugins `build-logic` uses are `[libraries]` entries, because they are implementation dependencies of the convention plugins, so module build files apply them only through the `aegis.*` plugin IDs |
| A file dependency | Not allowed in a declared configuration. `checkModuleGraph` fails, because a file dependency bypasses the version catalog and dependency verification. Use a catalog alias. AGP and Hilt add file dependencies to their own configurations, which the check doesn't count |
| Code bundled inside an artifact | licensee can't see it. Check its license by hand and cover it in the open-source notices, as for SQLCipher ("The SQLCipher exception") |
| Manifest entries a library merges in | Part of adding the library. A merged permission or exported component updates `docs/permissions-and-policy.md` ("Exported components and merged manifest entries") in the same change (`AGENTS.md` rule 11) |
| A GitHub Action | Pinned by commit SHA. Dependabot proposes updates ("Updates") |
| Third-party data | Not a build dependency, but it has terms of its own ("Third-party data") |

### Third-party data

IOC feeds, blocklists, published crawler IP ranges, curated registries and
ML training datasets are listed in `docs/data-sources.md`, with their terms
and the date each was verified, before the intel pipeline ingests them.
Onboard a new source with `/add-intel-source`. *About › Data sources*
lists every shipped source with its license, and any CC-BY source must
appear there (FR-INTEL-05). Malware samples and raw training data never
enter this repository (`AGENTS.md` rule 12).

## Forbidden dependencies

- **Analytics, advertising, attribution and marketing SDKs** (NFR-PRIV-02,
  `AGENTS.md` rule 7).
- **Any SDK that makes network calls Aegis doesn't control.** By default
  the only traffic Aegis originates is intel downloads and the DNS upstream
  (NFR-PRIV-01). OkHttp is the only HTTP stack (`docs/01-architecture.md`,
  "Technology stack"), and only the modules in "Restricted libraries" may
  use it.
- **Crash reporting, unless the owner agrees first.** NFR-PRIV-02 allows it
  only as opt-in. It would need a `docs/privacy.md` update and a change to
  the Data safety mapping (`docs/permissions-and-policy.md`, "Data safety
  form"), so ask before adding one.

When Claude Code writes a `.gradle.kts`, `.gradle` or `.toml` file, the
`pre_tool_guard.py` hook matches these patterns, ignoring case:

| Patterns | Decision |
| :- | :- |
| `firebase-analytics`, `firebase-perf`, `play-services-ads`, `play-services-analytics`, `play-services-measurement`, `com.appsflyer`, `com.adjust.sdk`, `io.branch.sdk`, `com.facebook.android`, `com.mixpanel`, `com.amplitude`, `com.segment.analytics`, `com.flurry`, `com.applovin`, `com.unity3d.ads`, `com.ironsource`, `com.chartboost`, `com.google.android.ump` | Deny: an analytics, ads or attribution SDK, which NFR-PRIV-02 forbids |
| `firebase-crashlytics`, `io.sentry`, `com.bugsnag`, `com.instabug`, `ch.acra` | Ask: a crash-reporting SDK needs opt-in, a `docs/privacy.md` update and a Data safety update |

The patterns are a guardrail, not the rule. An SDK the hook doesn't list
is still forbidden if it falls in a category above.

## Before adding a dependency

1. **Look for a platform or AndroidX API first.**
2. **Check four things:**
   - a license on the allowlist, for the artifact and for anything bundled
     inside it
   - active maintenance
   - transitive size
   - any manifest permissions it merges in
3. **Pick a stable release.** Use current stable releases and avoid alphas
   in `engine:*` (`docs/01-architecture.md`, "Technology stack"). Tools
   stay on stable releases too: no alpha toolchain until it is needed and
   reviewed (ADR-0007). Candidates that the stack names, such as dexlib2
   (spike S4), get the same license check.
4. **Check where it may go.** A pure module can't take an Android library,
   and a restricted group stays in its modules ("Restricted libraries").
5. **Declare it in `gradle/libs.versions.toml`** and reference the catalog
   alias from the module build file.
6. **Regenerate `gradle/verification-metadata.xml`** in the same change and
   review its diff ("Dependency verification").
7. **Update the docs in the same change** if the library merges in a
   permission or an exported component (`/add-permission`,
   `docs/permissions-and-policy.md`).
8. **Run the checks** (`./gradlew licensee checkModuleGraph`, then the
   build), and report what you ran and what it printed.

## License allowlist

Shipped dependencies may use only these licenses (`ALLOWED_SPDX` in
`QualityConventions.kt`, `build-logic/convention/`):

- `Apache-2.0`
- `MIT`
- `BSD-2-Clause`
- `BSD-3-Clause`

Adding a license needs the owner's approval.

- Every module convention plugin applies Cash App's licensee (`licensee`
  1.14.1 in the catalog), alongside detekt and ktlint.
- `./gradlew licensee` fails on a license outside the allowlist. CI runs it
  on every PR.
- The allowlist is project-wide, so most modules use only part of it, and
  licensee ignores the unused entries.

### The SQLCipher exception

`net.zetetic:sqlcipher-android` is the one exception. Its POM names only a
license URL, so licensee can't match it to an SPDX ID. Its LICENSE file
(`github.com/sqlcipher/sqlcipher-android`) is BSD-3-Clause.

- The owner approved allowing exactly the version that was checked
  (2026-11-01). `allowDependency` in `QualityConventions.kt` pins the
  exception to `sqlcipher-license-checked` in the catalog, 4.20.0 today,
  the same as `sqlcipher`.
- When you upgrade SQLCipher, re-read its LICENSE, then bump that entry.
  Until then, licensee fails.
- BSD-3-Clause also requires the copyright notice in the app's
  documentation, so include it in the open-source notices before release
  (Phase 7, "Open-source notices" below).
- The AAR also bundles code that licensee can't see:
  - SQLite (public domain)
  - libtomcrypt, as the crypto provider (license still to be checked)
  - a Java layer forked from AOSP (probably Apache-2.0)

### Open-source notices

**Planned for Phase 7.** An *Open-source licenses* screen, generated from
licensee's `artifacts.json`.

- BSD-3-Clause requires the copyright notice, conditions, and disclaimer in
  binary distributions.
- Apache-2.0 requires its license text and any NOTICE contents.
- The screen also covers what licensee can't see: SQLCipher bundles SQLite
  (public domain), libtomcrypt, and a Java layer forked from AOSP.

### The project's own license

Not chosen yet (`docs/00-overview.md`, "Open decisions" #7), and there is
no LICENSE file. The choice affects contributions and how third-party data
is redistributed.

## Dependency verification

`gradle/verification-metadata.xml` records a SHA-256 checksum for each
artifact, and Gradle checks what it resolves against it on every run,
locally and in CI (NFR-SEC-07). Verification is strict: the build should
download nothing that a person hasn't reviewed (ADR-0007).

Regenerate the metadata after any dependency change, in the same change.
Run the CI task set with `--write-verification-metadata sha256` from an
**empty Gradle home**, then review the diff. Every new artifact needs a
reason to be there.

A warm Gradle cache isn't enough. Gradle reuses metadata it parsed in
earlier builds, so it doesn't record every parent POM, BOM, or Gradle
module file that a cold cache fetches and verifies. CI always starts cold,
so metadata written from a warm cache can pass locally and fail on GitHub.
That is exactly what broke CI until 2026-11-07.

```bash
export GRADLE_USER_HOME="$(mktemp -d)"   # a cold cache, like CI's
./gradlew --no-configuration-cache --write-verification-metadata sha256 \
  checkModuleGraph checkSourceRules :build-logic:convention:test \
  assembleDebug assembleRelease assembleDebugAndroidTest \
  test lint detekt ktlintCheck licensee koverVerify classes
```

- `classes` covers CodeQL's build (`assembleDebug classes`).
- The empty home has no downloaded JDK, so point `JAVA_HOME` at a JDK 21
  (`docs/03-platforms.md`, "Toolchain").
- Then prove it: run the same tasks again from a second empty Gradle home,
  without `--write-verification-metadata`. Strict verification must pass and
  leave the file unchanged.
- Check each new hash against the checksum its repository publishes (Maven
  Central and Google Maven publish a SHA-1 next to each file).

- With a device or emulator attached, also run `connectedDebugAndroidTest`
  the same way, so that the test-platform artifacts get recorded.
- Gradle records only the artifacts it resolved, which on Linux means only
  the `linux` `aapt2` jar. When AGP changes, add the `osx` and `windows`
  `aapt2` jars for the new version by hand, and check each hash against the
  SHA-1 that Google Maven publishes.
- Never disable dependency verification to get a build green.

**Status (2026-11-07): fix prepared, not yet confirmed on GitHub.** Every
CI and CodeQL run on GitHub has failed so far. Dependency verification
rejected BOM metadata files (for example `bc-jdk18on-bom` 1.80.2,
`guava-parent` 33.4.0-jre, and five `junit-bom` versions), because the
metadata was written from a warm cache. Regenerating it from an empty
Gradle home added 15 metadata files (9 `.pom`, 6 `.module`, no libraries).
Each one matches its repository's published SHA-1. From a second empty
Gradle home, CI's build job, CodeQL's build, and `connectedDebugAndroidTest`
then passed under strict verification. M0's "clean CI run on `main`" is met
only once GitHub shows it (`docs/roadmap.md`, "Phase 0 — Foundations and
technical spikes").

### Repositories

The build plugins behind `build-logic` (AGP, KGP, detekt and the rest)
run with full privileges in every build, so repository content filters
limit each repository to the groups it must serve (ADR-0007). The filters
are in `settings.gradle.kts` and `build-logic/settings.gradle.kts`. Keep
the two files in step.

| Repository | Serves |
| :- | :- |
| Google Maven | Only `com.android.*`, `com.google.android.*`, `com.google.testing.platform.*` and `androidx.*` |
| Maven Central | Everything else |
| Gradle Plugin Portal | Only groups that it alone publishes: `org.jlleitschuh.gradle` (the ktlint plugin), plus `org.gradle.kotlin.*` for the plugins `build-logic`'s own build applies |

Module build files can't add repositories: the root settings use
`FAIL_ON_PROJECT_REPOS`.

## Updates

Dependabot (`.github/dependabot.yml`) opens update PRs every week:

- **Gradle**, from the root directory, with at most 5 open PRs. AndroidX
  updates (`androidx.*`) are grouped, and so are Kotlin and KSP updates
  (`org.jetbrains.kotlin*`, `com.google.devtools.ksp*`).
- **GitHub Actions.**

Dependabot doesn't regenerate `gradle/verification-metadata.xml`, so a
Gradle update PR fails CI until someone reviews the new artifacts and
regenerates the metadata in the same PR. That review step is intended.

Some upgrades need a manual step:

- **SQLCipher:** re-read the new version's LICENSE, then bump
  `sqlcipher-license-checked` ("The SQLCipher exception").
- **AGP:** add the `osx` and `windows` `aapt2` jars by hand ("Dependency
  verification").

### Pins with a reason

Comments in the catalog explain these entries. Read the comment before you
change one.

| Catalog entry | Value | Why |
| :- | :- | :- |
| `sqlcipher-license-checked` | 4.20.0 | The `sqlcipher-android` version whose LICENSE was last read (BSD-3-Clause). Change it only after re-reading the license of the version in `sqlcipher` |
| `detekt-kotlin` | 2.0.21 | detekt 1.23.x runs on the Kotlin compiler it was built with, not the project's Kotlin (2.4.21). `QualityConventions.kt` forces this version on the `detekt` configuration |
| `androidx-test-espresso` | 3.7.0 | Compose `ui-test` pulls Espresso 3.5, which breaks on API 37 (`InputManager.getInstance` was removed) |
| `sdk-compile`, `sdk-target`, `sdk-min` | 37, 37, 29 | SDK levels (ADR-0007). The convention plugins read them, never module build files |
| `jvm-target` | 17 | All modules compile to Java 17 bytecode (ADR-0007) |

The Gradle daemon runs on JDK 21 (`gradle/gradle-daemon-jvm.properties`),
because detekt 1.23 can't start on JDK 25 (`docs/03-platforms.md`,
"Toolchain").

## Enforcement

| Check | What it enforces | Where it runs |
| :- | :- | :- |
| Gradle dependency verification | Every resolved artifact matches `gradle/verification-metadata.xml` | Every Gradle run, locally and in CI |
| `./gradlew licensee` | The license allowlist, and the SQLCipher pin | CI ("License allowlist" step in `.github/workflows/ci.yml`) |
| `./gradlew checkModuleGraph` | Declared dependencies and resolved production classpaths: no file dependencies, `feature:*` modules never depend on each other, no Android library in a pure module, restricted groups stay in their modules. Checking resolved classpaths means a rule can't be bypassed through a transitive dependency | CI ("Module graph and source rules" step) |
| `./gradlew :build-logic:convention:test` | Tests for those checks | CI ("Build-logic tests" step) |
| `.claude/hooks/pre_tool_guard.py` | Denies analytics, ads and attribution SDKs; asks before crash-reporting SDKs | Claude Code, before a tool writes a `.gradle.kts`, `.gradle` or `.toml` file |
| `.claude/hooks/post_edit_checks.py` | Warns about inline dependency coordinates in Gradle build files (not `settings.gradle.kts`) | Claude Code, after an edit |
| `.claude/rules/build-and-dependencies.md` | Loads these rules for agents | Claude Code, when it touches `**/*.gradle.kts`, `gradle/**`, `build-logic/**` or `.github/workflows/**` |
| Dependabot | Weekly update PRs, which must pass dependency verification | GitHub |

The hooks are guardrails, not a security boundary. CI gates and code
review remain the enforcement points. Release signing and CI secrets are
covered in `docs/05-release.md` ("Signing and secrets").
