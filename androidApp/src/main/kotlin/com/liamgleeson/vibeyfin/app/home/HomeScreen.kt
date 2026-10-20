package com.liamgleeson.vibeyfin.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liamgleeson.vibeyfin.app.theme.screenMargin
import com.liamgleeson.vibeyfin.app.components.ArtworkTile
import com.liamgleeson.vibeyfin.app.components.LoadError
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.home.HomeUiState
import com.liamgleeson.vibeyfin.ui.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    onExit: (HomeExit) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    HomeScreen(
        state = state,
        onOpen = onOpen,
        onRetryLibraries = viewModel::onRetryLibraries,
        onSwitchProfile = viewModel::onSwitchProfile,
        onSwitchServer = viewModel::onSwitchServer,
        onSettings = onSettings,
        onSignOut = viewModel::onSignOut,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpen: (MediaItem) -> Unit,
    onRetryLibraries: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(screenMargin())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.userName?.let { Strings.greeting(it) } ?: Strings.home,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    state.serverName?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ProfileMenu(state.userName, onSwitchProfile, onSwitchServer, onSettings, onSignOut)
            }
            if (state.serverId != null) {
                Spacer(Modifier.height(24.dp))
                Libraries(state, onOpen, onRetryLibraries)
            }
        }
    }
}

@Composable
private fun Libraries(state: HomeUiState, onOpen: (MediaItem) -> Unit, onRetry: () -> Unit) {
    Text(
        text = Strings.libraries,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    when {
        state.loadingLibraries -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.librariesMessage != null -> LoadError(state.librariesMessage!!, onRetry)
        state.libraries.isEmpty() -> Text(
            text = Strings.noLibraries,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
            horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.libraries, key = { it.id }) { library ->
                ArtworkTile(library, onClick = { onOpen(library) })
            }
        }
    }
}

/** The profile button at the top right and the menu it opens. */
@Composable
private fun ProfileMenu(
    userName: String?,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(VibeyFinTokens.Size.touch)
                .clip(CircleShape)
                .background(VibeyFinTokens.Colors.surface3)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName?.take(1)?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = VibeyFinTokens.Colors.ink,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            MenuItem(Strings.switchProfile) { open = false; onSwitchProfile() }
            MenuItem(Strings.switchServer) { open = false; onSwitchServer() }
            MenuItem(Strings.settings) { open = false; onSettings() }
            MenuItem(Strings.signOut) { open = false; onSignOut() }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}
