package com.liamgleeson.vibeyfin.ui.placeholder

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaceholderUiState(
    val title: String = "VibeyFin",
    val tagline: String = "Your Jellyfin library, front and centre.",
)

/** Scaffold-only: proves Koin → ViewModel → Compose wiring. Replaced by the Servers screen. */
class PlaceholderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlaceholderUiState())
    val uiState: StateFlow<PlaceholderUiState> = _uiState.asStateFlow()
}
