package com.liamgleeson.vibeyfin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun TextStyle.heavy(weight: FontWeight, tracking: Float): TextStyle =
    copy(fontWeight = weight, letterSpacing = tracking.sp)

private val Base = Typography()

/** Heavy, tight type for titles; body text keeps the Material defaults. */
val VibeyFinTypography = Typography(
    displayLarge = Base.displayLarge.heavy(FontWeight.Black, -2f),
    displayMedium = Base.displayMedium.heavy(FontWeight.Black, -1.5f),
    displaySmall = Base.displaySmall.heavy(FontWeight.Black, -1f),
    headlineLarge = Base.headlineLarge.heavy(FontWeight.ExtraBold, -1f),
    headlineMedium = Base.headlineMedium.heavy(FontWeight.ExtraBold, -0.75f),
    headlineSmall = Base.headlineSmall.heavy(FontWeight.ExtraBold, -0.5f),
    titleLarge = Base.titleLarge.heavy(FontWeight.Bold, -0.5f),
    titleMedium = Base.titleMedium.heavy(FontWeight.Bold, -0.25f),
    titleSmall = Base.titleSmall.heavy(FontWeight.Bold, -0.1f),
)
