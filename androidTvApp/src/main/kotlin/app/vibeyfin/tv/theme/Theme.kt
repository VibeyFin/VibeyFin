package app.vibeyfin.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import app.vibeyfin.design.VibeyFinTokens

/** Keeps clear of the screen edge (overscan). */
val TvMarginHorizontal = 48.dp
val TvMarginVertical = 27.dp

private val TvColorScheme = with(VibeyFinTokens.Colors) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = onAccentSoft,
        secondary = onAccentSoft,
        onSecondary = accentSoft,
        background = bg,
        onBackground = ink,
        surface = surface2,
        onSurface = ink,
        surfaceVariant = surface3,
        onSurfaceVariant = inkMuted,
        error = danger,
        border = line,
        borderVariant = lineStrong,
    )
}

@Composable
fun VibeyFinTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColorScheme, content = content)
}
