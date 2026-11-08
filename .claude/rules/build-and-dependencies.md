---
paths:
  - "**/*.gradle.kts"
  - "gradle/**"
  - "build-logic/**"
  - ".github/workflows/**"
---

# Rules for builds, dependencies, and CI

These files decide what goes into the app and which gates it must pass. The
dependency policy is `docs/02-dependency-policy.md`, and the CI gates are in
`docs/04-quality.md` ("CI (GitHub Actions)").

- **Version catalog only.** Every dependency and plugin is declared in
  `gradle/libs.versions.toml`. Never write inline
  `"group:artifact:version"` strings in module build files.
- **Convention plugins own the settings.** The plugins in `build-logic/` own
  the SDK levels, the Kotlin and Compose settings, lint, detekt, and test
  options. Module build files apply plugins and declare dependencies,
  nothing more.
- **Module dependency rules** (`docs/01-architecture.md`, "Dependency
  rules"):
  - `feature:*` modules never depend on each other.
  - Pure modules never depend on Android libraries.
  - Keep the module-graph check passing.
- **Forbidden dependencies** (NFR-PRIV-02): analytics, ads, attribution,
  and marketing SDKs, and any SDK that makes network calls Aegis doesn't
  control. Crash reporting would need a `docs/privacy.md` update and
  opt-in, so ask first.
- **Before adding a dependency,** prefer platform or AndroidX APIs. Then
  check four things:
  - a license on the allowlist
  - active maintenance
  - transitive size
  - any manifest permissions it merges in
- **Supply chain:** update `gradle/verification-metadata.xml` in the same
  change. Never disable dependency verification or the license check to get
  a build green.
- **Signing:** release signing reads from environment or CI secrets only.
  - Never commit keystores, passwords, or key material.
  - `.gitignore` already excludes `*.jks`, `*.keystore`, and
    `local.properties`.
- **CI workflows:**
  - Pin third-party actions by commit SHA.
  - Default `permissions: read-all` (or narrower).
  - Never expose secrets to `pull_request` events from forks.
  - The intel online signing key is used only in the protected release
    environment (ADR-0006).
- **Don't weaken gates.** Never add `-x test`,
  `lint { abortOnError = false }`, detekt baselines, or `@Suppress` to get a
  change through without the user's explicit approval.
