# Testing Strategy

*Last updated: 2026-11-01 · Status: Draft*

---

## 1. Test layers

| Layer | Tools | Scope | Runs |
|---|---|---|---|
| Unit (JVM) | JUnit, MockK, Turbine, kotlinx-coroutines-test | Engine modules (`engine:*`, `shield:dns`, `ai:guard`, `intel:client` logic), ViewModels | Every PR |
| Robolectric | Robolectric | Android-adapter code paths that don't need real system services | Every PR |
| Fuzz | Jazzer (JVM fuzzing) | DNS parser, binary-XML parser, DEX parser, ZIP walker, bundle manifest parser, QR payload parser, hidden-text scanner | Short run every PR; long run nightly |
| Instrumented | AndroidX Test, Compose UI test, UI Automator | VpnService, PackageManager flows, notification listener, permission flows, uninstall prompts | Every PR on an emulator; device matrix nightly or per release |
| Macrobenchmark | Jetpack Macrobenchmark, baseline profiles | Cold start, scan throughput, dashboard jank | Per release |
| Battery and network | Battery Historian, `dumpsys batterystats`, packet captures on the test host | Shield cost, wake locks | Per milestone |
| Pipeline | JUnit on `tools/intel-pipeline` | Source adapters (golden files), validators, signing, and churn gates | Every PR |
| Security | MobSF static scan, OWASP MASTG test cases, manual tamper testing (repackaging, hooking) | Aegis itself | Per milestone; a full pass before M4 |

**Coverage targets:** ≥ 80% line coverage in engine modules (NFR-MAINT-01). Coverage isn't
gated for UI modules, which instead must have screenshot or semantic tests for every state.

## 2. Malware sample handling

- **Live malware is never committed to this repository**, not in tests, fixtures, or LFS.
- Repository fixtures under `testing/fixtures/` are **benign APKs built from source**. They *emulate* risky profiles: they request the banking-trojan permission set, declare accessibility or admin components, embed a dummy DEX in assets, and so on. They do nothing harmful, and they are signed with a test key clearly named as a test key.
- Real-sample evaluation runs in an **isolated lab environment** outside this repo: an air-gapped or network-isolated emulator, or dedicated devices on a quarantined network. Samples are stored encrypted and accessed under each source's terms ([DATA_SOURCES](DATA_SOURCES.md)). Only aggregate metrics come back into the repo.
- The **AMTSO feature-settings checks** for Android are the end-to-end smoke test for detection features (after checking their participation terms).
- Never upload samples from user devices. Aegis has no sample-upload feature in 1.0.

## 3. Detection quality

| Metric | Definition | 1.0 gate |
|---|---|---|
| **Benign FP (Malicious)** | Benign corpus apps receiving a *Malicious* verdict | **0** |
| **Benign FP (Suspicious)** | Benign corpus apps receiving *Suspicious* | ≤ 0.5% |
| **Known-family TPR** | Lab malware set (families with IOCs) detected as *Malicious* or *Stalkerware* | ≥ 98% |
| **Novel-variant TPR** | Held-out recent samples not in the IOC set, detected as *Suspicious* or worse | Tracked and reported per release; target set after the first baseline |
| **Stalkerware coverage** | Stalkerware-indicators samples detected | ≥ 95% of samples whose IOCs ship in the bundle |
| **Unscannable rate** | Benign corpus apps hitting parser limits | ≤ 0.2% |

**Benign corpus:** the most-installed apps from major categories, installed on lab devices
(their metadata and features extracted on the device, with no APK redistribution), plus
system apps from at least three OEM images. Refresh it every quarter.

Every change to rules, the model, or IOCs runs the corpus in CI against the pipeline's
feature dumps. A release is blocked when it regresses a gate.

### Adversarial evaluation

Before each model release, in the lab:

- Obfuscation and repackaging variants of known samples: renamed packages, re-signed APKs, string encryption, packers.
- **LLM-assisted mutation:** variants of known samples rewritten with an LLM to preserve behavior while changing code shape. This tests reliance on brittle features.
- Feature-space attacks: adding benign permissions and components to shift the score.
- Report the TPR under each transformation. A model ships only if it is no worse than the current model on every transformation.

## 4. Feature-specific test plans

| Feature | Key tests |
|---|---|
| Shield | 24-hour soak per matrix device. Category NXDOMAIN tests. TCP DNS fallback. Fail-open fault injection. Coexistence states: other VPN, Private DNS strict, Always-on. Process-death recovery. |
| AI Crawler Shield | A debug bundle with a test-only prefix and a fast-failure assertion. Route-cap degradation. Overrides. The S3 inbound procedure ([spec §6](features/ai-crawler-shield.md#6-inbound-traffic-spike-s3)). The copy checklist. |
| Scanner | Fixture verdict matrix. Zip-bomb and malformed DEX corpus. Install-to-verdict latency. Trust pinning after a certificate change. |
| Intel client | Tampered signature, wrong key ID, rollback attempt, expired bundle, partial download, power loss during swap (instrumented with process kill). |
| AI-1 impersonation | Lookalike fixtures. Official package signed with a test key. Confusable-character folding cases. |
| AI-3 agent guard | Enable a fixture accessibility service and notification listener, then assert the alert. Assert suppression for `isAccessibilityTool` apps. |
| AI-4 scam detector | A labeled message corpus covering scam and benign messages, multilingual later. Precision and recall per release. Verify that no text is persisted (DB inspection test). |
| AI-5 hidden text | Unicode tag, zero-width, and bidi fixtures, and a fuzzed text input. |
| Stalkerware flow | Notification copy assertions. The uninstall action is reachable only through the safety screen. |

## 5. Device matrix

| Device class | Examples | OS |
|---|---|---|
| Reference mid-range (performance budgets) | A recent mid-range Pixel or Samsung A-series | Android 16 or 17 |
| Low-end | 3–4 GB RAM device | Android 13+ |
| Aggressive OEM battery management | Xiaomi (HyperOS), Samsung (One UI) | Latest available |
| Emulators | API 29, 31, 34, 36, 37 (x86_64) | — |
| Network variants | Wi-Fi IPv4 only, Wi-Fi dual stack, carrier IPv6-only with 464XLAT, captive portal (lab) | — |

## 6. CI gates (summary)

These gates run on every PR. Once the workflows have run on GitHub, the owner makes them required checks on `main` in branch protection: build (debug and release), unit tests with the engine
coverage gate (`koverVerify`), lint, detekt, ktlint, `checkModuleGraph`, `checkSourceRules`,
build-logic tests, emulator instrumented smoke tests (API 29 and 37), Gradle dependency
verification, the license allowlist, the Claude Code hook self-tests, and CodeQL. Planned
additions: Robolectric tests (with the first Android adapter that needs them), short fuzz runs
(with the first parser), and pipeline tests (with `tools/intel-pipeline`). Details are in
[DEVELOPMENT](DEVELOPMENT.md#ci-gates).
