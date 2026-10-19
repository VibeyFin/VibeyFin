package com.liamgleeson.vibeyfin.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.domain.StartDestination
import com.liamgleeson.vibeyfin.domain.StartupRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Works out the first screen once; null while it is deciding. */
class StartupViewModel(router: StartupRouter) : ViewModel() {
    private val _destination = MutableStateFlow<StartDestination?>(null)
    val destination: StateFlow<StartDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch { _destination.value = router.decide() }
    }
}
