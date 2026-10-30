package app.vibeyfin.app.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// VibeyFin is dark-only and never uses dynamic color. Every role is set explicitly: Material fills
// unset roles with its baseline purple. Values come from the `android` column of design/tokens.json;
// tertiary mirrors secondary so aqua keeps its single meaning.
private val VibeyFinColorScheme = with(VibeyFinTokens.Colors) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = onAccentSoft,
        inversePrimary = accentPressed,
        secondary = onAccentSoft,
        onSecondary = accentSoft,
        secondaryContainer = accentSoft,
        onSecondaryContainer = onAccentSoft,
        tertiary = onAccentSoft,
        onTertiary = accentSoft,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = onAccentSoft,
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
fun VibeyFinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VibeyFinColorScheme,
        typography = VibeyFinTypography,
    ) {
        // Inside MaterialTheme, which sets its own ripple: this one keeps the ripple and adds the press scale
        CompositionLocalProvider(LocalIndication provides PressScaleIndication, content = content)
    }
}
