# 04 — Quality: testing, checks, CI

How we know Aegis works, and keeps working: the test layers, the build
checks every change must pass, how malware samples are handled, the
detection-quality gates, the devices we test on, and CI.

Status: the build checks, the JVM unit tests and the instrumented tests
exist. On 2026-11-01 the full local gate set passed, and instrumented tests
passed on an API 37 emulator; they weren't run on API 29 locally. CI on
GitHub does not pass yet (see "CI (GitHub Actions)"). Fuzzing, Robolectric,
pipeline, benchmark and detection-quality runs start with the code they
test; each row below says when.

## Test layers

| Layer | Tools | Scope | Runs | Today |
| :- | :- | :- | :- | :- |
| Unit (JVM) | JUnit, MockK, Turbine, kotlinx-coroutines-test | Engine modules (`engine:*`, `shield:dns`, `ai:guard`, `intel:client` logic), ViewModels | Every PR | In use in the Phase 0 modules; passed locally on 2026-11-01 |
| Robolectric | Robolectric | Android-adapter code paths that don't need real system services | Every PR | Planned: joins with the first Android adapter that needs it |
| Fuzz | Jazzer (JVM fuzzing) | DNS parser, binary-XML parser, DEX parser, ZIP walker, bundle manifest parser, QR payload parser, hidden-text scanner | Short run every PR; long run nightly | Planned: Jazzer is wired into `aegis.jvm.engine` with the first parser (Phase 1) |
| Instrumented | AndroidX Test, Compose UI test, UI Automator | VpnService, PackageManager flows, notification listener, permission flows, uninstall prompts | Every PR on an emulator; device matrix nightly or per release | In use in the Phase 0 modules. Passed locally on an API 37 emulator on 2026-11-01, not run on API 29 locally. CI is configured to run API 29 and 37 (`ci.yml`), but no CI run passes yet (see "CI (GitHub Actions)") |
| Macrobenchmark | Jetpack Macrobenchmark, baseline profiles | Cold start, scan throughput, dashboard jank | Per release | Not started; the baseline profile is planned for Phase 7 |
| Battery and network | Battery Historian, `dumpsys batterystats`, packet captures on the test host | Shield cost, wake locks | Per milestone | Not started (the Shield is Phase 3) |
| Pipeline | JUnit on `tools/intel-pipeline` | Source adapters (golden files), validators, signing, and churn gates | Every PR | Planned: starts with the pipeline in Phase 2 |
| Security | MobSF static scan, OWASP MASTG test cases, manual tamper testing (repackaging, hooking) | Aegis itself | Per milestone; a full pass before M4 | Not started |

**Coverage targets:** ≥ 80% line coverage in engine modules
(NFR-MAINT-01). Coverage isn't gated for UI modules, which instead must
have screenshot or semantic tests for every state.

### Writing tests

The full rules are in `.claude/rules/tests-and-fixtures.md`, which loads
for agents on any test path.

- **Deterministic.** Inject the clock and dispatchers, use `runTest` and
  Turbine for flows, and avoid real network calls, real sleeps, and
  order-dependent state.
- **Fakes first.** Prefer fakes for engine collaborators. Use MockK at
  Android boundaries only.
- **One behavior per test.** JVM tests (`src/test`) use backtick sentence
  names, such as `` `rejects zip with compression ratio above limit` ``.
  Instrumented tests (`src/androidTest`) can't, because DEX allows spaces
  in names only from API 30 and minSdk is 29. Use camelCase sentence names
  there, such as `launchShowsAppNameAndHonestStatus`
  (`docs/03-platforms.md`, "Behavior by API level").
- **Parsers** need malformed-input cases plus a Jazzer fuzz target, and a
  regression fixture for every bug the fuzzer finds.
- **Instrumented tests** that touch the VPN, the notification listener, or
  package install use the debug-only bundle and fixture apps, and never
  third-party endpoints.
- **No privacy leaks.** Assertions for the scam detector and the Shield
  verify that no message text or query log is persisted (inspect the DB in
  tests).

## Build checks

