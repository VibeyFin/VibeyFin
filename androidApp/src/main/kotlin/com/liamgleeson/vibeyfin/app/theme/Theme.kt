package com.liamgleeson.vibeyfin.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// VibeyFin is dark-only. Every role is set explicitly: Material fills unset roles with its baseline
// purple, which would leak into components we haven't styled yet. Secondary and tertiary stay
// neutral so amber (primary) and teal (VibeyFinPalette.Available) keep their meaning.
private val VibeyFinColorScheme = darkColorScheme(
    primary = VibeyFinPalette.Marquee,
    onPrimary = VibeyFinPalette.OnMarquee,
    primaryContainer = VibeyFinPalette.SurfaceHigh,
    onPrimaryContainer = VibeyFinPalette.Text,
    secondary = VibeyFinPalette.TextMuted,
    onSecondary = VibeyFinPalette.Ink,
    secondaryContainer = VibeyFinPalette.SurfaceHigh,
    onSecondaryContainer = VibeyFinPalette.Text,
    tertiary = VibeyFinPalette.TextMuted,
    onTertiary = VibeyFinPalette.Ink,
    tertiaryContainer = VibeyFinPalette.SurfaceHigh,
    onTertiaryContainer = VibeyFinPalette.Text,
    background = VibeyFinPalette.Ink,
    onBackground = VibeyFinPalette.Text,
    surface = VibeyFinPalette.Surface,
    onSurface = VibeyFinPalette.Text,
    surfaceVariant = VibeyFinPalette.SurfaceHigh,
    onSurfaceVariant = VibeyFinPalette.TextMuted,
    surfaceTint = VibeyFinPalette.Surface,
    surfaceDim = VibeyFinPalette.Ink,
    surfaceBright = VibeyFinPalette.SurfaceHigh,
    surfaceContainerLowest = VibeyFinPalette.Ink,
    surfaceContainerLow = VibeyFinPalette.Surface,
    surfaceContainer = VibeyFinPalette.Surface,
    surfaceContainerHigh = VibeyFinPalette.SurfaceHigh,
    surfaceContainerHighest = VibeyFinPalette.SurfaceHigh,
    outline = VibeyFinPalette.TextMuted,
    outlineVariant = VibeyFinPalette.SurfaceHigh,
)

@Composable
fun VibeyFinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VibeyFinColorScheme,
        typography = VibeyFinTypography,
        content = content,
    )
}
