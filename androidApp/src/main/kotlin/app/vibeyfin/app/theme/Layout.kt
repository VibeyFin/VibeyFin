package app.vibeyfin.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp

/** Screen margin: 16 dp on phones, 24 dp from the medium width class (600 dp) up. */
@Composable
fun screenMargin(): Dp =
    if (LocalConfiguration.current.screenWidthDp >= 600) VibeyFinTokens.Space.s5 else VibeyFinTokens.Space.s4
