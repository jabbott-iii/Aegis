# Subsystem: `intel:client` and `tools:intel-pipeline` — signed threat-intel bundles

Threat intelligence reaches the device as signed, versioned bundles of
blocklists, IOCs, rules and models. `tools:intel-pipeline` is a JVM
command-line tool that runs in GitHub Actions: it fetches each source, then
normalizes, validates, compiles and signs the bundle. `intel:client` is the
app side: it downloads bundles, verifies them, protects against rollback,
and swaps them in atomically. It's the only module that does network I/O
for bundles (`docs/01-architecture.md`, "Module layout"). The design
decision is ADR-0006. Nothing is built yet; Phase 2 builds both, in
parallel with Phase 1.

## Contents

| Module or component | Provides |
| :- | :- |
| `intel:client` (`IntelClient`) | The daily update check, download, signature and hash verification, rollback protection, expiry warnings, the atomic swap, and keeping the last good bundle. It tells `:shield` to reload |
| Baseline bundle | A bundle shipped inside the APK, so that protection works offline from first launch (FR-INTEL-04) |
| `tools:intel-pipeline` | fetch → normalize → validate → compile → sign, with a diff report attached to each build |
| `tools/intel-pipeline/sources/` | Curated configs, such as `ai-services.yaml` |

## Bundle contents

The parts the specs name, and who reads them. Categories are declared in
the bundle manifest, and the UI renders whatever categories the installed
engine version supports. Rules and models are versioned with
`minEngineVersion` (`docs/01-architecture.md`, "Extensibility points").

| Part | Read by | Spec |
| :- | :- | :- |
| `manifest.json` and `manifest.sig` | `intel:client`, `core:security` | ADR-0006 |
| IOC sets: sorted arrays of hashes | `engine:signatures` | `docs/features/malware-detection.md`, "IOC matching (`engine:signatures`)" |
| `rules.pb` | `engine:heuristics` | `docs/features/malware-detection.md`, "Metadata heuristics (`engine:heuristics`)" |
| The LiteRT model | `engine:ml` | `docs/features/malware-detection.md`, "ML classifier (`engine:ml`)" |
| Domain category sets: sorted arrays of 64-bit hashes, including `ai_service` | `shield:dns` | `docs/features/network-protection.md`, "DNS engine (`shield:dns`)"; `docs/features/ai-crawler-shield.md`, "AI service egress control (DNS)" |
| `crawler_ranges.pb` | `RouteBuilder` in `shield:vpn` | `docs/features/ai-crawler-shield.md`, "Route set compilation (CI, `tools/intel-pipeline`)" |
| `ai_registry.pb` | `ai:guard` | `docs/features/ai-threat-defense.md`, "AI-1. AI app impersonation" |
| `ai_sdk_fingerprints.pb` | `ai:guard` | `docs/features/ai-threat-defense.md`, "AI-2. AI access audit" |
| A list of high-value brands | Lookalike detection | `docs/features/network-protection.md`, "Categories" |
| An OS support table | The audit's OS support check | `docs/features/device-security-audit.md`, "Posture checks (FR-AUD-01)" |
| The victim-support resources | The stalkerware safety flow (kept in the bundle so it can be localized) | `docs/features/device-security-audit.md`, "Stalkerware safety flow" |

## Updates on the device (`intel:client`)

The step-by-step sequence is in `docs/01-architecture.md` ("Intel update").

- **When:** a background check once a day, preferring unmetered networks,
  plus a manual "Update now" (FR-INTEL-03).
- **Verify the signature first.** Nothing in a bundle is parsed until
  `core:security` has verified `manifest.sig` against the pinned keys and
  the delegation record. Then each part's SHA-256 is checked against the
  manifest, and only then are parts parsed (`AGENTS.md` rule 5,
  NFR-SEC-01, FR-INTEL-01). Any failure aborts the update.
- **Freshness:** reject any version that isn't newer than the installed one
  (monotonic versions), and any bundle whose `minAppVersion` is above the
  installed app. An expired bundle is still accepted if it's newer than the
  installed one; it stays in use and the UI warns about it (FR-INTEL-02).
- **Swap:** parts go to `intel/staging/`, which is swapped with
  `intel/current` atomically. `intel/previous` is kept for local rollback,
  and `:shield` is told to reload.
