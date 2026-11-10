package app.vibeyfin.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings

/** The destinations of the main navigation bar. Each keeps its own back stack. */
enum class Tab(val graph: Any, val root: Any, val label: String) {
    Home(HomeTab, app.vibeyfin.app.navigation.Home, Strings.tabHome),
    Search(SearchTab, app.vibeyfin.app.navigation.Search, Strings.tabSearch),
    Library(LibraryTab, app.vibeyfin.app.navigation.Library, Strings.tabLibrary),
    Downloads(DownloadsTab, app.vibeyfin.app.navigation.Downloads, Strings.tabDownloads),
}

private val BAR_HEIGHT: Dp = 64.dp

private val BAR_INSET: Dp = VibeyFinTokens.Space.s2

// Wider than the 48 dp touch minimum
private val ICON_ITEM_WIDTH: Dp = 56.dp

/** What a tab's list leaves free at its bottom so its last row clears the floating bar: the bar and the gap under it. */
val FLOATING_BAR_SPACE: Dp = BAR_HEIGHT + VibeyFinTokens.Space.s3 * 2

/** The space the floating bar takes over the content; 0 where there is no bar. Every scrolling page of a tab adds it at the bottom. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/**
 * The main navigation on phones and tablets: a pill floating over the content, centered and only as wide as its tabs, with a fine
 * edge that sets it apart from the artwork behind it. The tab showing has its label beside its icon; the
 * others are icons alone, named for TalkBack. Without the server there is
 * no Library tab ([tabs] leaves it out). As on iOS, a finger dragged along the bar moves the highlight to the tab under it, with
 * a tick for each; the page changes only when the finger lifts, to the tab it lifted on.
 */
@Composable
fun FloatingNavigationBar(selected: Tab, tabs: List<Tab>, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val spans = remember { mutableStateMapOf<Tab, ClosedFloatingPointRange<Float>>() }

    var dragged by remember { mutableStateOf<Tab?>(null) }
    val highlighted = dragged ?: selected

    Surface(
        modifier = modifier.padding(vertical = VibeyFinTokens.Space.s3),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, VibeyFinTokens.Colors.ink.copy(alpha = 0.08f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .height(BAR_HEIGHT)
                .padding(BAR_INSET)
                .selectableGroup()
                .scrubTabs(
                    spans,
                    highlighted,
                    onMove = { dragged = it },
                    onRelease = {
                        // Only a tab the bar still shows: offline there is no Library to go to
                        dragged?.let { if (it != selected && it in tabs) onSelect(it) }
                        dragged = null
                    },
                    onCancel = { dragged = null },
                )
                .semantics { contentDescription = Strings.mainNavigation },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1),
        ) {
            tabs.forEach { tab ->
                // A tab that leaves the bar (Library going offline) takes its place with it, so a drag over where it was finds
                // the tab there now, not the one that is gone.
                DisposableEffect(tab) { onDispose { spans.remove(tab) } }
                BarItem(
                    tab,
                    active = tab == highlighted,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.onPlaced { spans[tab] = it.positionInParent().x.let { x -> x..(x + it.size.width) } },
                )
            }
        }
    }
}

@Composable
private fun BarItem(tab: Tab, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(if (active) VibeyFinTokens.Colors.tonal else Color.Transparent, label = "tab tint")
    val content by animateColorAsState(if (active) VibeyFinTokens.Colors.spark else VibeyFinTokens.Colors.inkMuted, label = "tab content")

    Row(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = ICON_ITEM_WIDTH)
            .clip(CircleShape)
            .background(tint)
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            // The label is gone from the tabs not showing; TalkBack still names each one
            .semantics { contentDescription = tab.label }
            .animateContentSize()
            .padding(horizontal = if (active) VibeyFinTokens.Space.s4 else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon(), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))

        if (active) {
            Spacer(Modifier.width(VibeyFinTokens.Space.s2))
            Text(tab.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
private fun Tab.icon(): ImageVector = when (this) {
    Tab.Home -> Icons.Filled.Home
    Tab.Search -> Icons.Filled.Search
    Tab.Library -> ImageVector.vectorResource(R.drawable.ic_library)
    Tab.Downloads -> ImageVector.vectorResource(R.drawable.ic_download)
}

// A tap still reaches the items: a drag only starts past the touch slop
private fun Modifier.scrubTabs(
    spans: Map<Tab, ClosedFloatingPointRange<Float>>,
    highlighted: Tab,
    onMove: (Tab) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(highlighted)
    val move by rememberUpdatedState(onMove)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)

    pointerInput(spans) {
        detectHorizontalDragGestures(
            onDragEnd = { release() },
            onDragCancel = { cancel() },
        ) { change, _ ->
            val x = change.position.x
            val under = spans.entries.firstOrNull { x in it.value }?.key ?: return@detectHorizontalDragGestures

            if (under != current) {
                move(under)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }
}
