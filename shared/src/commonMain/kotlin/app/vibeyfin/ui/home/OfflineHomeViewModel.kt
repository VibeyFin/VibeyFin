package app.vibeyfin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.OfflineLibrary
import app.vibeyfin.domain.accountOrNull
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.downloads.profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class OfflineHomeUiState(
    /** The profile whose downloads these are; null while nobody is signed in. */
    val userName: String? = null,
    /** The first read of the device's own list is on its way. */
    val loading: Boolean = true,
    /** What the profile is in the middle of, and the next downloaded episode of their shows; from where they stopped, kept on the device. */
    val continueRow: ContinueRow = ContinueRow(),
    /** The movies and the shows on the device, a row each; a row with nothing in it is not here. */
    val rows: List<MediaRow> = emptyList(),
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    /** Nothing is downloaded: the page says how to get something. */
    val isEmpty: Boolean get() = !loading && !continueRow.isVisible && rows.isEmpty()
}

/**
 * Home without the server: what the profile can watch from the device. No libraries, no featured title and no genre rows: those
 * come from the server. Everything is read from what was downloaded, so it works with no network at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineHomeViewModel(
    private val connection: ConnectionManager,
    offline: OfflineLibrary,
) : ViewModel() {
    private val content: Flow<OfflineHomeUiState> = connection.profile.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(OfflineHomeUiState(loading = false))
        } else {
            combine(offline.continueWatching(profile), offline.movies(profile), offline.shows(profile)) { continuing, movies, shows ->
                OfflineHomeUiState(
                    loading = false,
                    continueRow = ContinueRow(cards = continuing.map { it.toContinueCard() }),
                    rows = listOf(
                        MediaRow("offline-movies", Strings.filterMovies, items = movies, loading = false),
                        MediaRow("offline-shows", Strings.filterShows, items = shows, loading = false),
                    ).filter { it.isVisible },
                )
            }
        }
    }

    val uiState: StateFlow<OfflineHomeUiState> = combine(content, connection.state, connection.sessionEnded()) { state, connectionState, exit ->
        state.copy(userName = connectionState.accountOrNull()?.name, exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineHomeUiState())
}
