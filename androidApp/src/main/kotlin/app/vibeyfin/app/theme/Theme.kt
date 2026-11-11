package app.vibeyfin.app.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// VibeyFin is dark-only and never uses dynamic color. Every role is set explicitly: Material fills
// unset roles with its baseline purple. Values come from the theme's colours, generated from design/tokens.json.
private fun colorSchemeFor(colors: ThemeColors): ColorScheme = with(colors) {
    darkColorScheme(
        primary = action,
        onPrimary = onAction,
        primaryContainer = tonal,
        onPrimaryContainer = onTonal,
        inversePrimary = actionPressed,
        secondary = onTonal,
        onSecondary = tonal,
        secondaryContainer = tonal,
        onSecondaryContainer = onTonal,
        tertiary = spark,
        onTertiary = onSpark,
        tertiaryContainer = sparkSoft,
        onTertiaryContainer = onSparkSoft,
        background = bg,
        onBackground = ink,
        surface = bg,
        onSurface = ink,
        surfaceVariant = surface3,
        onSurfaceVariant = inkMuted,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = bg,
        error = danger,
        onError = bg,
        errorContainer = surface3,
        onErrorContainer = danger,
        outline = lineStrong,
        outlineVariant = line,
        scrim = scrim,
        surfaceBright = surface3,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface1,
        surfaceContainer = surface2,
        surfaceContainerHigh = surface3,
        surfaceContainerHighest = surface3,
    )
}

@Composable
fun VibeyFinTheme(themeId: String? = null, content: @Composable () -> Unit) {
    val colors: ThemeColors = remember(themeId) { ThemePalettes.byId(themeId) }

    activeThemeColors = colors

    MaterialTheme(
        colorScheme = remember(colors) { colorSchemeFor(colors) },
        typography = VibeyFinTypography,
    ) {
        // Inside MaterialTheme, which sets its own ripple: this one keeps the ripple and adds the press scale
        CompositionLocalProvider(LocalIndication provides PressScaleIndication, content = content)
    }
}