- **Never lose protection.** An update failure never removes existing
  protection: the last good bundle is kept (NFR-REL-02).
- **Reading parts:** both the main process and `:shield` memory-map bundle
  parts read-only.
- **Transport:** an HTTPS GET with no identifiers (NFR-PRIV-01) from static
  hosting such as GitHub Releases or a CDN. The Network Security Config
  disables cleartext and trusts only system CAs. Integrity comes from the
  signature rather than the transport, so certificate pinning isn't
  required. The HTTP client is OkHttp, which `checkModuleGraph` allows only
  in `intel:client` and `shield:dns`.
- **Attribution:** source licenses that require it (for example CC-BY) are
  credited on an *About › Data sources* screen (FR-INTEL-05).

## The pipeline (`tools:intel-pipeline`)

- **Sources:** it fetches each source in `docs/data-sources.md`. New
  sources go through `/add-intel-source`. Crawler ranges are fetched in CI
  and never on the device.
- **Validation gates:** schema checks, prefix-breadth limits, canary
  allowlists (popular domains and IP ranges that must never be blocked),
  and a maximum churn per release.
- **Crawler ranges**, in detail (`docs/features/ai-crawler-shield.md`,
  "Route set compilation (CI, `tools/intel-pipeline`)"):
  1. Fetch each source, record the HTTP date and ETag, and parse the JSON
     prefix lists. Formats differ per operator, so each source has its own
     adapter.
  2. Reject IPv4 prefixes shorter than `/16` and IPv6 prefixes shorter than
     `/32`, and any prefix that overlaps the canary allowlist (ranges for
     major CDNs, DNS resolvers and popular services, plus the bundle host
     itself). Fail the build if churn exceeds 30% against the previous
     release; a maintainer must approve.
  3. Aggregate adjacent and overlapping prefixes within the same category.
  4. Emit `crawler_ranges.pb` with prefix, family, category, operator,
     source ID and fetchedAt.
  5. Sign it as part of the normal intel release.
- **AI service domains:** compiled from the curated
  `tools/intel-pipeline/sources/ai-services.yaml`. Each entry needs
  evidence: the vendor's documentation of the API host.
- **Signing:** two-tier Ed25519. An offline root key, pinned in the app,
  delegates a short-lived online key, which CI uses inside a protected
  environment. No private key lives in the repository (`AGENTS.md`
  rule 5).
  Where each key lives, and how bundles are released, is in
  `docs/05-release.md` ("Signing and secrets", "Intel bundle releases").

## Limits

| Limit | Value | When exceeded |
| :- | :- | :- |
| IPv4 prefix length | `/16` or longer | The prefix is rejected |
| IPv6 prefix length | `/32` or longer | The prefix is rejected |
| Churn per release | 30% against the previous release | The build fails until a maintainer approves |
| Update checks | Once a day in the background, plus "Update now" | — |

## Rules

- `intel:client` follows `.claude/rules/untrusted-input-parsers.md` (bundle
  manifests are attacker-controlled until their signature is verified) and
  `.claude/rules/kotlin-engine.md`. `checkSourceRules` fails on `!!` under
  `intel/`.
- `.claude/rules/intel-pipeline.md` applies to `tools/intel-pipeline/**`
  and `intel/**`.
- `intel:client` isn't one of the six pure modules, so it gets no explicit
  API mode or coverage gate from the build until open decision #8 is
  settled. Ask the owner before creating it.

## Tests

- **Intel client** (`docs/04-quality.md`, "Feature test plans"): a tampered
  signature, a wrong key ID, a rollback attempt, an expired bundle, a
  partial download, and power loss during the swap (instrumented, with a
  process kill).
- **Fuzzing:** the bundle manifest parser (Jazzer).
- **Forged and rolled-back bundles** (`docs/threat-model.md`, "B3. Threats
  and mitigations"): unit tests with tampered bundles, a CI test that uses
  an unsigned bundle, and unit tests of the monotonic version check.
- **Pipeline** (JUnit on `tools/intel-pipeline`, every PR): source adapters
  against golden files for each source format, validators, signing, and the
  churn gate, including a test that a churn-gate failure blocks the
  release. The CI job starts with the pipeline in Phase 2.
- **M1 exit:** the intel update works end to end on a test channel.

## Open questions

- **NFR-MAINT-01 for `intel:client`** (`docs/00-overview.md`, "Open
  decisions" #8).
