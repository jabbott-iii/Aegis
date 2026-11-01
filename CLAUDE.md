# Aegis: instructions for Claude

Aegis is an Android security app in Kotlin. It covers antivirus scanning, a device security audit,
DNS-level network protection, AI threat defenses, and an opt-in AI Crawler Shield.

**Status:** **Phase 0** is in progress. The Gradle scaffold, the CI workflows, and the foundation
modules (`app`, `core:common`, `core:data`, `core:model`, `core:security`, `core:ui`,
`testing:common`) exist, including encrypted Room and the settings DataStore. Still to do in
Phase 0: spikes S1–S7. See `docs/PLAN.md`.
Keep "Commands" below in step with what actually runs.

## Where the truth lives

Read the relevant doc **before** you change behavior. Don't re-derive decisions the docs already make.

| Need | Read |
|---|---|
| Scope, phases, spikes, risks, open decisions | `docs/PLAN.md` |
| Requirement IDs (FR-*, NFR-*) and priorities | `docs/REQUIREMENTS.md` |
| Modules, processes, flows, data model, stack | `docs/ARCHITECTURE.md` |
| Feature behavior and acceptance criteria | `docs/features/*.md` |
| Threats, trust boundaries, review triggers | `docs/THREAT_MODEL.md` |
| Permissions, Play policy, disclosures | `docs/PERMISSIONS_AND_POLICY.md` |
| Data inventory, what leaves the device | `docs/PRIVACY.md` |
| External data, licenses, verification status | `docs/DATA_SOURCES.md` |
| Test layers, gates, malware-sample handling | `docs/TESTING.md` |
| Conventions, CI gates, secrets, releases | `docs/DEVELOPMENT.md` |
| Decisions and their rationale | `docs/adr/*.md` |

If code and docs disagree, stop and ask which one is right. Don't silently "fix" either.

## Non-negotiables

Each of these comes from an ADR or an NFR. Changing one requires a new or revised ADR **and** the user's explicit approval.

1. **No TLS interception**: no CA installation, no decryption, and no HTTP payload or user-agent inspection (ADR-0002).
2. **No AccessibilityService** in the Aegis app (ADR-0004). Benign test fixtures under `testing/fixtures/` may declare one so that they emulate risky apps.
3. **No LLM in the verdict path.** Verdicts come from IOCs, rules, or fixed-threshold classifiers (ADR-0005).
4. **ML alone never yields *Malicious*.** At most *Suspicious* (malware-detection spec §3.6).
5. **Verify the signature before parsing any intel bundle content.** Use two-tier Ed25519 signing, and never put a private key in the repo or in logs (ADR-0006).
6. **Untrusted input** (APKs, archives, DEX, DNS packets, QR codes, media, shared text) is parsed with size, depth, and time limits. APK, DEX, and media parsing runs in the `:parser` isolated process (NFR-SEC-02).
7. **No data leaves the device** except signed bundle downloads and forwarded DNS (NFR-PRIV-01). No analytics, ads, or attribution SDKs (NFR-PRIV-02).
8. **No PII in logs.** Use only the `core:common` logging facade. Never log package names, domains, URLs, file names, or message text in release builds.
9. **Shield fails open** with a visible alert. It never silently breaks connectivity (FR-NET-09).
10. **Honest claims.** UI copy states only protection that has been verified. "Inbound" crawler blocking stays forbidden until spike S3 passes (FR-ACS-08).
11. **Every new permission or exported component** updates `docs/PERMISSIONS_AND_POLICY.md` in the same change, and comes with a disclosure and a fallback. Use `/add-permission`.
12. **Never put live malware in the repo, its history, or LFS.** Never download samples into this working tree (TESTING §2).
13. **Stalkerware:** notifications use neutral wording, and removal happens only through the safety flow. Never auto-remove (device-security-audit §4).

## Architecture rules

- The module layout is fixed in ARCHITECTURE §3. `feature:*` modules never depend on each other.
- `engine:apk`, `engine:signatures`, `engine:heuristics`, `shield:dns`, `core:model`, and `core:common` are **pure Kotlin/JVM**: no `android.*` imports. Android adapters live in `engine:scanner`, `shield:vpn`, and `app`.
- Only `core:security` touches the Keystore and Tink. Only `intel:client` does network I/O for bundles.
- Processes: main, `:shield` (VpnService), and `:parser` (`isolatedProcess`). Input reaches the parser as a `ParcelFileDescriptor` over AIDL.
- Path-scoped rules in `.claude/rules/` add detail for each area. They load automatically when you touch matching files.

