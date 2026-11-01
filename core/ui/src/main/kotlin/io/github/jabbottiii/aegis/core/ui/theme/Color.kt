package io.github.jabbottiii.aegis.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val AegisLightColors =
    lightColorScheme(
        primary = Color(0xFF1F5E8C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFE5FF),
        onPrimaryContainer = Color(0xFF001D33),
        secondary = Color(0xFF526070),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD5E4F7),
        onSecondaryContainer = Color(0xFF0F1D2A),
        tertiary = Color(0xFF3E6A4F),
        onTertiary = Color(0xFFFFFFFF),
    )

internal val AegisDarkColors =
    darkColorScheme(
        primary = Color(0xFF9CCBFB),
        onPrimary = Color(0xFF003354),
        primaryContainer = Color(0xFF00497A),
        onPrimaryContainer = Color(0xFFCFE5FF),
        secondary = Color(0xFFB9C8DA),
        onSecondary = Color(0xFF243240),
        secondaryContainer = Color(0xFF3B4857),
        onSecondaryContainer = Color(0xFFD5E4F7),
        tertiary = Color(0xFFA4D3B3),
        onTertiary = Color(0xFF0B3822),
    )

/**
 * Colors for [io.github.jabbottiii.aegis.core.ui.component.StatusLevel]. Status is never shown by
 * color alone (NFR-A11Y-01): every status also has an icon and a text label.
 */
@Immutable
data class StatusColors(
    val ok: Color,
    val review: Color,
    val actionNeeded: Color,
    val inactive: Color,
)

internal val LightStatusColors =
    StatusColors(
        ok = Color(0xFF2E7D32),
        review = Color(0xFF8A5100),
        actionNeeded = Color(0xFFBA1A1A),
        inactive = Color(0xFF5B6168),
    )

internal val DarkStatusColors =
    StatusColors(
        ok = Color(0xFF81C784),
        review = Color(0xFFFFB951),
        actionNeeded = Color(0xFFFFB4AB),
        inactive = Color(0xFFC1C7CE),
    )

internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
