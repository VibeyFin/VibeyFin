package app.vibeyfin.app.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.BusyButton
import app.vibeyfin.app.components.DangerConfirmDialog
import app.vibeyfin.app.components.TabHeader
import app.vibeyfin.app.components.VibeyFinLogo
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.theme.BrandDisplay
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.domain.DiscoveredServer
import app.vibeyfin.domain.model.Server
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.servers.ServerRow
import app.vibeyfin.ui.servers.ServersUiState
import app.vibeyfin.ui.servers.ServersViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServersRoute(
    onOpenServer: (Server) -> Unit,
    onServerDetails: (Server) -> Unit,
    onAddServer: () -> Unit,
    onDiscoveredServer: (DiscoveredServer) -> Unit,
    viewModel: ServersViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ServersScreen(
        state = state,
        onOpenServer = onOpenServer,
        onServerDetails = onServerDetails,
        onRemoveServer = { viewModel.onRemoveServer(it.id) },
        onAddServer = onAddServer,
        onDiscoveredServer = onDiscoveredServer,
        onSearchAgain = viewModel::onSearchAgain,
    )

    state.confirmRemove?.let { row ->
        DangerConfirmDialog(
            title = Strings.removeServerTitle(row.server.name),
            message = Strings.removeServerMessage(row.profileCount),
            confirmLabel = Strings.remove,
            onConfirm = viewModel::onConfirmRemove,
            onDismiss = viewModel::onDismissRemove,
        )
    }
}

@Composable
fun ServersScreen(
    state: ServersUiState,
    onOpenServer: (Server) -> Unit,
    onServerDetails: (Server) -> Unit,
    onRemoveServer: (Server) -> Unit,
    onAddServer: () -> Unit,
    onDiscoveredServer: (DiscoveredServer) -> Unit,
    onSearchAgain: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)), contentAlignment = Alignment.TopCenter) {
            val content = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().padding(horizontal = screenMargin())
            when {
                state.loading -> Unit
                state.servers.isEmpty() -> Welcome(state, content, onAddServer, onDiscoveredServer, onSearchAgain)
                else -> ServerList(state, content, onOpenServer, onServerDetails, onRemoveServer, onAddServer, onDiscoveredServer, onSearchAgain)
            }
        }
    }
}

@Composable
private fun Welcome(
    state: ServersUiState,
    modifier: Modifier,
    onAddServer: () -> Unit,
    onDiscoveredServer: (DiscoveredServer) -> Unit,
    onSearchAgain: () -> Unit,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VibeyFinLogo()
            Text(Strings.appName, style = BrandDisplay, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = Strings.welcomeLead,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Column(Modifier.padding(bottom = VibeyFinTokens.Space.s5), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
            if (state.canSearch) {
                NearbyHeader(state.searching, spinner = state.discovered.isNotEmpty(), onSearchAgain)
                when {
                    state.discovered.isNotEmpty() -> state.discovered.forEach { DiscoveredRow(it, compact = false) { onDiscoveredServer(it) } }
                    state.searching -> LookingRow()
                    else -> Lead(Strings.noServersFoundHelp)
                }
            } else {
                Lead(Strings.welcomeAddressHelp, TextAlign.Center)
            }

            if (state.discovered.isEmpty()) {
                BusyButton(Strings.addServer, onAddServer)
            } else {
                AddServerButton(Strings.addServerByAddress, onAddServer)
            }
        }
    }
}

@Composable
private fun ServerList(
    state: ServersUiState,
    modifier: Modifier,
    onOpenServer: (Server) -> Unit,
    onServerDetails: (Server) -> Unit,
    onRemoveServer: (Server) -> Unit,
    onAddServer: () -> Unit,
    onDiscoveredServer: (DiscoveredServer) -> Unit,
    onSearchAgain: () -> Unit,
) {
    Column(modifier = modifier) {
        TabHeader(Strings.servers, horizontalPadding = 0.dp, trailing = {})
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
        ) {
            item(key = "top") { Spacer(Modifier.height(VibeyFinTokens.Space.s1)) }
            items(state.servers, key = { "saved-${it.server.id}" }) { row ->
                SavedRow(row, onClick = { onOpenServer(row.server) }, onDetails = { onServerDetails(row.server) }, onRemove = { onRemoveServer(row.server) })
            }

            if (state.canSearch) {
                item(key = "nearby-header") { NearbyHeader(state.searching, spinner = true, onSearchAgain) }
                items(state.discovered, key = { "found-${it.id}" }) { DiscoveredRow(it, compact = true) { onDiscoveredServer(it) } }

                if (state.discovered.isEmpty() && !state.searching) {
                    item(key = "nearby-empty") { Lead(Strings.noNewServersFound) }
                }
            }
        }

        AddServerButton(Strings.addServer, onAddServer, Modifier.padding(vertical = VibeyFinTokens.Space.s4))
    }
}

@Composable
private fun Lead(text: String, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddServerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge),
        colors = ButtonDefaults.filledTonalButtonColors(contentColor = VibeyFinTokens.Colors.ink),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

@Composable
private fun NearbyHeader(searching: Boolean, spinner: Boolean, onSearchAgain: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = VibeyFinTokens.Space.s2).height(VibeyFinTokens.Size.button),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = Strings.onYourNetwork,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            !searching -> TextButton(onClick = onSearchAgain) { Text(Strings.searchAgain, color = MaterialTheme.colorScheme.onSurface) }
            spinner -> SearchingLabel(Strings.searching)
        }
    }
}

@Composable
private fun SearchingLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LookingRow() {
    RowCard { SearchingLabel(Strings.lookingForServers) }
}

@Composable
private fun RowCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md)
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    val body = @Composable {
        Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = VibeyFinTokens.Space.s2, top = VibeyFinTokens.Space.s4, bottom = VibeyFinTokens.Space.s4)) { content() }
    }

    if (onClick == null) {
        Surface(modifier = Modifier.fillMaxWidth(), color = color, shape = shape) { body() }
    } else {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = color, shape = shape) { body() }
    }
}

@Composable
private fun ServerText(name: String, address: String, meta: String?, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        meta?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavedRow(row: ServerRow, onClick: () -> Unit, onDetails: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }

    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerText(row.server.name, row.server.addresses.firstOrNull().orEmpty(), row.meta, Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = Strings.moreFor(row.server.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
                    VibeyFinMenuItem(Strings.serverDetails, Icons.Filled.Info) {
                        menu = false
                        onDetails()
                    }
                    VibeyFinMenuItem(Strings.removeServer, Icons.Filled.Delete) {
                        menu = false
                        onRemove()
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredRow(server: DiscoveredServer, compact: Boolean, onClick: () -> Unit) {
    RowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerText(server.name, server.address, null, Modifier.weight(1f))
            if (compact) {
                Text(
                    text = Strings.addToServers,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s3),
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s2),
                )
            }
        }
    }
}
