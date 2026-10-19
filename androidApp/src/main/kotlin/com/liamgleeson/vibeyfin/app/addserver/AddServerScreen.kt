package com.liamgleeson.vibeyfin.app.addserver

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.liamgleeson.vibeyfin.app.components.vibeyFinTextFieldColors
import com.liamgleeson.vibeyfin.app.theme.VibeyFinPalette
import com.liamgleeson.vibeyfin.ui.addserver.AddServerStep
import com.liamgleeson.vibeyfin.ui.addserver.AddServerUiState
import com.liamgleeson.vibeyfin.ui.addserver.AddServerViewModel
import com.liamgleeson.vibeyfin.ui.addserver.message
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Add a server",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server address") },
                placeholder = { Text("192.168.1.20:8096") },
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
            Spacer(Modifier.height(16.dp))

            // Connect is the main action until a server has been found; then it's Add server.
            if (found == null) {
                val checking = state.step is AddServerStep.Checking
                Button(
                    onClick = onConnect,
                    enabled = state.canConnect,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text("Connecting")
                    } else {
                        Text("Connect")
                    }
                }
            }

            when (val step = state.step) {
                is AddServerStep.Failed -> {
                    Spacer(Modifier.height(24.dp))
                    ErrorCard(step.issue.message())
                }

                is AddServerStep.Found -> {
                    FoundCard(step)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            when {
                                !step.alreadySaved -> "Add server"
                                step.addressIsNew -> "Save address"
                                else -> "Done"
                            },
                        )
                    }
                }

                AddServerStep.Idle, AddServerStep.Checking -> Unit
            }
        }
    }
}

@Composable
private fun FoundCard(found: AddServerStep.Found) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Teal means available: the server answered.
                Box(Modifier.size(8.dp).background(VibeyFinPalette.Available, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Found",
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeyFinPalette.Available,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = found.server.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Jellyfin ${found.server.version}",
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
                    text = "Also reachable at " + found.server.otherAddresses.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (found.alreadySaved) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (found.addressIsNew) {
                        "You already have this server. This adds the new address to it."
                    } else {
                        "You already have this server at this address."
                    },
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Can't connect",
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
