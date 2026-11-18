package app.vibeyfin.app.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** A page's vertical scroll, ending with [LocalBottomBarPadding] so the last row clears the bars it scrolls under. */
@Composable
fun Modifier.verticalPageScroll(state: ScrollState = rememberScrollState()): Modifier = this
    .verticalScroll(state)
    .padding(bottom = LocalBottomBarPadding.current)

/** Where a snackbar sits: above [bottomBar], or above the keyboard while it is open, and clear of side insets. */
@Composable
fun Modifier.bottomOverlayPadding(bottomBar: Dp): Modifier = this
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
    .padding(bottom = bottomBar)
