package app.vibeyfin.app.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/** The margin a page puts around text and controls: [pageMargin] plus the wider side inset, so artwork can still run to the edge. */
@Composable
fun screenMargin(): Dp = pageMargin() + sideInset()

/** 16 dp on phones, 24 dp from 600 dp up; alone only where the layout already keeps to the safe area (the player). */
@Composable
fun pageMargin(): Dp = if (isTabletWidth()) VibeyFinTokens.Space.s5 else VibeyFinTokens.Space.s4

/** The wider of the two side insets (a cut-out, or the 3-button bar in landscape), added on both sides. */
@Composable
fun sideInset(): Dp {
    val safeArea = WindowInsets.safeDrawing.asPaddingValues()

    return max(safeArea.calculateLeftPadding(LayoutDirection.Ltr), safeArea.calculateRightPadding(LayoutDirection.Ltr))
}

/** The height of the system navigation bar under the page; 0 with the bar at a side. */
@Composable
fun navigationBarHeight(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** The height of the open keyboard, from the bottom of the screen; 0 when it is closed. */
@Composable
fun keyboardHeight(): Dp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

/** Status bar and keyboard only: the sides are in [screenMargin] and the navigation bar in each list's bottom padding, so lists run under it. */
@Composable
fun Modifier.pageInsetsPadding(): Modifier = this
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))

/** The margin of a title's page: wider beside the wide layout's backdrop. */
@Composable
fun detailMargin(wide: Boolean): Dp = if (wide) VibeyFinTokens.Space.s6 + sideInset() else screenMargin()

/** Tablet width: the medium width class (600 dp) and up, where Settings shows two panes. */
@Composable
fun isTabletWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** The widest a screen made of a single column of text and rows gets: past this, on a tablet or an unfolded foldable, it is centered. */
val ContentMaxWidth: Dp = 720.dp
