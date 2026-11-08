---
paths:
  - "**/src/test/**"
  - "**/src/androidTest/**"
  - "**/src/jvmTest/**"
  - "testing/**"
---

# Rules for tests and fixtures

Test layers, gates, and malware-sample handling are in `docs/04-quality.md`.
Tests and fixtures are benign, deterministic, and never leak private data.

- **No live malware, ever.** No real malware APKs, DEX files, or
  password-protected sample archives in the repo, its history, or LFS.
  Real-sample evaluation happens in the isolated lab, and only aggregate
  metrics come back into the repo (`docs/04-quality.md`, "Malware sample
  handling").
- **Fixtures are benign and built from source.**
  - `testing/fixtures/` apps *emulate* risky profiles (permissions,
    components, embedded dummy DEX) and do nothing harmful.
  - They are signed with a key whose alias and file name contain `test`.
- **Deterministic tests.** Inject the clock and dispatchers, use `runTest`
  and Turbine for flows, and avoid real network calls, real sleeps, and
  order-dependent state.
- **Prefer fakes** for engine collaborators. Use MockK at Android
  boundaries only.
- **Naming:** one behavior per test.
  - JVM unit tests (`src/test`) use backtick sentence names, such as
    `` `rejects zip with compression ratio above limit` ``.
  - Instrumented tests (`src/androidTest`) can't, because DEX allows spaces
    in names only from API 30 and minSdk is 29. Use camelCase sentence
    names there, such as `launchShowsAppNameAndHonestStatus`.
- **Parser tests** need malformed-input cases plus a Jazzer fuzz target,
  and a regression fixture for every bug the fuzzer finds.
- **Instrumented tests** that touch the VPN, the notification listener, or
  package install use the debug-only bundle and fixture apps, and never
  third-party endpoints.
- **Detection-quality gates** (`docs/04-quality.md`, "Detection quality")
  run from feature dumps, never from APK binaries checked into the repo.
- **No privacy leaks.** Assertions for the scam detector and the Shield
  verify that no message text or query log is persisted (inspect the DB in
  tests).
