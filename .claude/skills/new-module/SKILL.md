---
name: new-module
description: Create a new Gradle module in the Aegis multi-module layout using the project convention plugins and dependency rules. Use when adding a module listed in ARCHITECTURE §3 or proposing a new one.
argument-hint: "[gradle path, e.g. :engine:signatures]"
arguments: [module]
---

# New module $module

## 1. Check it belongs

- Is `$module` listed in `docs/ARCHITECTURE.md` §3? If it isn't, explain to the user why it's needed and get agreement first. A new module is an architecture change.
- Decide its type from the dependency rules. The convention plugins are listed in `docs/DEVELOPMENT.md` § Project layout.
  - **Pure Kotlin/JVM engine or core** (`aegis.jvm.engine`): exactly the modules in `ModuleGraphRules.PURE_MODULES` (`build-logic/convention/src/main/kotlin/io/github/jabbottiii/aegis/buildlogic/ModuleGraph.kt`). These are `engine:apk`, `engine:signatures`, `engine:heuristics`, `shield:dns`, `core:model`, and `core:common`. `checkModuleGraph` fails if one of them doesn't apply `aegis.jvm.engine`, applies an Android plugin, or reaches an Android module or library on a production classpath. Tests may use Android libraries (such as Robolectric), but a pure module can't declare a dependency on an Android module in any configuration, including tests. Making another module pure (for example `ai:guard`, which is still open) is an architecture change. Get the user's agreement, then update `PURE_MODULES`, CLAUDE.md § Architecture rules, ARCHITECTURE §3, `PURE_MODULE_PREFIXES` in `.claude/hooks/post_edit_checks.py`, and the `kotlin-engine` rule together.
  - **Plain Kotlin/JVM** (`aegis.jvm.library`): JVM modules that don't need the engine gates, such as `testing:common`.
  - **Android library** (`aegis.android.library`, plus `aegis.android.compose` for UI and `aegis.hilt` for DI): everything else.
  - **Engine modules outside the pure six** (`engine:scanner`, `engine:ml`, `intel:client`, or a non-pure `ai:guard`): neither `aegis.android.library` nor `aegis.jvm.library` adds explicit API mode or the Kover gate. How NFR-MAINT-01 is enforced for them is an open owner decision (PLAN §6 #8). Ask the user before you create one.
  - **Fixture APKs** (`testing/fixtures/*`): `aegis.android.application` refuses any module other than `:app`, so the first fixture needs its own convention plugin. That plugin gives fixtures their own applicationIds and signs them with a key whose alias and file name contain `test` (TESTING §2). It never uses Aegis's applicationId or release signing. Keystores are gitignored and must never be committed (`build-and-dependencies` rule). So the plugin generates a throwaway `*-test` keystore under `build/` at build time, the way CI makes its throwaway release key. If you think a committed test keystore is needed, ask the user first. In the same change, add the plugin class to `build-logic`, a `gradlePlugin { register(...) }` entry to `build-logic/convention/build.gradle.kts`, an alias to the `[plugins]` section of `gradle/libs.versions.toml`, and a row to the DEVELOPMENT plugin table.
  - **`tools:intel-pipeline`** (a JVM command-line tool run in CI, Phase 2): no convention plugin fits yet. `aegis.jvm.library` has no application entry point, and `checkSourceRules` would fail on a CLI's console output. Ask the user how to handle it, for example with an `aegis.jvm.cli` plugin and a source-rules exemption for that module. Add the plugin the same way as the fixture plugin.
- A Gradle path segment with a hyphen (`intel-pipeline`) doesn't make a valid Kotlin package. Ask the user how to map it before you derive the package or namespace (for example `intelpipeline`), because `aegisNamespace` copies the path as is.

## 2. Create it

- The directory comes from the Gradle path (`:engine:signatures` becomes `engine/signatures/`).
- The `build.gradle.kts` applies **only** convention plugins and declares dependencies through catalog aliases. It has no SDK levels, Kotlin options, or inline versions.
- The package comes from the Gradle path under the base package in CLAUDE.md § Conventions: `:engine:signatures` becomes `io.github.jabbottiii.aegis.engine.signatures`.
- Source sets: `src/main/kotlin`, plus `src/test/kotlin`, plus `src/androidTest/kotlin` for Android modules that need device tests.
- `aegis.android.library` sets the namespace from the Gradle path, so don't set one in the module. A manifest is needed only if the module declares components or permissions. If it does, run `/add-permission` and re-check the merged release manifest.
- Add the module to `settings.gradle.kts`.
- Dependency verification is strict. If the module brings in a library the build doesn't already use, regenerate `gradle/verification-metadata.xml` (DEVELOPMENT.md § Dependencies) and review its diff. Then run `./gradlew licensee`. For an Android module, also re-check the merged release manifest for permissions or components the new library merges in, and run `/add-permission` for any that appear.

## 3. Wire the dependencies

- Depend only on what the dependency rules allow. `feature:*` modules never depend on each other.
- Expose a small public API, and keep the implementation `internal`. `aegis.jvm.engine` enforces explicit API mode. In other engine modules, follow what the owner decided for PLAN §6 #8.

## 4. Verify

Run these and report the actual results:

```bash
./gradlew checkModuleGraph checkSourceRules
./gradlew $module:build
./gradlew $module:koverVerify   # aegis.jvm.engine modules only
./gradlew detekt ktlintCheck
```

If you changed `build-logic`, also run `./gradlew :build-logic:convention:test`. If you changed `.claude/hooks/`, run `python3 .claude/hooks/test_hooks.py`. Finish with `/pre-pr-check`.

## 5. Docs

If the module is new or its responsibility changed, update ARCHITECTURE §3 and §4 in the same change. Also update every list of the modules that exist so far:
- the "Phase 0 created …" sentence at the end of ARCHITECTURE §3. Reword it rather than just appending, because later modules aren't from Phase 0.
- the "only `core:model` and `core:common` exist so far" note in the `kotlin-engine` rule, if you created a pure module
- the CLAUDE.md **Status** line

Don't commit.
