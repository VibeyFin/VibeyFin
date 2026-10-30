package app.vibeyfin.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screen margin: 16 dp on phones, 24 dp from the medium width class (600 dp) up. */
@Composable
fun screenMargin(): Dp = if (isTabletWidth()) VibeyFinTokens.Space.s5 else VibeyFinTokens.Space.s4

/** Tablet width: the medium width class (600 dp) and up, where Settings shows two panes. */
@Composable
fun isTabletWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** The widest a screen made of a single column of text and rows gets: past this, on a tablet or an unfolded foldable, it is centered. */
val ContentMaxWidth: Dp = 720.dp
