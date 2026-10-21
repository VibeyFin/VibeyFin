package app.vibeyfin.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.settings.SettingsUiState
import app.vibeyfin.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, onBack, viewModel::onAlwaysAskChanged, viewModel::onShowPlaybackStatsChanged, viewModel::onBingeThresholdChanged)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAlwaysAskChanged: (Boolean) -> Unit,
    onShowPlaybackStatsChanged: (Boolean) -> Unit,
    onBingeThresholdChanged: (Int) -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = screenMargin()),
            ) {
                Spacer(Modifier.height(BackButtonSpace))
                Text(
                    text = Strings.settings,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(VibeyFinTokens.Space.s5))
                SwitchRow(
                    title = Strings.alwaysAskWhoIsWatchingTitle,
                    help = Strings.alwaysAskWhoIsWatchingHelp,
                    checked = state.alwaysAskWhoIsWatching,
                    onCheckedChange = onAlwaysAskChanged,
                )
                if (state.hasProfile) {
                    Spacer(Modifier.height(VibeyFinTokens.Space.s5))
                    SwitchRow(
                        title = Strings.playbackStatsTitle,
                        help = Strings.playbackStatsHelp,
                        checked = state.showPlaybackStats,
                        onCheckedChange = onShowPlaybackStatsChanged,
                    )
                    Spacer(Modifier.height(24.dp))
                    ThresholdRow(
                        title = Strings.stillWatchingTitle,
                        help = Strings.stillWatchingHelp,
                        value = state.bingeThreshold,
                        onChange = onBingeThresholdChanged,
                    )
                }
            }
            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        // The whole row toggles, not just the switch, so the label is a target too.
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = VibeyFinTokens.Space.s4), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Aqua is for the one primary action, so the switch is light on dark instead.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VibeyFinTokens.Colors.bg,
                checkedTrackColor = VibeyFinTokens.Colors.ink,
                uncheckedThumbColor = VibeyFinTokens.Colors.inkMuted,
                uncheckedTrackColor = VibeyFinTokens.Colors.surface3,
                uncheckedBorderColor = VibeyFinTokens.Colors.lineStrong,
            ),
        )
    }
}

/** Off, or after 2 to 5 episodes in a row: a row that opens a menu with those choices. */
@Composable
private fun ThresholdRow(title: String, help: String, value: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.DropdownList) { open = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(text = help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The menu hangs from the value on the right, where the eye is, not from the row's left edge.
            Box {
                Text(
                    text = thresholdLabel(value),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VibeyFinMenu(expanded = open, onDismiss = { open = false }) {
                    THRESHOLD_CHOICES.forEach { choice ->
                        VibeyFinMenuItem(thresholdLabel(choice), if (choice == value) Icons.Filled.Check else null) {
                            open = false
                            onChange(choice)
                        }
                    }
                }
            }
        }
    }
}

private val THRESHOLD_CHOICES = listOf(0, 2, 3, 4, 5)

private fun thresholdLabel(value: Int) = if (value == 0) Strings.stillWatchingOff else Strings.stillWatchingAfter(value)
