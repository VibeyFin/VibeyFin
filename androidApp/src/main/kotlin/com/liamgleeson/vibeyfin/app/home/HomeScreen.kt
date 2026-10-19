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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.liamgleeson.vibeyfin.app.theme.VibeyFinPalette
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.home.HomeUiState
import com.liamgleeson.vibeyfin.ui.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    onExit: (HomeExit) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        state.exit?.let(onExit)
    }
    HomeScreen(
        state = state,
        onSwitchProfile = viewModel::onSwitchProfile,
        onSwitchServer = viewModel::onSwitchServer,
        onSettings = onSettings,
        onSignOut = viewModel::onSignOut,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.userName?.let { "Hi, $it" } ?: "Home",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    state.serverName?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ProfileMenu(state.userName, onSwitchProfile, onSwitchServer, onSettings, onSignOut)
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
                .size(44.dp)
                .clip(CircleShape)
                .background(VibeyFinPalette.SurfaceHigh)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName?.take(1)?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = VibeyFinPalette.Text,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = VibeyFinPalette.SurfaceHigh,
        ) {
            MenuItem("Switch profile") { open = false; onSwitchProfile() }
            MenuItem("Switch server") { open = false; onSwitchServer() }
            MenuItem("Settings") { open = false; onSettings() }
            MenuItem("Sign out") { open = false; onSignOut() }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = VibeyFinPalette.Text) }, onClick = onClick)
}
