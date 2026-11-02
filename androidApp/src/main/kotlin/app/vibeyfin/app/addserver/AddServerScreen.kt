package app.vibeyfin.app.addserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.AddressField
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.BusyButton
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.TabHeader
import app.vibeyfin.app.theme.ContentMaxWidth
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.ui.addserver.AddServerStep
import app.vibeyfin.ui.addserver.AddServerUiState
import app.vibeyfin.ui.addserver.AddServerViewModel
import app.vibeyfin.ui.addserver.message
import app.vibeyfin.ui.common.Strings
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** [showNext] says where saving leads: to the server's "Who's watching?", unless an address is being added from its details. */
@Composable
fun AddServerRoute(
    initialAddress: String?,
    showNext: Boolean,
    onBack: () -> Unit,
    onSaved: (serverId: String) -> Unit,
    viewModel: AddServerViewModel = koinViewModel(parameters = { parametersOf(initialAddress) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedServerId) { state.savedServerId?.let(onSaved) }

    AddServerScreen(
        state = state,
        focusOnOpen = initialAddress == null,
        showNext = showNext,
        onBack = onBack,
        onAddressChange = viewModel::onAddressChange,
        onConnect = viewModel::onConnect,
        onConfirm = viewModel::onConfirm,
    )
}

@Composable
fun AddServerScreen(
    state: AddServerUiState,
    focusOnOpen: Boolean,
    showNext: Boolean,
    onBack: () -> Unit,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onConfirm: () -> Unit,
) {
    val step = state.step
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (focusOnOpen) focus.requestFocus() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(BackButtonSpace))
                Column(
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = screenMargin()),
                    verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
                ) {
                    TabHeader(Strings.addAServer, horizontalPadding = 0.dp, trailing = {})
                    AddressField(
                        value = state.address,
                        onValueChange = onAddressChange,
                        label = Strings.serverAddress,
                        onGo = onConnect,
                        placeholder = Strings.serverAddressExample,
                        help = Strings.serverAddressHelp.takeIf { step is AddServerStep.Idle || step is AddServerStep.Checking },
                        error = (step as? AddServerStep.Failed)?.issue?.message(),
                        modifier = Modifier.focusRequester(focus),
                    )

                    if (step is AddServerStep.Found) {
                        FoundCard(step)
                        BusyButton(
                            label = when {
                                !step.alreadySaved -> Strings.addServer
                                step.addressIsNew -> Strings.saveAddress
                                else -> Strings.done
                            },
                            onClick = onConfirm,
                        )
                        if (showNext && !step.alreadySaved) {
                            Text(
                                text = Strings.addServerNext(step.server.name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4),
                            )
                        }
                    } else {
                        BusyButton(
                            label = Strings.connect,
                            onClick = onConnect,
                            busy = step is AddServerStep.Checking,
                            busyLabel = Strings.connecting,
                            enabled = state.canConnect,
                        )
                    }
                }
            }

            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun FoundCard(found: AddServerStep.Found) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
            // The server answered: a check and the word, in the text color. Aqua is for the primary action.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                Text(Strings.found, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(VibeyFinTokens.Space.s1))
            Text(found.server.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(Strings.jellyfinVersion(found.server.version), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(found.server.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            if (found.server.otherAddresses.isNotEmpty()) {
                Text(
                    text = Strings.alsoReachableAt(found.server.otherAddresses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (found.alreadySaved) {
                Spacer(Modifier.height(VibeyFinTokens.Space.s2))
                Text(
                    text = Strings.alreadySaved(found.addressIsNew),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
