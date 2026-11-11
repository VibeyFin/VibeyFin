package app.vibeyfin.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.ThemeIds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The signed-in profile's theme id; the default while nobody is signed in. */
class ThemeViewModel(profileSettings: ProfileSettings) : ViewModel() {
    val themeId: StateFlow<String> = profileSettings.themeId
        .map { id: String? -> id ?: ThemeIds.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeIds.DEFAULT)
}
