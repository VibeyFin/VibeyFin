package app.vibeyfin.tv.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.domain.StartDestination
import app.vibeyfin.ui.startup.StartupViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun StartupRoute(onDecided: (StartDestination) -> Unit, viewModel: StartupViewModel = koinViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) { destination?.let(onDecided) }
}
