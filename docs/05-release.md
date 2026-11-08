# 05 — Build, release, operate

Most of this file is the plan for Phase 2 (intel bundles) and Phase 7
(release). What exists today is the release build type (R8, resource
shrinking, signing from environment variables), the app version in
`gradle.properties`, and CI's throwaway signing key. There is no release
workflow yet: `.github/workflows/` holds only `ci.yml` and `codeql.yml`.
Sections and items that are only planned say so.

## Build types and R8

The `aegis.android.application` convention plugin configures the app. It
applies only to `:app` and refuses any other module. It sets:

- `namespace` and `applicationId` to `io.github.jabbottiii.aegis`
  (ADR-0007)
- `targetSdk` from the catalog (`sdk-target`, 37)
- `versionCode` and `versionName` from `gradle.properties` ("Versioning")
- `BuildConfig` generation, and lint's `checkDependencies`
- release signing from environment variables ("Signing and secrets")
- the quality gates: detekt and ktlint (`docs/04-quality.md`, "Build
  checks") and licensee (`docs/02-dependency-policy.md`, "License
  allowlist")

Its release build type:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
        // signingConfig = signingConfigs.getByName("release"),
        // set only when AEGIS_SIGNING_STORE_FILE is set
    }
}
```

The plugin leaves the `debug` build type at AGP's defaults.

- **NFR-SEC-05** requires release builds to be R8-minified,
  `debuggable=false`, and signed with Play App Signing, with upload keys
  kept outside the repo. The `pre_tool_guard.py` hook denies
  `android:debuggable="true"` in a manifest.
- **R8 rules** live in `app/proguard-rules.pro`. Today it holds one rule,
  defense in depth for `docs/privacy.md` ("Logging"): `LogPolicy.RELEASE`
  already drops everything at INFO and below, and R8 also removes any
  verbose, debug, or info logcat call that remains.

  ```text
  -assumenosideeffects class android.util.Log {
      public static int v(...);
      public static int d(...);
      public static int i(...);
  }
  ```

- **Exported components differ by build type.** Debug builds also export
  Compose's `PreviewActivity` and the test host
  (`docs/permissions-and-policy.md`, "Exported components and merged
  manifest entries").
- **Baseline profile:** planned for Phase 7, measured with Macrobenchmark
  (`docs/04-quality.md`, "Test layers").
- **Fixture APKs** (`testing/fixtures/`) will need their own plugin, with
  their own IDs and a key clearly named as a test key
  (`docs/04-quality.md`, "Malware sample handling").
- **Flavors:** there are none today. Whether to ship on F-Droid is open
  decision #3 (`docs/00-overview.md`, "Open decisions"). F-Droid would
  need a `foss` flavor without Play Integrity, ML Kit or other proprietary
  SDKs.
- **CI builds both types** on every PR, and signs the release build with a
  throwaway key ("Signing and secrets"):

  ```bash
  ./gradlew assembleDebug assembleRelease
  ```

## Versioning

- **App version:** `aegis.versionCode` and `aegis.versionName` in
  `gradle.properties`, today `1` and `0.1.0`. The application plugin reads
  them into `defaultConfig`. Bump both for every release.
- **Application ID:** `io.github.jabbottiii.aegis`, chosen on 2026-11-01
  (ADR-0007). It can't change after the first Play upload: a new ID means a
  new store listing.
- **Changelog:** updated with every release. There is no changelog file
  yet.
- **Commits** follow Conventional Commits (`CONTRIBUTING.md`, "Commits and
  pull requests").
- **Intel bundles** carry their own version numbers, which move
  independently of the app ("Intel bundle releases"):
  - `version` is a monotonic integer. The app rejects a bundle whose
    version isn't above the installed one.
  - `minAppVersion` keeps a bundle from reaching app versions that can't
    use it.
  - `minEngineVersion` versions the rules and models in the bundle.
  - A breaking change to the bundle format uses `!` in its commit message
    and bumps `minEngineVersion`.

## Signing and secrets

| Secret | Where it lives | Never |
| :- | :- | :- |
| Play upload key | Developer's machine or hardware token, and optionally a CI secret on a protected release environment | Never in the repo or in plain CI variables |
| App signing key | Play App Signing (managed by Google) | — |
| **Intel root key (Ed25519)** | Offline, on the maintainer's hardware token (ADR-0006). Used only to sign delegation records. | Never in CI, never in the repo |
| **Intel online signing key (Ed25519)** | A secret in a protected GitHub Actions environment with required reviewers. It is delegated by the root key for 90 days or less. | Never in the repo or in workflows that forks can trigger |
| Third-party API keys (future) | A backend proxy only | Never inside the APK |

### App release signing

The application plugin reads release signing from four environment
variables:

- `AEGIS_SIGNING_STORE_FILE`
- `AEGIS_SIGNING_STORE_PASSWORD`
- `AEGIS_SIGNING_KEY_ALIAS`
- `AEGIS_SIGNING_KEY_PASSWORD`

Without `AEGIS_SIGNING_STORE_FILE`, the release build is unsigned. If it is
set and another of the four is missing, the build fails with
`<NAME> is not set`.

- Release signing reads from environment or CI secrets only. Never commit
  keystores, passwords, or key material.
- `.gitignore` already excludes keystores (`*.jks`, `*.keystore`),
  `local.properties`, `google-services.json`, and build outputs, including
  `*.apk` and `*.aab`. Keep it that way.

### CI's throwaway key

The release build must be signed to install, so CI signs it with a key it
generates for each run and never publishes (`.github/workflows/ci.yml`,
"Create a throwaway release signing key"):

- a PKCS12 keystore in `$RUNNER_TEMP`, alias `ci-throwaway`, holding an
  RSA 2048 key with a validity of 1 day
- a random password from `openssl rand -hex 24`, masked in the log
- the four `AEGIS_SIGNING_*` variables, written to `$GITHUB_ENV` for the
  later steps

The CI workflow uses no secrets, and fork PRs never get any.

### Rules for workflows and agents

- **Workflows:**
  - Pin third-party actions by commit SHA.
  - Set the default `GITHUB_TOKEN` permissions to read-only
    (`permissions: read-all` or narrower). `ci.yml` and `codeql.yml` use
    `contents: read`.
  - Never expose secrets to `pull_request` events from forks.
  - The intel online signing key is used only in the protected release
    environment (ADR-0006).
- **No private key in the repo or in logs** (`AGENTS.md` rule 5). The
  `pre_tool_guard.py` hook denies writing a private key block, a Google API
  key, an AWS access key ID or a GitHub token into any non-test file.
- **Claude Code** (`.claude/settings.json`) can't read `local.properties`,
  `keystore.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `*.key`,
  `google-services.json` or `.env` files. It asks before `git tag`,
  `gh release`, and Gradle tasks with `publish` or `upload` in their names.
  Agents never commit, tag or push (`AGENTS.md` rule 15).
