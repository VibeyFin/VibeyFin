package com.liamgleeson.vibeyfin.app.startup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liamgleeson.vibeyfin.app.theme.VibeyFinPalette
import com.liamgleeson.vibeyfin.domain.StartDestination
import com.liamgleeson.vibeyfin.ui.startup.StartupViewModel
import org.koin.androidx.compose.koinViewModel

/** Blank ink while the app works out where to open, which takes a moment of storage reads, not the network. */
@Composable
fun StartupRoute(onDecided: (StartDestination) -> Unit, viewModel: StartupViewModel = koinViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) {
        destination?.let(onDecided)
    }
    Box(Modifier.fillMaxSize().background(VibeyFinPalette.Ink))
}
