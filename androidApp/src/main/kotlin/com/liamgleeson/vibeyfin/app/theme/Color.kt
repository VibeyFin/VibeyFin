package com.liamgleeson.vibeyfin.app.theme

import androidx.compose.ui.graphics.Color

/**
 * The VibeyFin palette. Dark blue-ink base; amber is reserved for the single primary action on a
 * screen (Play, Sign in) and teal only for availability. Artwork carries the colour elsewhere.
 */
object VibeyFinPalette {
    val Ink = Color(0xFF10121A)
    val Surface = Color(0xFF1A1D29)
    val SurfaceHigh = Color(0xFF262A3A)
    val Text = Color(0xFFECEAE4)
    val TextMuted = Color(0xFF8E92A3)

    /** Amber. Primary action only. */
    val Marquee = Color(0xFFE8B04B)
    val OnMarquee = Color(0xFF1B1405)

    /** Teal. Availability only (e.g. "available" in Seerr). */
    val Available = Color(0xFF5CC8B0)
}
