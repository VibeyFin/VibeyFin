package app.vibeyfin.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.vibeyfin.R
import app.vibeyfin.app.components.HeaderButton
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.settings.PinSheet
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.whoswatching.SignInFlight
import app.vibeyfin.ui.common.AvatarTint
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.profile.ProfileMenuViewModel
import app.vibeyfin.ui.settings.SettingsPageId
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

/** The profile button the shell hands to the tab roots' headers; nothing outside the tabs draws one. */
val LocalProfileButton = staticCompositionLocalOf<@Composable () -> Unit> { {} }

/**
 * The profile's letter at the top right of every tab. A tap opens the menu at once. TalkBack gets Switch profile as a custom action too.
 * Offline there is no server to sign in to: the menu has Settings only.
 */
@Composable
fun ProfileButton(
    offline: Boolean,
    onSettings: () -> Unit,
    onQuickConnect: () -> Unit,
    onExit: (HomeExit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileMenuViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    LaunchedEffect(state.openQuickConnect) {
        if (state.openQuickConnect) {
            onQuickConnect()
            viewModel.onQuickConnectOpened()
        }
    }
    state.pinPrompt?.let { prompt ->
        PinSheet(prompt, viewModel::onSubmitPin, viewModel::onForgotPin, viewModel::onSubmitPassword, viewModel::onDismissPin)
    }

    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        HeaderButton(
            onClick = { open = true },
            background = state.userName?.let { VibeyFinTokens.Colors.avatars[AvatarTint.of(it)] } ?: VibeyFinTokens.Colors.surface3,
            // Named for the person, as on iOS; the letter on it would only be read as "A"
            modifier = Modifier.semantics {
                contentDescription = state.userName.orEmpty()
                if (!offline) {
                    customActions = listOf(
                        CustomAccessibilityAction(Strings.switchProfile) {
                            viewModel.onSwitchProfile()
                            true
                        },
                    )
                }
            },
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
                VibeyFinMenuItem(Strings.switchProfile, Icons.Filled.Person) {
                    open = false

                    viewModel.onSwitchProfile()
                }

                VibeyFinMenuItem(Strings.switchServer, ImageVector.vectorResource(R.drawable.ic_server)) {
                    open = false

                    viewModel.onSwitchServer()
                }
            }

            if (!offline && !state.kidsProfile) {
                VibeyFinMenuItem(Strings.quickConnect, ImageVector.vectorResource(R.drawable.ic_devices)) {
                    open = false

                    viewModel.onQuickConnect()
                }
            }

            VibeyFinMenuItem(Strings.settings, Icons.Filled.Settings) {
                open = false

                onSettings()
            }

            // A kids profile cannot sign out; the view model ignores it too
            if (!offline && !state.kidsProfile) {
                VibeyFinMenuItem(Strings.signOut, Icons.AutoMirrored.Filled.ExitToApp) {
                    open = false

                    viewModel.onSignOut()
                }
            }
        }
    }
}

fun profileButtonSlot(
    navController: NavHostController,
    offline: Boolean,
    onExit: (HomeExit) -> Unit,
    signInFlight: SignInFlight,
): @Composable () -> Unit = {
    ProfileButton(
        offline,
        onSettings = { navController.navigate(Settings) },
        onQuickConnect = { navController.navigate(SettingsPageRoute(SettingsPageId.QuickConnect.name)) },
        onExit = onExit,
        modifier = signInFlight.profileButtonModifier(),
    )
}
