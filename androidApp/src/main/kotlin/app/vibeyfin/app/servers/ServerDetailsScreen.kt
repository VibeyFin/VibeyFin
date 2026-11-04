package app.vibeyfin.app.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.DangerConfirmDialog
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.settings.ButtonRow
import app.vibeyfin.app.settings.RowFrame
import app.vibeyfin.app.settings.RowText
import app.vibeyfin.app.settings.SectionCard
import app.vibeyfin.app.settings.SectionFooter
import app.vibeyfin.app.settings.SectionHeader
import app.vibeyfin.app.settings.ValueRow
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.servers.ServerAddressRow
import app.vibeyfin.ui.servers.ServerDetailsUiState
import app.vibeyfin.ui.servers.ServerDetailsViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ServerDetailsRoute(
    serverId: String,
    onBack: () -> Unit,
    onAddAddress: () -> Unit,
    onRemoved: () -> Unit,
    viewModel: ServerDetailsViewModel = koinViewModel(parameters = { parametersOf(serverId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.removed) { if (state.removed) onRemoved() }

    ServerDetailsScreen(
        state = state,
        onBack = onBack,
        onAddAddress = onAddAddress,
        onRemoveAddress = viewModel::onRemoveAddress,
        onRemoveServer = viewModel::onRemoveServer,
    )

    if (state.confirmRemove) {
        DangerConfirmDialog(
            title = state.removeTitle,
            message = state.removeMessage,
            confirmLabel = Strings.remove,
            onConfirm = viewModel::onConfirmRemove,
            onDismiss = viewModel::onDismissRemove,
        )
    }
}

@Composable
fun ServerDetailsScreen(
    state: ServerDetailsUiState,
    onBack: () -> Unit,
    onAddAddress: () -> Unit,
    onRemoveAddress: (String) -> Unit,
    onRemoveServer: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(BackButtonSpace))
                if (!state.loading) {
                    Column(
                        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = screenMargin()),
                        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
                    ) {
                        Details(state, onAddAddress, onRemoveAddress, onRemoveServer)
                    }
                }
            }

            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun Details(state: ServerDetailsUiState, onAddAddress: () -> Unit, onRemoveAddress: (String) -> Unit, onRemoveServer: () -> Unit) {
    Text(
        text = state.name,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = VibeyFinTokens.Space.s3),
    )

    SectionHeader(Strings.addresses)
    SectionCard {
        state.addresses.forEach { AddressRow(it, state.canRemoveAddress, onRemoveAddress) }
        ButtonRow(Strings.addAddress, onAddAddress, leading = { Icon(Icons.Filled.Add, contentDescription = null) })
    }
    SectionFooter(Strings.addressesHelp)

    Spacer(Modifier.height(VibeyFinTokens.Space.s3))
    SectionHeader(Strings.serverSection)
    SectionCard {
        state.version?.let { ValueRow(Strings.version, Strings.jellyfinVersion(it)) }
        ValueRow(Strings.profilesOnDevice, state.profileCount.toString())
    }

    Spacer(Modifier.height(VibeyFinTokens.Space.s3))
    SectionCard { ButtonRow(Strings.removeServer, onRemoveServer, destructive = true) }
    SectionFooter(state.removeHelp)
    Spacer(Modifier.height(VibeyFinTokens.Space.s5))
}

@Composable
private fun AddressRow(row: ServerAddressRow, removable: Boolean, onRemove: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }

    RowFrame {
        RowText(row.address, Strings.lastWorked.takeIf { row.lastWorked }, Modifier.weight(1f))
        if (removable) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = Strings.moreFor(row.address), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
                    VibeyFinMenuItem(Strings.removeAddress, Icons.Filled.Delete) {
                        menu = false
                        onRemove(row.address)
                    }
                }
            }
        }
    }
}
