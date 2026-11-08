# Subsystem: `core:ui` — design system

The design system every screen uses: the Material 3 theme with Aegis's
colors, the status colors, and the components that show the status of the
device or of one protection module (`docs/01-architecture.md`, "Module
layout"). It's an Android library with Compose (`aegis.android.library`,
`aegis.android.compose`) and exposes Material 3 and Compose UI as `api`
dependencies. Phase 0 built the theme, the five status levels with their
icons and labels, `StatusIndicator`, `StatusCard`, and their previews. The
`app` shell uses them today (`app.md`), and the feature screens build on
them from Phase 1 (`features.md`).

## Contents

| Package or file | Provides |
| :- | :- |
| `theme` | `AegisTheme` (the composable and the object), `StatusColors`; internally the light and dark color schemes, the status colors, the typography and `LocalStatusColors` |
| `component` | `StatusLevel`, `StatusIndicator`, `StatusCard`, and their previews |
| `res/values/strings.xml` | The status labels |
| `res/drawable` | `aegis_ic_status_ok`, `_review`, `_action_needed`, `_off` and `_not_checked`: one Material icon per status level (Apache-2.0) |

The namespace is `io.github.jabbottiii.aegis.core.ui`, from the Gradle
path, and resources carry the `aegis_` prefix.

## Key types

```kotlin
@Composable
fun AegisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,              // used on Android 12+ only
    content: @Composable () -> Unit,
)

object AegisTheme {
    val statusColors: StatusColors             // @Composable, @ReadOnlyComposable
}

@Immutable
data class StatusColors(val ok: Color, val review: Color, val actionNeeded: Color, val inactive: Color)

enum class StatusLevel(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    OK, REVIEW, ACTION_NEEDED, OFF, NOT_CHECKED,
}

@Composable
fun StatusIndicator(level: StatusLevel, modifier: Modifier = Modifier, label: String = stringResource(level.labelRes))

@Composable
fun StatusCard(title: String, level: StatusLevel, description: String, modifier: Modifier = Modifier)
```

## Status levels

`StatusLevel` is the status of the device or of one protection module
(FR-UX-02). The labels describe what was observed, never a guarantee.

| Level | Label | Icon (Material Icons) | Status color, light / dark |
| :- | :- | :- | :- |
| `OK` | "No issues found" | `check_circle` | `ok`: `#2E7D32` / `#81C784` |
| `REVIEW` | "Review recommended" | `warning` | `review`: `#8A5100` / `#FFB951` |
| `ACTION_NEEDED` | "Action needed" | `error` | `actionNeeded`: `#BA1A1A` / `#FFB4AB` |
| `OFF` | "Off" | `remove_circle_outline` | `inactive`: `#5B6168` / `#C1C7CE` |
| `NOT_CHECKED` | "Not checked yet" | `help_outline` | `inactive` |

## Theme

- **Light, dark and dynamic color** (FR-UX-04). The theme follows the
  system's dark setting. With `dynamicColor` on Android 12+, it uses the
  system's dynamic color scheme. Otherwise Aegis's own schemes apply:
  primary `#1F5E8C` in light (the launcher icon's background color) and
  `#9CCBFB` in dark, with matching secondary and tertiary colors. Previews
  pass `dynamicColor = false`.
- **Typography:** the Material 3 type scale with the system font, so text
  follows the user's font scale, up to the 200% that FR-UX-04 requires.
- **Status colors are separate.** `AegisTheme` provides `LightStatusColors`
  or `DarkStatusColors` through `LocalStatusColors`, chosen by `darkTheme`
  alone, so dynamic color never changes them. Code reads them through
  `AegisTheme.statusColors`.

## Accessibility

- **Never color alone** (NFR-A11Y-01). Every status level has an icon and a
  text label as well as a color. The label carries the meaning, so TalkBack
  reads it.
- **The icon is decorative:** no content description, 24 dp, tinted with
  the level's status color.
- **One announcement per card.** `StatusIndicator` sets no merge boundary of
  its own, so a parent that merges reads its label as part of that parent's
  announcement. `StatusCard` merges its descendants, so TalkBack reads the
  title, the status label and the description together as one node.
- **Previews** show `StatusIndicator` for every level in light and dark,
  and `StatusCard` in light, dark and 200% font scale.

## Rules

`.claude/rules/ui-and-copy.md` applies to `core/ui/**`: copy describes what
Aegis does in platform terms and states only verified protection
(`AGENTS.md` rule 10).

## Tests

- `StatusSemanticsTest` (instrumented): every status level shows its text
  label; a `StatusCard` is one TalkBack node whose texts are the title, the
  status label and the description, in that order.
- UI modules aren't coverage-gated. Instead they need screenshot or
  semantic tests for every state (`docs/04-quality.md`, "Test layers").
- Instrumented test names can't contain spaces: DEX allows them only from
  API 30, and minSdk is 29.