## Conventions

- `ktlint_official` code style (`.editorconfig`), enforced by ktlint, plus detekt. Engine modules use explicit API mode and no `!!`. Inject dispatchers and the clock. Never use `GlobalScope`.
- Parsers return `ParseResult.Rejected(reason)` instead of throwing on hostile input.
- All dependencies go in `gradle/libs.versions.toml`, with no inline versions.
- Commit messages follow Conventional Commits (`feat(shield): …`). Branches: `feat/…`, `fix/…`, `docs/…`, `spike/S<n>-…`.
- Base package and applicationId: **`io.github.jabbottiii.aegis`** (chosen 2026-11-01, ADR-0007; the applicationId can't change after the first Play upload). Library modules use `io.github.jabbottiii.aegis.<area>.<name>`, with the namespace derived from the Gradle path. `:app` uses the base package.
- minSdk **29**, compileSdk and targetSdk **37** (chosen 2026-11-01, ADR-0007). These live in `gradle/libs.versions.toml` and only the convention plugins read them.

## Commands

These match the CI gates (`.github/workflows/ci.yml`, DEVELOPMENT.md § CI gates). CI's plain `detekt` has no type resolution, so don't rely on type-resolved detekt rules. `checkSourceRules` covers the critical ones. Don't claim any
of them passed unless you ran them and saw the result.

```bash
./gradlew checkModuleGraph checkSourceRules   # module rules (ARCHITECTURE §3) + no direct logging, dispatchers, GlobalScope, engine !!
./gradlew :build-logic:convention:test         # tests for those checks
./gradlew assembleDebug assembleRelease
./gradlew test koverVerify            # unit tests + the ≥ 80% engine coverage gate
./gradlew lint detekt ktlintCheck
./gradlew licensee                    # dependency license allowlist
./gradlew :<module>:test              # one module
./gradlew connectedDebugAndroidTest   # needs an emulator or device
python3 .claude/hooks/test_hooks.py   # Claude Code hook self-tests
./gradlew ktlintFormat                # auto-fix formatting
```

- The Gradle daemon runs on **JDK 21** (`gradle/gradle-daemon-jvm.properties`), because detekt 1.23 can't start on JDK 25. Gradle doesn't download one. A JDK 21 must be on `JAVA_HOME` or `PATH` or in a detected location. To use Android Studio's JBR, set `JAVA_HOME` to it.
- Point Gradle at the SDK with `ANDROID_HOME` or with `sdk.dir` in the gitignored `local.properties`.
- Dependency verification is strict. After any dependency change, regenerate `gradle/verification-metadata.xml` (DEVELOPMENT.md § Dependencies) and review its diff.

## How to work here

1. **Find the requirement.** Every change maps to FR, NFR, or spike IDs. Use `/implement-requirement <ID>`.
2. **Check the gates.** Some work waits on a spike (for example, AI Crawler Shield copy waits on S3, and route caps wait on S2). Use `/run-spike S<n>` for Phase 0 spikes.
3. **Test first** for engine logic. Every parser gets a fuzz harness.
4. **Update docs in the same change:** the spec, plus PERMISSIONS_AND_POLICY, PRIVACY, or THREAT_MODEL whenever its review triggers apply. Record new decisions with `/new-adr`.
5. **Review** security-sensitive changes with the `security-reviewer` agent. Use `policy-privacy-reviewer` for manifest, permission, data-flow, or UI-claim changes, and `detection-quality-reviewer` for rule, model, IOC, or verdict changes.
6. **Finish** with `/pre-pr-check`.

Other guidance:

- **Git:** do **not** commit, amend, push, rebase, reset, or tag. Leave changes uncommitted for the user to review, and offer a commit message instead.
- **Ask first** about anything ambiguous or hard to reverse, and about any change that would weaken a security control or break compatibility. State the conflict and propose the safest option.
- **Completion summary:** report what changed, which files were affected, what validation you ran, what you didn't run, your assumptions, open issues, and residual risks.

## Claude Code setup in this repo

- `.claude/settings.json` holds the shared permissions and hooks. Personal overrides go in `.claude/settings.local.json`, which is gitignored.
- The hooks require `python3` (stdlib only):
  - `pre_tool_guard.py` blocks secret commits, security-weakening config, forbidden SDKs, and malware downloads. It asks before restricted permissions.
  - `post_edit_checks.py` warns about Kotlin rule violations, undocumented permissions, inline dependency versions, and forbidden UI claims.
- Skills live in `.claude/skills/` and agents in `.claude/agents/`.