- **No API keys in the APK.** Optional features that need one, such as Web
  Risk, require a backend proxy (`docs/00-overview.md`, "Open decisions"
  #4).

## Play tracks and rollout

**Planned.** None of these steps has run yet.

1. Bump `aegis.versionCode` and `aegis.versionName`, and update the
   changelog ("Versioning").
2. Tag the release, and CI builds the release AAB. The owner tags, since
   agents never do (`AGENTS.md` rule 15). No release workflow exists
   yet.
3. Upload to the Play **internal** track, then closed testing, then
   production with a staged rollout (10% → 50% → 100%), watching the crash
   rate and FP reports.

The milestones use the tracks like this (`docs/roadmap.md`):

| Milestone | Play track | Exit criteria |
| :- | :- | :- |
| M0 Foundations ready | None | A clean CI run on `main`, an empty app with the design system, spike write-ups merged, and the ADRs updated to *Accepted* or *Rejected*. Not met yet: CI hasn't passed on GitHub (`docs/02-dependency-policy.md`, "Dependency verification"), and spikes S1–S7 haven't started |
| M1 Scanner alpha | Internal testing | The scanner detects the AMTSO Android test artifacts and fixture apps, the intel update works end to end on a test channel, and internal testing runs on the Play *internal testing* track |
| M2 Shield beta | Closed testing | Battery drain attributable to the Shield is ≤ 2% per day on the device matrix |
| M3 AI defense beta | Not set | No track or exit criteria are set for M3 yet |
| M4 1.0 on Play | Production | Production release, with staged rollout starting at 10% |

### Closed testing before production

Phase 7 runs closed testing with real users. Play requires new
**personal** developer accounts to run a closed test with a minimum number
of testers for a minimum period before production access. Confirm the
current numbers in Play Console when you get there.

### Play Console work (Phase 7)

- Permissions Declarations: `QUERY_ALL_PACKAGES`, All files access,
  VpnService, and special-use FGS if applicable
  (`docs/permissions-and-policy.md`, "VpnService")
- the Data safety form (`docs/permissions-and-policy.md`, "Data safety
  form")
- a privacy-policy URL
- store listing copy that documents the VPN use
- the content rating

### Other channels

Google Play is the primary channel. Whether Aegis also ships through
GitHub Releases or F-Droid is open decision #3 (`docs/00-overview.md`,
"Open decisions"). The requirements for each channel, including Google's
developer verification for apps installed outside Play, are in
`docs/permissions-and-policy.md` ("Distribution"). Register as a verified
developer before any non-Play distribution (risk R11,
`docs/00-overview.md`, "Top risks and mitigations").

## Intel bundle releases

**Planned for Phase 2.** `tools/intel-pipeline/` doesn't exist yet.

Intel bundles release on their own cadence, separate from app releases.
Bundles should update daily, ideally from automated CI (ADR-0006). A
bundle's `minAppVersion` keeps it from reaching app versions that can't use
it.

### The pipeline

- `tools/intel-pipeline/` is a JVM CLI that runs in GitHub Actions. It
  fetches each source in `docs/data-sources.md`, then normalizes,
  validates, and compiles the bundle parts and writes a manifest.
- Validation gates: schema checks, prefix-breadth limits, canary
  allowlists (popular domains and IP ranges that must never be blocked),
  and maximum churn per release. A diff report is attached to each build.
- Every change to rules, the model, or IOCs runs the benign corpus in CI
  against the pipeline's feature dumps, and a release is blocked when it
  regresses a gate. A model release also needs the adversarial evaluation
  (`docs/04-quality.md`, "Detection quality").
- The pipeline generates `attribution.json` in the bundle from
  `docs/data-sources.md`, for *About › Data sources* (FR-INTEL-05).

### What a bundle holds

- `manifest.json`: version (monotonic integer), createdAt, validUntil,
  minAppVersion, minEngineVersion, and a list of parts (name, SHA-256,
  size).
- `manifest.sig`: an Ed25519 signature over the exact manifest bytes.
- The parts themselves, in protobuf or binary formats.
- The current delegation record.

Heuristic rules and ML models are data in the bundle, versioned with
`minEngineVersion`, so they can be updated without an app release
(FR-DET-07). A baseline bundle ships inside the APK, so that protection
works offline from first launch (FR-INTEL-04).

### Signing a bundle

Two-tier Ed25519 keys (ADR-0006):

- The **offline root key** is held on a hardware token by the maintainer.
  Its public key is pinned in the app, with two slots to allow rotation in
  a later app release.
- The root signs a **delegation record** for an **online signing key**,
  valid for 90 days or less. The record contains the key ID, the public
  key, notBefore, notAfter, and the allowed bundle channels.
- CI signs bundles with the online key inside a protected GitHub Actions
  environment that has required reviewers. Each bundle ships with the
  current delegation record.
- Daily releases can be fully automated without the root key ever
  entering CI.

Revocation:

- A compromised online key is limited by the delegation expiry. The root
  issues a new delegation, and the old key ID goes on a `revokedKeyIds`
  list inside the next root-signed delegation record.
- A compromised root key requires an app update that rotates the pinned
  slot.

The app verifies the signature before parsing anything else
(`AGENTS.md` rule 5, NFR-SEC-01). Its other checks (rollback, expiry, the atomic swap)
are in `docs/01-architecture.md` ("Intel update").

### Hosting

The bundle host (GitHub Releases or a CDN) is not trusted for integrity,
only for availability. HTTPS is still used, for privacy and defense in
depth. Downloads carry no device or user identifiers (NFR-PRIV-01).

## Release checklist

Manual items for an app release. **Planned:** none of them has run yet.

- [ ] `aegis.versionCode` and `aegis.versionName` bumped, and the changelog
      updated.
- [ ] CI passed on the release commit, including the release build.
- [ ] The OWASP MASVS review and the MASTG test cases done, every parser
      (APK, DEX, DNS, QR payloads) fuzzed, and tamper testing done. A full
      security pass comes before M4 (`docs/04-quality.md`, "Test layers").
- [ ] Performance and battery tuned against the NFR budgets
      (`docs/requirements.md`, "Non-functional requirements") on the
      reference mid-range device, with the baseline profile in place.
- [ ] Macrobenchmark and the device-matrix instrumented tests run for the
      release (`docs/04-quality.md`, "Test layers", "Device matrix").
- [ ] Detection-quality gates hold on the benign corpus. For a model
      release, the model is no worse than the current one on every
      adversarial transformation (`docs/04-quality.md`, "Detection
      quality").
- [ ] UI copy reviewed against the "honest claims" principle. No inbound
      crawler-blocking claim unless spike S3 passed (`AGENTS.md` rule
      10, FR-ACS-08).
- [ ] The *Open-source licenses* screen is current, including SQLCipher's
      BSD-3-Clause notice and the code bundled in its AAR
      (`docs/02-dependency-policy.md`, "Open-source notices").
- [ ] Data source terms re-verified, and every shipped source, including
      every CC-BY source, listed in *About › Data sources*
      (`docs/data-sources.md`).
- [ ] `canary-allowlist.yaml` and `support-resources.yaml` reviewed
      (`docs/data-sources.md`).
- [ ] Play Console work done: Permissions Declarations, the Data safety
      form, the privacy-policy URL, store listing copy, and the content
      rating ("Play tracks and rollout").
- [ ] Before the first production release from a new personal account:
      the closed test completed, with the current numbers confirmed in
      Play Console.
- [ ] Before any release outside Play: registered as a verified developer
      (risk R11).
- [ ] Staged rollout 10% → 50% → 100%, watching the crash rate and FP
      reports.
