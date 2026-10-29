package app.vibeyfin.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** Material 3's own bar: labels always shown, the selected item in the `accent-soft` pill (the theme's secondary container). */
@Composable
fun MainNavigationBar(selected: Tab, onSelect: (Tab) -> Unit, offline: Boolean = false) {
    NavigationBar(modifier = Modifier.semantics { contentDescription = Strings.mainNavigation }) {
        // Without the server there is no Library to browse: Home, Search and Downloads work from what is on the device.
        Tab.entries.filter { !offline || it != Tab.Library }.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(tab.label) },
                alwaysShowLabel = true,
            )
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

/** Height the tabs take at the top of a tablet, which the tab roots without artwork leave free. */
val TABLET_TABS_HEIGHT: Dp = 72.dp

/**
 * The tabs on a tablet: a row centered at the top over the content, the selected one in the `accent-soft` pill (the theme's secondary
 * container), the others plain. No rail, like the iPad's top bar. Without the server there is no Library tab ([tabs] leaves it out).
 */
@Composable
fun TabletTabs(selected: Tab, tabs: List<Tab>, onSelect: (Tab) -> Unit, onSwipe: (step: Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .safeDrawingPadding()
            .padding(top = 8.dp)
            .swipeBetweenTabs(onPrevious = { onSwipe(-1) }, onNext = { onSwipe(1) })
            .semantics { contentDescription = Strings.mainNavigation },
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val active = tab == selected
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                Icon(tab.icon(), contentDescription = null, tint = color, modifier = Modifier.width(20.dp).height(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tab.label, style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}

/**
 * A swipe sideways on the bar changes tab: right goes to the previous one, left to the next.
 */
fun Modifier.swipeBetweenTabs(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = composed {
    var travelled by remember { mutableFloatStateOf(0f) }
    pointerInput(onPrevious, onNext) {
        val threshold = 72.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                if (travelled > threshold) onPrevious() else if (travelled < -threshold) onNext()
                travelled = 0f
            },
            onDragCancel = { travelled = 0f },
            onHorizontalDrag = { _, amount -> travelled += amount },
        )
    }
}