Every one of these runs in CI on every PR (see "CI (GitHub Actions)") and
can run locally.

| Check | Command | What it enforces | Configured in |
| :- | :- | :- | :- |
| Module graph | `./gradlew checkModuleGraph` | The module dependency rules in `docs/01-architecture.md` ("Dependency rules"), transitive dependencies included. It reads declared dependencies in every configuration and resolves each module's production compile and runtime classpaths (every variant, never test classpaths); each module also gets a `checkResolvedGraph` task | `aegis.root`; `build-logic/…/ModuleGraph.kt`, with unit tests |
| Source rules | `./gradlew checkSourceRules` | In production sources only: no direct logging (`AGENTS.md` rule 8), no hard-coded `Dispatchers.IO`/`Default`/`Main`/`Unconfined`, no `GlobalScope`, no `runBlocking`, no `!!` in engine code. It's a pattern scan over code with comments and strings removed, so it needs no type resolution; indirect routes such as reflection still need review | `aegis.root`; `build-logic/…/SourceRules.kt` |
| Build-logic tests | `./gradlew :build-logic:convention:test` | Unit tests for the two checks above | `build-logic/convention` |
| Android Lint | `./gradlew lint` | Fails on errors. The convention plugin promotes the TLS, Network Security Config (user CAs, insecure base config), exported-component, WebView, dynamic-code-loading, and world-readable-file checks to errors | The Android convention plugins (`AndroidConventions.kt`) |
| detekt | `./gradlew detekt` | Complexity and code smells; it also bans the `android.util.Log` import. CI's plain `detekt` has no type resolution, so rules that need it don't run: under AGP 9, detekt 1.23 has no Android variant tasks. `checkSourceRules` covers the critical ones | `config/detekt/detekt.yml`; `QualityConventions.kt` |
| ktlint | `./gradlew ktlintCheck` (`./gradlew ktlintFormat` fixes formatting) | ktlint's `ktlint_official` code style, a stricter superset of the Kotlin coding conventions | `.editorconfig`; `QualityConventions.kt` |
| Coverage | `./gradlew koverVerify` | ≥ 80% line coverage on `aegis.jvm.engine` modules (NFR-MAINT-01) | `aegis.jvm.engine` |
| License allowlist | `./gradlew licensee` | Shipped dependencies are Apache-2.0, MIT, BSD-2-Clause, or BSD-3-Clause, plus one pinned exception for `net.zetetic:sqlcipher-android` (`docs/02-dependency-policy.md`, "License allowlist") | `QualityConventions.kt` |
| Dependency verification | Every Gradle run | Strict SHA-256 verification against `gradle/verification-metadata.xml` (NFR-SEC-07). Regenerate it after any dependency change and review the diff (`docs/02-dependency-policy.md`, "Dependency verification") | `gradle/verification-metadata.xml` |
| Hook self-tests | `python3 .claude/hooks/test_hooks.py` | The Claude Code hooks (`CLAUDE.md`, "Claude Code specifics") | `.claude/hooks/` |

Run them all locally (these match the CI gates):

```bash
./gradlew checkModuleGraph checkSourceRules   # module rules + source rules
./gradlew :build-logic:convention:test         # tests for those checks
./gradlew assembleDebug assembleRelease
./gradlew test koverVerify            # unit tests + the ≥ 80% engine coverage gate
./gradlew lint detekt ktlintCheck
./gradlew licensee                    # dependency license allowlist
./gradlew :<module>:test              # one module
./gradlew connectedDebugAndroidTest   # needs an emulator or device
python3 .claude/hooks/test_hooks.py   # Claude Code hook self-tests
```

The toolchain these need (JDK 21 for the daemon, the API 37 SDK platform)
is in `docs/03-platforms.md` ("Toolchain").

- **Every module plugin applies detekt, ktlint, and licensee.** These gates
  are not configurable per module: never add baselines, suppressions, or
  `ignoreFailures` in `QualityConventions.kt`.
