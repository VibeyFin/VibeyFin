package app.vibeyfin.app.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.settings.PinSheet
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.profile.ProfileMenuViewModel
import org.koin.androidx.compose.koinViewModel

/** The profile button the shell hands to the tab roots' headers; nothing outside the tabs draws one. */
val LocalProfileButton = staticCompositionLocalOf<@Composable () -> Unit> { {} }

/**
 * The profile's letter at the top right of every tab. A tap opens the menu; a double tap goes straight to "Who's watching?", after
 * the PIN when the profile has one. TalkBack's own double tap activates, so it gets Switch profile as an action instead. Offline
 * there is no server to sign in to: the menu has Settings only and a double tap does nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileButton(
    offline: Boolean,
    onSettings: () -> Unit,
    onExit: (HomeExit) -> Unit,
    viewModel: ProfileMenuViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    state.pinPrompt?.let { prompt ->
        PinSheet(prompt, viewModel::onSubmitPin, viewModel::onForgotPin, viewModel::onSubmitPassword, viewModel::onDismissPin)
    }
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(VibeyFinTokens.Size.touch)
                .clip(CircleShape)
                .background(VibeyFinTokens.Colors.surface3)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = Strings.showOptions,
                    onClick = { open = true },
                    onDoubleClick = if (offline) null else viewModel::onSwitchProfile,
                )
                // Named for the person, as on iOS; the letter on it would only be read as "A".
                .semantics {
                    contentDescription = state.userName.orEmpty()
                    if (!offline) {
                        customActions = listOf(CustomAccessibilityAction(Strings.switchProfile) { viewModel.onSwitchProfile(); true })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.userName?.take(1)?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = VibeyFinTokens.Colors.ink,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        VibeyFinMenu(expanded = open, onDismiss = { open = false }) {
            if (!offline) {
                VibeyFinMenuItem(Strings.switchProfile, Icons.Filled.Person) { open = false; viewModel.onSwitchProfile() }
                VibeyFinMenuItem(Strings.switchServer, Icons.Filled.Refresh) { open = false; viewModel.onSwitchServer() }
            }
            VibeyFinMenuItem(Strings.settings, Icons.Filled.Settings) { open = false; onSettings() }
            // A kids profile cannot sign out; the view model ignores it too.
            if (!offline && !state.kidsProfile) {
                VibeyFinMenuItem(Strings.signOut, Icons.AutoMirrored.Filled.ExitToApp) { open = false; viewModel.onSignOut() }
            }
        }
    }
}
