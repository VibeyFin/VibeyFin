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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.BackButtonSpace
import app.vibeyfin.app.components.FloatingBackButton
import app.vibeyfin.app.components.vibeyFinTextFieldColors
import app.vibeyfin.app.theme.screenMargin
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.ui.addserver.AddServerStep
import app.vibeyfin.ui.addserver.AddServerUiState
import app.vibeyfin.ui.addserver.AddServerViewModel
import app.vibeyfin.ui.addserver.message
import app.vibeyfin.ui.common.Strings
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AddServerRoute(
    initialAddress: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddServerViewModel = koinViewModel(parameters = { parametersOf(initialAddress) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    AddServerScreen(
        state = state,
        onBack = onBack,
        onAddressChange = viewModel::onAddressChange,
        onConnect = viewModel::onConnect,
        onConfirm = viewModel::onConfirm,
    )
}

@Composable
fun AddServerScreen(
    state: AddServerUiState,
    onBack: () -> Unit,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onConfirm: () -> Unit,
) {
    val found = state.step as? AddServerStep.Found

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
                    text = Strings.addAServer,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(VibeyFinTokens.Space.s5))

                OutlinedTextField(
                    value = state.address,
                    onValueChange = onAddressChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(Strings.serverAddress) },
                    placeholder = { Text(Strings.serverAddressExample) },
                    singleLine = true,
                    isError = state.step is AddServerStep.Failed,
                    colors = vibeyFinTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onConnect() }),
                )
                Spacer(Modifier.height(VibeyFinTokens.Space.s4))

                // Connect is the main action until a server has been found, then it's Add server
                if (found == null) {
                    val checking = state.step is AddServerStep.Checking

                    Button(
                        onClick = onConnect,
                        // Stays enabled while checking so the busy button keeps its colors
                        // The view model ignores a second tap
                        enabled = state.canConnect || checking,
                        modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge),
                    ) {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(VibeyFinTokens.Space.s3))
                            Text(Strings.connecting)
                        } else {
                            Text(Strings.connect)
                        }
                    }
                }

                when (val step = state.step) {
                    is AddServerStep.Failed -> {
                        Spacer(Modifier.height(VibeyFinTokens.Space.s5))
                        ErrorCard(step.issue.message())
                    }

                    is AddServerStep.Found -> {
                        FoundCard(step)
                        Spacer(Modifier.height(VibeyFinTokens.Space.s4))

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth().height(VibeyFinTokens.Size.buttonLarge),
                        ) {
                            Text(
                                when {
                                    !step.alreadySaved -> Strings.addServer
                                    step.addressIsNew -> Strings.saveAddress
                                    else -> Strings.done
                                },
                            )
                        }
                    }

                    AddServerStep.Idle, AddServerStep.Checking -> Unit
                }
            }

            FloatingBackButton(onBack)
        }
    }
}

@Composable
private fun FoundCard(found: AddServerStep.Found) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = VibeyFinTokens.Space.s2),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The server answered: a check and the word, in the text color. Aqua is for the primary action.
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(VibeyFinTokens.Space.s2))
                Text(
                    text = Strings.found,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(VibeyFinTokens.Space.s1))
            Text(
                text = found.server.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = Strings.jellyfinVersion(found.server.version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = found.server.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

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

@Composable
private fun ErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
            Text(
                text = Strings.cantConnectTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
