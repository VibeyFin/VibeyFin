package app.vibeyfin.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.vibeyfin.tv.components.PrimaryAction
import app.vibeyfin.tv.components.SecondaryAction
import app.vibeyfin.tv.components.TvTextField
import app.vibeyfin.tv.theme.TvMarginHorizontal
import app.vibeyfin.tv.theme.TvMarginVertical
import app.vibeyfin.ui.addserver.AddServerStep
import app.vibeyfin.ui.addserver.AddServerViewModel
import app.vibeyfin.ui.common.Strings
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AddServerRoute(address: String?, onDone: () -> Unit, viewModel: AddServerViewModel = koinViewModel(parameters = { parametersOf(address) })) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    Box(Modifier.fillMaxSize().padding(horizontal = TvMarginHorizontal, vertical = TvMarginVertical), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(Strings.addAServer, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            TvTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                label = Strings.serverAddress,
                hint = Strings.serverAddressExample,
                imeAction = ImeAction.Go,
                onGo = viewModel::onConnect,
            )
            when (val step = state.step) {
                is AddServerStep.Failed -> {
                    Text(step.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    SecondaryAction(Strings.tryAgain, viewModel::onConnect)
                }
                is AddServerStep.Found -> {
                    Text("${step.server.name} · ${Strings.jellyfinVersion(step.server.version)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    PrimaryAction(Strings.addServer, viewModel::onConfirm)
                }
                AddServerStep.Checking -> Text(Strings.connecting, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AddServerStep.Idle -> PrimaryAction(Strings.connect, viewModel::onConnect)
            }
        }
    }
}
