package app.vibeyfin.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.vibeyfin.tv.components.FocusSurface
import app.vibeyfin.design.BrandDisplay
import app.vibeyfin.tv.theme.TvMarginHorizontal
import app.vibeyfin.tv.theme.TvMarginVertical
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.servers.ServersViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServersRoute(
    onOpenServer: (serverId: String) -> Unit,
    onAddServer: (address: String?) -> Unit,
    viewModel: ServersViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(state.loading, state.servers.isEmpty()) {
        if (!state.loading) runCatching { first.requestFocus() }
    }
    Row(Modifier.fillMaxSize().padding(horizontal = TvMarginHorizontal, vertical = TvMarginVertical), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
        Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(Strings.appName, style = BrandDisplay, color = MaterialTheme.colorScheme.onBackground)
            Text(Strings.servers, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            if (state.canSearch && state.servers.isEmpty() && state.discovered.isEmpty() && !state.searching) {
                Text(Strings.noServersFound, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            }
        }
        LazyColumn(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            items(state.servers, key = { it.id }) { server ->
                Entry(server.name, server.addresses.firstOrNull().orEmpty(), Modifier.focusRequester(first)) { onOpenServer(server.id) }
            }
            if (state.discovered.isNotEmpty()) item(key = "found-title") {
                Text(Strings.onYourNetwork, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            items(state.discovered, key = { "found-${it.id}" }) { found ->
                Entry(found.name, found.address) { onAddServer(found.address) }
            }
            item(key = "add") {
                Entry(Strings.addServer, null, if (state.servers.isEmpty() && state.discovered.isEmpty()) Modifier.focusRequester(first) else Modifier) { onAddServer(null) }
            }
            if (state.canSearch) {
                item(key = "search") {
                    Entry(if (state.searching) Strings.searching else Strings.searchAgain, null) { viewModel.onSearchAgain() }
                }
            }
        }
    }
}

@Composable
private fun Entry(title: String, detail: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