- **Don't weaken gates.** Never add `-x test`,
  `lint { abortOnError = false }`, detekt baselines, or `@Suppress` to get
  a change through, and never disable dependency verification or the
  license check to get a build green, without the user's explicit approval
  (`.claude/rules/build-and-dependencies.md`).
- **Coverage outside the pure modules is open.** `aegis.jvm.engine` gives
  the six pure modules explicit API mode and the Kover gate.
  `engine:scanner`, `engine:ml`, `intel:client`, and a non-pure `ai:guard`
  would get neither. Open decision #8 (`docs/00-overview.md`, "Open
  decisions") chooses between an engine convention plugin for those
  modules and narrowing NFR-MAINT-01 to the pure modules, before the first
  of them is created.
- **detekt follow-up:** move to detekt 2.x once it is stable, then re-check
  the JDK 21 constraint (ADR-0007).

## Malware sample handling

- **Live malware is never committed to this repository**, not in tests,
  fixtures, or LFS, and never in its history (`AGENTS.md` rule 12). Never
  download samples into this working tree. No real malware APKs, DEX
  files, or password-protected sample archives.
- **Repository fixtures are benign APKs built from source.** They live
  under `testing/fixtures/`, which doesn't exist yet. They *emulate* risky
  profiles: they request the banking-trojan permission set, declare
  accessibility or admin components, embed a dummy DEX in assets, and so
  on. They do nothing harmful.
  - They are signed with a test key clearly named as a test key: its alias
    and file name contain `test`.
  - Fixture APKs need their own convention plugin, with their own
    application IDs and a test-named key; `aegis.android.application` is
    only for `:app`.
  - Their manifests deliberately declare risky permissions and components.
    Never copy a fixture manifest into a production module
    (`.claude/rules/android-manifest-and-permissions.md`).
- **Real-sample evaluation runs in an isolated lab environment** outside
  this repo: an air-gapped or network-isolated emulator, or dedicated
  devices on a quarantined network. Samples are stored encrypted and
  accessed under each source's terms (`docs/data-sources.md`). Only
  aggregate metrics come back into the repo.
- **The AMTSO feature-settings checks** for Android are the end-to-end
  smoke test for detection features, after checking their participation
  terms.
- **Never upload samples from user devices.** Aegis has no sample-upload
  feature in 1.0.
- **Detection-quality gates run from feature dumps,** never from APK
  binaries checked into the repo.
- The `pre_tool_guard.py` hook blocks malware downloads for agents
  (`CLAUDE.md`, "Claude Code specifics").

## Detection quality

| Metric | Definition | 1.0 gate |
| :- | :- | :- |
| **Benign FP (Malicious)** | Benign corpus apps receiving a *Malicious* verdict | **0** |
| **Benign FP (Suspicious)** | Benign corpus apps receiving *Suspicious* | ≤ 0.5% |
| **Known-family TPR** | Lab malware set (families with IOCs) detected as *Malicious* or *Stalkerware* | ≥ 98% |
| **Novel-variant TPR** | Held-out recent samples not in the IOC set, detected as *Suspicious* or worse | Tracked and reported per release; target set after the first baseline |
| **Stalkerware coverage** | Stalkerware-indicators samples detected | ≥ 95% of samples whose IOCs ship in the bundle |
| **Unscannable rate** | Benign corpus apps hitting parser limits | ≤ 0.2% |

**Benign corpus:** the most-installed apps from major categories,
installed on lab devices (their metadata and features extracted on the
device, with no APK redistribution), plus system apps from at least three
OEM images. Refresh it every quarter.

Every change to rules, the model, or IOCs runs the corpus in CI against
the pipeline's feature dumps. A release is blocked when it regresses a
gate. None of this runs yet: the scanner (Phase 1), the pipeline (Phase 2)
and the classifier (Phase 6) don't exist.

How the gates are used elsewhere:

- Heuristic rule thresholds are tuned against the benign corpus, and the
  ML threshold is the one that reaches a false-positive rate of 0.1% or
  lower on it, with the TPR at that threshold reported
  (`docs/features/malware-detection.md`, "Detection pipeline").
