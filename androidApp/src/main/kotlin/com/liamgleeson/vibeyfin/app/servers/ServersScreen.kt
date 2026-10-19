package com.liamgleeson.vibeyfin.app.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liamgleeson.vibeyfin.domain.DiscoveredServer
import com.liamgleeson.vibeyfin.domain.model.Server
import com.liamgleeson.vibeyfin.ui.servers.ServersUiState
import com.liamgleeson.vibeyfin.ui.servers.ServersViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServersRoute(
    onOpenServer: (Server) -> Unit,
    onAddServer: () -> Unit,
    onDiscoveredServer: (DiscoveredServer) -> Unit,
    viewModel: ServersViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ServersScreen(
        state = state,
        onOpenServer = onOpenServer,
        onAddServer = onAddServer,
        onDiscoveredServer = onDiscoveredServer,
        onSearchAgain = viewModel::onSearchAgain,
    )
}

@Composable
fun ServersScreen(
    state: ServersUiState,
    onOpenServer: (Server) -> Unit,
    onAddServer: () -> Unit,
    onDiscoveredServer: (DiscoveredServer) -> Unit,
    onSearchAgain: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.loading -> Unit
            state.servers.isEmpty() && state.discovered.isEmpty() -> EmptyServers(state, onAddServer, onSearchAgain)
            else -> ServerList(state, onOpenServer, onAddServer, onDiscoveredServer, onSearchAgain)
        }
    }
}

@Composable
private fun EmptyServers(state: ServersUiState, onAddServer: () -> Unit, onSearchAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "VibeyFin",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAddServer, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Add server")
        }
        if (state.canSearch) {
            Spacer(Modifier.height(16.dp))
            if (state.searching) {
                SearchingLabel()
            } else {
                Text(
                    text = "No servers found on your network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSearchAgain) {
                    Text("Search again", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ServerList(
    state: ServersUiState,
    onOpenServer: (Server) -> Unit,
    onAddServer: () -> Unit,
    onDiscoveredServer: (DiscoveredServer) -> Unit,
    onSearchAgain: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Servers",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.servers, key = { "saved-${it.id}" }) { server -> ServerRow(server) { onOpenServer(server) } }
            if (state.canSearch) {
                item(key = "nearby-header") { NearbyHeader(state.searching, onSearchAgain) }
                items(state.discovered, key = { "found-${it.id}" }) { DiscoveredRow(it) { onDiscoveredServer(it) } }
                if (state.discovered.isEmpty() && !state.searching) {
                    item(key = "nearby-empty") {
                        Text(
                            text = "No new servers found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onAddServer,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp),
        ) {
            Text("Add server")
        }
    }
}

@Composable
private fun NearbyHeader(searching: Boolean, onSearchAgain: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "On your network",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (searching) {
            SearchingLabel()
        } else {
            TextButton(onClick = onSearchAgain) {
                Text("Search again", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SearchingLabel() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Searching",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServerRow(server: Server, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = server.addresses.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            server.version?.let {
                Text(
                    text = "Jellyfin $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiscoveredRow(server: DiscoveredServer, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = server.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(16.dp))
            Text(
                text = "Add",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
