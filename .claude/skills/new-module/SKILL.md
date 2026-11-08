---
name: new-module
description: Use when adding a Gradle module listed in docs/01-architecture.md ("Module layout"), or proposing a new one. Creates it in the Aegis multi-module layout with the project's convention plugins and dependency rules.
argument-hint: "<gradle path, e.g. :engine:signatures>"
arguments: [module]
---

# Create a module

Module: `$module`

The module layout is fixed (`docs/01-architecture.md`, "Module layout"),
and `checkModuleGraph` enforces its dependency rules. A module outside the
layout is an architecture change.

## 1. Check that it belongs

- Is `$module` listed in `docs/01-architecture.md` ("Module layout")? If
  it isn't, explain to the user why it's needed and get their agreement
  first. A new module is an architecture change.
- Decide its type from the dependency rules. The convention plugins are
  listed in `docs/01-architecture.md` ("Convention plugins").
  - **Pure Kotlin/JVM engine or core** (`aegis.jvm.engine`): exactly the
    modules in `ModuleGraphRules.PURE_MODULES`
    (`build-logic/convention/src/main/kotlin/io/github/jabbottiii/aegis/buildlogic/ModuleGraph.kt`).
    These are `engine:apk`, `engine:signatures`, `engine:heuristics`,
    `shield:dns`, `core:model`, and `core:common`.
    - `checkModuleGraph` fails if one of them doesn't apply
      `aegis.jvm.engine`, applies an Android plugin, or reaches an Android
      module or library on a production classpath.
    - Tests may use Android libraries (such as Robolectric), but a pure
      module can't declare a dependency on an Android module in any
      configuration, including tests.
    - Making another module pure (for example `ai:guard`, which is still
      open) is an architecture change. Get the user's agreement, then
      update these together: `PURE_MODULES`, `AGENTS.md` ("Architecture
      rules" and the "Module map" row), `docs/01-architecture.md`
      ("Module layout", including "Dependency rules"),
      `docs/02-dependency-policy.md` ("The rule"), the module's subsystem
      spec in `docs/subsystems/`, `PURE_MODULE_PREFIXES` in
      `.claude/hooks/post_edit_checks.py`, and
      `.claude/rules/kotlin-engine.md`.
  - **Plain Kotlin/JVM** (`aegis.jvm.library`): JVM modules that don't
    need the engine gates, such as `testing:common`.
  - **Android library** (`aegis.android.library`, plus
    `aegis.android.compose` for UI and `aegis.hilt` for DI): everything
    else.
  - **Engine modules outside the pure six** (`engine:scanner`,
    `engine:ml`, `intel:client`, or a non-pure `ai:guard`): neither
    `aegis.android.library` nor `aegis.jvm.library` adds explicit API mode
    or the Kover gate. How NFR-MAINT-01 is enforced for them is an open
    owner decision (`docs/00-overview.md`, "Open decisions" #8). Ask the
    user before you create one.
  - **Fixture APKs** (`testing/fixtures/*`): `aegis.android.application`
    refuses any module other than `:app`, so the first fixture needs its
    own convention plugin.
    - That plugin gives fixtures their own applicationIds, and signs them
      with a key whose alias and file name contain `test`
      (`docs/04-quality.md`, "Malware sample handling"). It never uses
      Aegis's applicationId or release signing.
    - Keystores are gitignored and must never be committed
      (`.claude/rules/build-and-dependencies.md`). So the plugin generates
      a throwaway `*-test` keystore under `build/` at build time, the way
      CI makes its throwaway release key. If you think a committed test
      keystore is needed, ask the user first.
    - In the same change, add the plugin class to `build-logic`, a
      `gradlePlugin { register(...) }` entry to
      `build-logic/convention/build.gradle.kts`, an alias to the
      `[plugins]` section of `gradle/libs.versions.toml`, and a row to the
      plugin table in `docs/01-architecture.md` ("Convention plugins").
  - **`tools:intel-pipeline`** (a JVM command-line tool run in CI,
    Phase 2): no convention plugin fits yet. `aegis.jvm.library` has no
    application entry point, and `checkSourceRules` would fail on a CLI's
    console output. Ask the user how to handle it, for example with an
    `aegis.jvm.cli` plugin and a source-rules exemption for that module.
    Add the plugin the same way as the fixture plugin.
- A Gradle path segment with a hyphen (`intel-pipeline`) doesn't make a
  valid Kotlin package. Ask the user how to map it (for example
  `intelpipeline`) before you derive the package or namespace, because
  `aegisNamespace` copies the path as is.

## 2. Create it

- The directory comes from the Gradle path (`:engine:signatures` becomes
  `engine/signatures/`).
- The `build.gradle.kts` applies **only** convention plugins and declares
  dependencies through catalog aliases. It has no SDK levels, Kotlin
  options, or inline versions.
- The package comes from the Gradle path under the base package in
  `AGENTS.md` ("Conventions"): `:engine:signatures` becomes
  `io.github.jabbottiii.aegis.engine.signatures`.
- Source sets: `src/main/kotlin`, plus `src/test/kotlin`, plus
  `src/androidTest/kotlin` for Android modules that need device tests.
- `aegis.android.library` sets the namespace from the Gradle path, so
  don't set one in the module. A manifest is needed only if the module
  declares components or permissions. If it does, run `/add-permission`
  and re-check the merged release manifest.
- Add the module to `settings.gradle.kts`.
- Dependency verification is strict. If the module brings in a library
  the build doesn't already use:
  1. Regenerate `gradle/verification-metadata.xml`
     (`docs/02-dependency-policy.md`, "Dependency verification") and
     review its diff.
  2. Run `./gradlew licensee`.
  3. For an Android module, also re-check the merged release manifest for
     permissions or components the new library merges in, and run
     `/add-permission` for any that appear.

## 3. Wire the dependencies

- Depend only on what the dependency rules allow. `feature:*` modules
  never depend on each other.
- Expose a small public API, and keep the implementation `internal`.
  `aegis.jvm.engine` enforces explicit API mode. In other engine modules,
  follow what the owner decided for open decision #8
  (`docs/00-overview.md`, "Open decisions").

## 4. Verify

Run these and report the actual results:

```bash
./gradlew checkModuleGraph checkSourceRules
./gradlew $module:build
./gradlew $module:koverVerify   # aegis.jvm.engine modules only
./gradlew detekt ktlintCheck
```

- If you changed `build-logic`, also run
  `./gradlew :build-logic:convention:test`.
- If you changed `.claude/hooks/`, run `python3 .claude/hooks/test_hooks.py`.
- Finish with `/pre-pr-check`.

## 5. Update the docs

If the module is new or its responsibility changed, update
`docs/01-architecture.md` ("Module layout" and "Components") in the same
change, and the subsystem spec in `docs/subsystems/` that covers the
module (its "Contents" table). Also update every list of the modules that
exist so far, including:

- the "The modules that exist so far are …" sentence right after the tree
  in `docs/01-architecture.md` ("Module layout"), and the `planned`
  marker on the module's line in that tree. Reword the sentence rather
  than just appending, because later modules aren't from Phase 0.
- the status sentence in the intro of the module's subsystem spec in
  `docs/subsystems/` (for example "Nothing is built yet")
- for a `testing:fixtures` module, the "which doesn't exist yet" note in
  `docs/04-quality.md` ("Malware sample handling")
- the "only `core:model` and `core:common` exist so far" note in
  `.claude/rules/kotlin-engine.md`, if you created a pure module
- the **Status:** paragraph in `AGENTS.md`, and the module's row in its
  "Module map" (the Status column)
- `README.md` ("Status"), if it lists them

Don't commit.