- ML alone yields at most *Suspicious* (`AGENTS.md` rule 4;
  `docs/features/malware-detection.md`, "Verdict aggregation").
- `detection-quality-reviewer` reviews rule, model, IOC, and verdict
  changes, including these corpus gates (see "Review checklist").

### Adversarial evaluation

Before each model release, in the lab:

- **Obfuscation and repackaging variants** of known samples: renamed
  packages, re-signed APKs, string encryption, packers.
- **LLM-assisted mutation:** variants of known samples rewritten with an
  LLM to preserve behavior while changing code shape. This tests reliance
  on brittle features.
- **Feature-space attacks:** adding benign permissions and components to
  shift the score.
- **Report the TPR under each transformation.** A model ships only if it
  is no worse than the current model on every transformation.

## Feature test plans

| Feature | Key tests |
| :- | :- |
| Shield | 24-hour soak per matrix device. Category NXDOMAIN tests. TCP DNS fallback. Fail-open fault injection. Coexistence states: other VPN, Private DNS strict, Always-on. Process-death recovery. |
| AI Crawler Shield | A debug bundle with a test-only prefix and a fast-failure assertion. Route-cap degradation. Overrides. The S3 inbound procedure (`docs/features/ai-crawler-shield.md`, "Inbound traffic: spike S3"). The copy checklist. |
| Scanner | Fixture verdict matrix. Zip-bomb and malformed DEX corpus. Install-to-verdict latency. Trust pinning after a certificate change. |
| Intel client | Tampered signature, wrong key ID, rollback attempt, expired bundle, partial download, power loss during swap (instrumented with process kill). |
| AI-1 impersonation | Lookalike fixtures. Official package signed with a test key. Confusable-character folding cases. |
| AI-3 agent guard | Enable a fixture accessibility service and notification listener, then assert the alert. Assert suppression for `isAccessibilityTool` apps. |
| AI-4 scam detector | A labeled message corpus covering scam and benign messages, multilingual later. Precision and recall per release. Verify that no text is persisted (DB inspection test). |
| AI-5 hidden text | Unicode tag, zero-width, and bidi fixtures, and a fuzzed text input. |
| Stalkerware flow | Notification copy assertions. The uninstall action is reachable only through the safety screen. |

Each feature spec's acceptance criteria (`docs/features/*.md`) say what
these tests must show.

## Device matrix

| Device class | Examples | OS |
| :- | :- | :- |
| Reference mid-range (performance budgets) | A recent mid-range Pixel or Samsung A-series | Android 16 or 17 |
| Low-end | 3–4 GB RAM device | Android 13+ |
| Aggressive OEM battery management | Xiaomi (HyperOS), Samsung (One UI) | Latest available |
| Emulators | API 29, 31, 34, 36, 37 (x86_64) | — |
| Network variants | Wi-Fi IPv4 only, Wi-Fi dual stack, carrier IPv6-only with 464XLAT, captive portal (lab) | — |

