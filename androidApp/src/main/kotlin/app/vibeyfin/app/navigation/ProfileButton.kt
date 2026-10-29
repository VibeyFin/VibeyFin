package app.vibeyfin.app.navigation

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalViewConfiguration
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
 * The profile's letter at the top right of every tab. A tap opens the menu at once; a double tap goes straight to "Who's watching?",
 * after the PIN when the profile has one. TalkBack's own double tap activates, so it gets Switch profile as an action instead.
 * Offline there is no server to sign in to: the menu has Settings only and a double tap does nothing.
 */
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
    // When the menu opened. A tap telling a double tap apart waits the whole double-tap time before it acts, which made the menu slow
    // to open; so the first tap opens it at once, and a second tap soon after (which lands outside the menu and closes it) is the
    // double tap.
    var openedAt by remember { mutableLongStateOf(0L) }
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    Box {
        Box(
            modifier = Modifier
                .size(VibeyFinTokens.Size.touch)
                .clip(CircleShape)
                .background(VibeyFinTokens.Colors.surface3)
                .clickable(role = Role.Button, onClickLabel = Strings.showOptions) {
                    open = true
                    openedAt = SystemClock.uptimeMillis()
                }
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
        VibeyFinMenu(
            expanded = open,
            onDismiss = {
                open = false
                if (!offline && SystemClock.uptimeMillis() - openedAt < doubleTapTimeout) viewModel.onSwitchProfile()
            },
        ) {
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
