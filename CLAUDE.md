@AGENTS.md

## Claude Code specifics

The project rules are in `AGENTS.md` (imported above), shared with any other
coding agent. This section covers only what is specific to Claude Code.

**Project skills** (in `.claude/skills`):

| Skill | Use it to… |
| :- | :- |
| `/implement-requirement <requirement-id>` | Implement an FR-* or NFR-* requirement end to end: spec and gates, tests first, code, docs, review, then `/pre-pr-check` |
| `/next-task [task-id]` | Pick the next unblocked roadmap task, or the one named, and carry it through the definition of done. Only the user invokes it. |
| `/new-module <gradle-path>` | Create a module from `docs/01-architecture.md` ("Module layout") with the convention plugins and dependency rules |
| `/add-permission <permission-or-component>` | Add a permission, special access, sensitive API, exported component, or foreground-service type, keeping the manifest, `docs/permissions-and-policy.md`, `docs/privacy.md`, disclosures, and fallbacks in sync |
| `/add-intel-source <source>` | Onboard an external data source (IOC feed, blocklist, crawler IP ranges, registry data): verify its terms first, then the `docs/data-sources.md` entry, adapter, validation, and attribution |
| `/run-spike <spike-id>` | Plan, run, and write up a Phase 0 spike (S1–S7), then feed its outcome back into ADRs and specs. Only the user invokes it. |
| `/record-decision <title>` | Append an ADR to `docs/decisions.md`, with status *Proposed*. |
| `/pre-pr-check` | Run the definition of done on the uncommitted changes: the gates, doc sync, review agents, a completion summary, and a suggested commit message. It never commits. |

**Path-scoped rules** in `.claude/rules` load automatically when you touch
matching files:

| Rule file | Applies to |
| :- | :- |
| `android-manifest-and-permissions.md` | Every `AndroidManifest.xml`, and `src/main/res/xml/` (such as the Network Security Config) |
| `build-and-dependencies.md` | Gradle build files (`*.gradle.kts`), `gradle/`, `build-logic/`, `.github/workflows/` |
| `intel-pipeline.md` | `tools/intel-pipeline/`, `intel/`, `core/security/` |
| `kotlin-engine.md` | Kotlin in `engine/`, `shield/dns/`, `ai/guard/`, `intel/client/`, `core/model/`, `core/common/` |
| `shield-vpn.md` | `shield/`, `feature/shield/` |
| `tests-and-fixtures.md` | `src/test/`, `src/androidTest/`, and `src/jvmTest/` in any module, and `testing/` |
| `ui-and-copy.md` | `feature/`, `core/ui/`, and `strings.xml` under `src/main/res/values*/` |
| `untrusted-input-parsers.md` | `engine/apk/`, `shield/dns/`, `ai/guard/`, `intel/client/`, and share handling under `app/src/main/**/share/` |

**Review agents** in `.claude/agents` are read-only: they never edit files.
Delegate to them by what changed, then fix each finding or explain why it
doesn't apply.

| Agent | Use it for |
| :- | :- |
| `security-reviewer` | Parsers, crypto and signing, IPC, exported components, the VPN and DNS engine, storage, or networking, checked against `docs/threat-model.md`, the ADRs, and OWASP MASVS. Always for code changes in `engine/`, `shield/`, `intel/`, `core/security/`, `ai/guard/`, manifests, or IPC, and before finishing any security-sensitive task. |
| `policy-privacy-reviewer` | Google Play policy, privacy and data handling, data-source licensing, and honest user-facing claims: manifest or permission changes, new data flows, new data sources, strings and UI copy, and Play submission work. It may read official policy pages on `support.google.com/googleplay/android-developer` and `developer.android.com`. |
| `detection-quality-reviewer` | IOCs, heuristic rules, ML features and models, verdict aggregation, AI guard detectors, blocklists, and crawler ranges: false-positive risk, evasion robustness, evidence quality, and test coverage. Use it whenever detection logic or intel pipeline output changes. |

**Hooks** (`.claude/hooks/`, configured in `.claude/settings.json`) need
`python3` and use only its standard library. CI runs their self-tests
(`python3 .claude/hooks/test_hooks.py`).