- The performance budgets in `docs/requirements.md` ("Non-functional
  requirements") are targets measured on the reference mid-range device.
- CI is configured to run only the API 29 and API 37 emulators; CI does
  not pass yet. The other emulator levels and the physical devices are for
  the nightly or per-release device-matrix runs, which don't exist yet.
- Spike S3 runs on Pixel, Samsung, and Xiaomi with Android 13, 15, and 17
  (`docs/03-platforms.md`, "OEM behavior").

## CI (GitHub Actions)

**Status: not yet green on GitHub.** Every CI and CodeQL run so far failed
dependency verification, because the metadata was written from a warm
Gradle cache. The fix, metadata regenerated from an empty Gradle home,
passes a local simulation of every job, but GitHub hasn't confirmed it
(`docs/02-dependency-policy.md`, "Dependency verification"). Until it does,
M0's "clean CI run on `main`" isn't met (`docs/roadmap.md`, "Phase 0 —
Foundations and technical spikes").

These gates run on every PR. Once the workflows have run on GitHub, the
owner makes them required checks on `main` in branch protection. Items 4
and 6 start once their first code lands.

1. `./gradlew assembleDebug assembleRelease`. The release build uses a
   throwaway CI key and is never published.
2. `./gradlew test koverVerify`: unit tests, plus the ≥ 80% line-coverage
   gate on engine modules. Robolectric tests join with the first Android
   adapter that needs them.
3. `./gradlew lint detekt ktlintCheck` (see "Build checks").
4. Short fuzz runs (Jazzer, time-boxed). *Planned: starts with the first
   parser.*
5. Instrumented smoke tests on emulators: API 29 and API 37.
6. `tools/intel-pipeline` tests. *Planned: start with the pipeline in
   Phase 2.*
7. Gradle **dependency verification** (`gradle/verification-metadata.xml`),
   which applies to every Gradle run.
8. **License allowlist** check for dependencies: `./gradlew licensee`
   (Cash App's licensee, with the allowlist in `QualityConventions.kt`).
9. CodeQL analysis for Java and Kotlin.
10. `./gradlew checkModuleGraph checkSourceRules`: module dependency rules,
    including transitive dependencies, plus the source rules for logging,
    dispatchers, `GlobalScope`, `runBlocking`, and engine `!!`.
11. `./gradlew :build-logic:convention:test` (tests for those checks) and
    `python3 .claude/hooks/test_hooks.py`.

### `ci.yml`

On every push to `main` and every pull request. Top-level permissions are
`contents: read`. Runs share a concurrency group per ref, and a newer push
to a pull request cancels its older run. The workflow uses no secrets.

- **`build`** ("Build, unit tests, and static checks"; `ubuntu-latest`,
  45 min):
  1. Check out with `persist-credentials: false`.
  2. Install Temurin JDK 21 (`actions/setup-java`).
  3. Set up Gradle (`gradle/actions/setup-gradle`), which also validates
     `gradle-wrapper.jar` against Gradle's published checksums.
  4. Create a throwaway release signing key: a PKCS12 keystore in
     `$RUNNER_TEMP` with a random, masked password, valid for one day,
     exported as the `AEGIS_SIGNING_*` variables. The release build must
     be signed to install, but this key is generated per run and never
     published (`docs/05-release.md`, "Signing and secrets").
  5. `./gradlew checkModuleGraph checkSourceRules`.
  6. `./gradlew :build-logic:convention:test`.
  7. `./gradlew assembleDebug assembleRelease`.
  8. `./gradlew test koverVerify`.
  9. `./gradlew lint detekt ktlintCheck`.
  10. `./gradlew licensee`.
  11. Always upload `**/build/reports/` as the `reports` artifact, kept 14
      days.
- **`hooks`** ("Claude Code hook self-tests"; `ubuntu-latest`, 5 min):
  check out, then `python3 .claude/hooks/test_hooks.py`.
- **`instrumented`** ("Instrumented smoke tests (API N)"; `ubuntu-latest`,
  60 min), a matrix over API `29` (minSdk) and `37.0` (compileSdk; API 37
  images are published as `android-37.0`), with `fail-fast: false`:
  1. Check out, install JDK 21, and set up Gradle as above.
  2. Enable KVM.
  3. Run `./gradlew connectedDebugAndroidTest` in an emulator
     (`reactivecircus/android-emulator-runner`; `google_apis`, `x86_64`,
     animations disabled).
  4. Always upload `**/build/reports/androidTests/` as
     `instrumented-reports-api-<level>`, kept 14 days.

### `codeql.yml`

CodeQL analysis for Java and Kotlin, on every push to `main`, every pull
request, and weekly (Mondays at 04:23 UTC). Top-level permissions are
`contents: read`; the analysis job adds `security-events: write`.

- Gradle's cache is read-only here.
- CodeQL has to watch the compiler run, so the build runs with
  `--no-daemon --no-build-cache --no-configuration-cache` and
  `-Pkotlin.compiler.execution.strategy=in-process`.
- It builds `assembleDebug classes`: `classes` compiles the pure JVM
  modules that `:app` doesn't depend on.
- CodeQL runs only in CI, never locally.

### Workflow hardening

- **Pin third-party actions by commit SHA**, with the version in a
  comment.
- **Set the default `GITHUB_TOKEN` permissions to read-only.**
- **Never expose secrets to workflows triggered by forks.** Fork PRs never
  get any. The intel online signing key is used only in the protected
  release environment (ADR-0006).
- **Check out without persisting credentials** (`persist-credentials:
  false`).

### Not in CI yet

- Robolectric tests, with the first Android adapter that needs them.
- Short fuzz runs, with the first parser. The long nightly fuzz run and the
  nightly device-matrix run need a nightly workflow, which doesn't exist
  yet.
- Pipeline tests, with `tools/intel-pipeline`.
- The detection-quality corpus run, with the scanner and the pipeline.

Dependabot (`.github/dependabot.yml`) opens update PRs. They fail until
someone regenerates the verification metadata in the same PR, which is
intended (ADR-0007; `docs/02-dependency-policy.md`, "Dependency
verification").

## Review checklist

Use this before finishing any change. Agents run it as `/pre-pr-check`.
It never commits: changes stay uncommitted for the user to review.

**Gates:**

- [ ] Did every gate the build defines run, with its actual result
      recorded? A gate the build doesn't define yet is marked *not
      available yet*, and no pass is reported that wasn't seen.
- [ ] Did the module-specific tests for the touched modules run
      (`./gradlew :<module>:test`)?
- [ ] Instrumented tests (`connectedDebugAndroidTest`): run if a device or
      emulator is attached (`adb devices`), otherwise listed as *not run*?
      CI is configured to run them on API 29 and API 37 emulators.
- [ ] CodeQL listed as *not run locally*? Jazzer fuzz runs (CI item 4)
      and `tools/intel-pipeline` tests (CI item 6) listed as *not
      available yet* until the build defines them?
- [ ] If a dependency or plugin changed: was
      `gradle/verification-metadata.xml` regenerated, and does its diff add
      only the expected components? Were the test-platform artifacts
      recorded by running `connectedDebugAndroidTest` with a device
      attached?
- [ ] If AGP changed: were the `osx` and `windows` `aapt2` jars for the new
      version added by hand, with hashes checked against Google Maven? A
      Linux run records only the `linux` jar, so CI stays green while
      macOS and Windows builds fail verification.

**Docs in sync:**

- [ ] Does every behavior change map to a requirement or spike ID, and
      does the spec reflect it?
- [ ] Does a new or changed permission or exported component appear in
      `docs/permissions-and-policy.md` (`/add-permission`)?
- [ ] Does a new stored, retained, or transmitted data item appear in
      `docs/privacy.md`?
- [ ] Are the `docs/threat-model.md` "B4. Review triggers" handled: a new
      parser, network destination, data source, exported component,
      permission, or verdict-precedence change?
- [ ] Does a new external data source appear in `docs/data-sources.md`
      with verified terms?
- [ ] Do significant decisions have an ADR in `docs/decisions.md`
      (`/record-decision`)?
- [ ] Do user-facing strings follow `.claude/rules/ui-and-copy.md`, and
      does AI Crawler Shield copy pass its copy review checklist
      (FR-ACS-08)?

**Review agents** (delegate by what changed; fix each finding, or explain
why it doesn't apply):

- [ ] `security-reviewer`: always, for code changes in `engine/`,
      `shield/`, `intel/`, `core/security/`, `ai/guard/`, manifests, or
      IPC.
- [ ] `policy-privacy-reviewer`: manifests, permissions, data flows,
      strings, or `docs/data-sources.md`.
- [ ] `detection-quality-reviewer`: rules, IOCs, models, verdict logic, or
      intel pipeline output.

**Report** (nothing committed, staged, or pushed):

- [ ] A summary of the changes and the files affected.
- [ ] The validation performed, with results.
- [ ] The checks not run, and why.
- [ ] Assumptions, open issues, and residual risks.
- [ ] A suggested Conventional Commit message (`type(scope): summary`, with
      a short body that references the requirement IDs).

The definition of done for a roadmap task is in `AGENTS.md` ("Definition
of done for a roadmap task").
