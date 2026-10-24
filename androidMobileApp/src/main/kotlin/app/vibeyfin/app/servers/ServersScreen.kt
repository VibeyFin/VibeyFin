package app.vibeyfin.app.servers

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.design.BrandDisplay
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.domain.DiscoveredServer
import app.vibeyfin.domain.model.Server
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.servers.ServersUiState
import app.vibeyfin.ui.servers.ServersViewModel
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
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = screenMargin()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = Strings.appName,
            style = BrandDisplay,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(VibeyFinTokens.Space.s6))
        Button(onClick = onAddServer, modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge)) {
            Text(Strings.addServer)
        }

        if (state.canSearch) {
            Spacer(Modifier.height(VibeyFinTokens.Space.s4))

            if (state.searching) {
                SearchingLabel()
            } else {
                Text(
                    text = Strings.noServersFound,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSearchAgain) {
                    Text(Strings.searchAgain, color = MaterialTheme.colorScheme.onSurface)
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
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = screenMargin())) {
        Spacer(Modifier.height(VibeyFinTokens.Space.s6))
        Text(
            text = Strings.servers,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(VibeyFinTokens.Space.s5))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            items(state.servers, key = { "saved-${it.id}" }) { server -> ServerRow(server) { onOpenServer(server) } }

            if (state.canSearch) {
                item(key = "nearby-header") { NearbyHeader(state.searching, onSearchAgain) }
                items(state.discovered, key = { "found-${it.id}" }) { DiscoveredRow(it) { onDiscoveredServer(it) } }

                if (state.discovered.isEmpty() && !state.searching) {
                    item(key = "nearby-empty") {
                        Text(
                            text = Strings.noNewServersFound,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        FilledTonalButton(
            onClick = onAddServer,
            modifier = Modifier.fillMaxWidth().padding(vertical = VibeyFinTokens.Space.s4).height(VibeyFinTokens.Size.touch),
        ) {
            Text(Strings.addServer)
        }
    }
}

@Composable
private fun NearbyHeader(searching: Boolean, onSearchAgain: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = VibeyFinTokens.Space.s3).height(VibeyFinTokens.Size.button),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = Strings.onYourNetwork,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (searching) {
            SearchingLabel()
        } else {
            TextButton(onClick = onSearchAgain) {
                Text(Strings.searchAgain, color = MaterialTheme.colorScheme.onSurface)
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
        Spacer(Modifier.size(VibeyFinTokens.Space.s2))
        Text(
            text = Strings.searching,
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
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
                    text = Strings.jellyfinVersion(it),
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
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

            Spacer(Modifier.size(VibeyFinTokens.Space.s4))
            Text(
                text = Strings.addToServers,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
