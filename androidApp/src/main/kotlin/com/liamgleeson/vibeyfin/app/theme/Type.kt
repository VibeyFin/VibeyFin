package com.liamgleeson.vibeyfin.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.liamgleeson.vibeyfin.R

/**
 * Standard Material 3 type scale on the system font (Roboto). The design's `m3-*` styles are the M3
 * defaults, so nothing is overridden.
 */
val VibeyFinTypography = Typography()

private val BrandFamily = FontFamily(
    Font(
        resId = R.font.bricolage_grotesque,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

/**
 * `brand-display`: the only brand face. For the wordmark, a hero title without a Logo image and
 * onboarding. Everything else uses [VibeyFinTypography].
 */
val BrandDisplay = TextStyle(
    fontFamily = BrandFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.8).sp,
)
