package app.vibeyfin.app.navigation

import androidx.compose.foundation.layout.padding
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
