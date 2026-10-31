---
name: new-module
description: Create a new Gradle module in the Aegis multi-module layout using the project convention plugins and dependency rules. Use when adding a module listed in ARCHITECTURE §3 or proposing a new one.
argument-hint: "[gradle path, e.g. :engine:signatures]"
arguments: [module]
---

# New module $module

## 1. Check it belongs

- Is `$module` listed in `docs/ARCHITECTURE.md` §3? If it isn't, explain to the user why it's needed and get agreement first. A new module is an architecture change.
- Decide its type from the dependency rules:
  - **Pure Kotlin/JVM** (`aegis.jvm.engine`): `engine:apk`, `engine:signatures`, `engine:heuristics`, `shield:dns`, `core:model`, `core:common`
  - **Android library** (`aegis.android.library`, plus `aegis.android.compose` for UI and `aegis.hilt` for DI): everything else
- If `build-logic/` or the convention plugins don't exist yet, this is Phase 0 scaffolding work. Create them first, following `docs/DEVELOPMENT.md` § Project layout.

## 2. Create it

- The directory comes from the Gradle path (`:engine:signatures` becomes `engine/signatures/`).
- The `build.gradle.kts` applies **only** convention plugins and declares dependencies through catalog aliases. It has no SDK levels, Kotlin options, or inline versions.
- The package is `<basePackage>.<area>.<name>`. The base package is recorded in CLAUDE.md § Conventions. If it's still TBD, ask the user.
- Source sets: `src/main/kotlin`, plus `src/test/kotlin`, plus `src/androidTest/kotlin` for Android modules that need device tests.
- Android library modules need a namespace in the convention-plugin style. A manifest is needed only if the module declares components.
- Add the module to `settings.gradle.kts`.

## 3. Wire the dependencies

- Depend only on what the dependency rules allow. `feature:*` modules never depend on each other.
- Expose a small public API (explicit API mode in engine modules), and keep the implementation `internal`.

## 4. Verify

Run `./gradlew :<path>:build` (or `:<path>:test` for JVM modules) and the module-graph check, if it exists. Report the actual results.

## 5. Docs

If the module is new or its responsibility changed, update ARCHITECTURE §3 and §4 in the same change. Don't commit.
