package com.liamgleeson.vibeyfin.app.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liamgleeson.vibeyfin.app.theme.VibeyFinPalette
import com.liamgleeson.vibeyfin.ui.settings.SettingsUiState
import com.liamgleeson.vibeyfin.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, onBack, viewModel::onAlwaysAskChanged)
}

@Composable
fun SettingsScreen(state: SettingsUiState, onBack: () -> Unit, onAlwaysAskChanged: (Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            TextButton(onClick = onBack) { Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                // The whole row toggles, not just the switch, so the label is a target too.
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = state.alwaysAskWhoIsWatching,
                    role = Role.Switch,
                    onValueChange = onAlwaysAskChanged,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Always ask who's watching",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Show the profile screen every time the app opens instead of going straight to the last profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Amber is for the one primary action, so the switch is light on dark instead.
                Switch(
                    checked = state.alwaysAskWhoIsWatching,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VibeyFinPalette.Ink,
                        checkedTrackColor = VibeyFinPalette.Text,
                        uncheckedThumbColor = VibeyFinPalette.TextMuted,
                        uncheckedTrackColor = VibeyFinPalette.SurfaceHigh,
                        uncheckedBorderColor = VibeyFinPalette.SurfaceHigh,
                    ),
                )
            }
        }
    }
}
