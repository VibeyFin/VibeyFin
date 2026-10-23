package app.vibeyfin.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import app.vibeyfin.R
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
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
fun MainNavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(modifier = Modifier.semantics { contentDescription = Strings.mainNavigation }) {
        Tab.entries.forEach { tab ->
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

/** What Downloads shows until offline playback lands. */
@Composable
fun NotAvailableYet() {
    Box(Modifier.fillMaxSize().padding(horizontal = screenMargin()), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
            Text(
                text = Strings.notAvailableYet,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