- **`pre_tool_guard.py`** runs before every Bash, Edit, Write, and
  MultiEdit call. It enforces non-negotiables that permission rules can't
  express.
  - It blocks downloading malware samples from sample hosts and unpacking
    password-protected sample archives (`docs/04-quality.md`, "Malware
    sample handling"). Hash and metadata queries are fine.
  - It blocks writing secrets (private keys, API tokens) into non-test
    files.
  - It blocks security-weakening Android config (cleartext traffic, trusted
    user CAs, backup, debuggable) and trust-all TLS code.
  - It blocks analytics, ads, and attribution SDK dependencies
    (NFR-PRIV-02).
  - It asks before restricted permissions or an AccessibilityService in a
    production manifest (ADR-0004), and before crash-reporting SDKs, which
    need a `docs/privacy.md` update and opt-in.
  - It fails open if it can't parse its input, so a hook bug never blocks
    all work.
- **`post_edit_checks.py`** runs after every Edit, Write, and MultiEdit.
  The edit has already happened, so its findings are warnings to fix, not
  blocks. Fix them before moving on.
  - Kotlin: Android imports in pure modules, `!!` in engine code,
    `GlobalScope`, `runBlocking`, and direct logging outside the app's
    logcat sink. The last four are early warnings for part of what
    `./gradlew checkSourceRules` fails on in CI, over the same files. Keep
    the hook's prefixes, file scope, and exemptions in step with it.
    Android imports in pure modules fail CI through compilation (those
    modules are pure JVM) and `checkModuleGraph`.
  - `AndroidManifest.xml`: permissions missing from
    `docs/permissions-and-policy.md`, and exported components.
  - Gradle build files: inline dependency versions.
  - `strings.xml`: forbidden security claims (the `ui-and-copy` rule).
  - Any internal error exits cleanly, so a hook bug never blocks work.
- Hooks are guardrails, not a security boundary. CI gates and code review
  remain the enforcement points.

**Permissions** (`.claude/settings.json`):

- It pre-approves `./gradlew` commands, read-only Git (`git status`,
  `git diff`, `git log`, `git show`), `adb devices`, `adb shell getprop`,
  `adb shell dumpsys`, the hook self-tests, and WebFetch on
  `developer.android.com`, `source.android.com`, `kotlinlang.org`,
  `support.google.com`, and `mas.owasp.org`.
- It asks before Git commands that change history or the working tree
  (`commit`, `push`, `rebase`, `reset`, `merge`, `tag`, `cherry-pick`,
  `revert`, `clean`, `restore`, `stash`, `branch -D`), `gh pr create`,
  `gh release`, Gradle tasks that publish or upload, `adb install`,
  `adb uninstall`, `adb shell pm`, and `adb shell settings put`.
- It denies force pushes, and reading secrets: `local.properties`,
  `keystore.properties`, keystores and key files (`*.jks`, `*.keystore`,
  `*.p12`, `*.pfx`, `*.key`), `google-services.json`, and `.env` files.
- Personal overrides go in `.claude/settings.local.json` and
  `CLAUDE.local.md`, which are both gitignored.
- These are guardrails, not a security boundary. The rules in `AGENTS.md`
  still apply when a command slips past a pattern.

**Working style:**

- Start a requirement with `/implement-requirement <ID>`. The user starts
  the next roadmap task with `/next-task` and a Phase 0 spike with
  `/run-spike S<n>`; suggest them, since you can't invoke either one.
- Stop and ask the user whenever a step turns up ambiguity, a conflict
  between docs, or a change that is hard to reverse.
- Run the narrowest relevant check first (`./gradlew :<module>:test`), then
  the broader gates.
- Instrumented tests need a device or emulator. Run them if `adb devices`
  shows one; otherwise list them as not run. CI is set up to run them on
  API 29 and API 37 emulators, but CI on `main` currently fails on
  dependency verification (`README.md`, "Status"), so the API 29 tests have
  not run anywhere yet.
- Record a decision with `/record-decision`. Only the user marks an ADR
  *Accepted*.
- Delegate reviews to the agents above, by what changed.
- Finish with `/pre-pr-check`. Don't commit: end with the completion
  summary from `AGENTS.md` ("Definition of done for a roadmap task") and a
  suggested Conventional Commit message.
