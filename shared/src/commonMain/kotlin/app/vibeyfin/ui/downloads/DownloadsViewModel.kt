package app.vibeyfin.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadState
import app.vibeyfin.domain.NetworkState
import app.vibeyfin.domain.OfflineMode
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.domain.profileOrNull
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A show whose episodes are downloaded, or on their way, with the episodes in order. */
data class DownloadedShow(
    val seriesId: String,
    val title: String,
    /** The show's own picture on the device; null when it was not downloaded. */
    val posterUrl: String?,
    val posterBlurHash: String?,
    val episodes: List<Download>,
) {
    val bytes: Long get() = episodes.sumOf { it.bytes }
    val doneCount: Int get() = episodes.count { it.isDone }

    /** Some episode is paused or failed, so the show offers Resume (or Retry when one failed). */
    val canResume: Boolean get() = episodes.resumable.isNotEmpty()

    val resumeLabel: String
        get() = if (episodes.any { it.state == DownloadState.Failed }) Strings.retryFailedDownloads else Strings.resumeAllDownloads

    /** "3 episodes · 1.2 GB". */
    val line: String get() = Strings.showDownloadLine(episodes.size, bytes)
}

data class DownloadsUiState(
    /** The list has not been read yet. */
    val loading: Boolean = true,
    val movies: List<Download> = emptyList(),
    val shows: List<DownloadedShow> = emptyList(),
    /** What the profile's downloads take on this device. */
    val bytesUsed: Long = 0,
    /** What a download that is waiting is waiting for: a network, Wi-Fi or the server. */
    val waiting: DownloadRunner.Waiting = DownloadRunner.Waiting.Network,
    /** The question before downloads are deleted; null when none is asked. */
    val removal: RemovePrompt? = null,
    /** A download that could not be done, with what to try. */
    val message: String? = null,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    val isEmpty: Boolean get() = !loading && movies.isEmpty() && shows.isEmpty()

    /** "1.4 GB used"; null when nothing is downloaded. */
    val storageLine: String? get() = bytesUsed.takeIf { it > 0 }?.let(Strings::downloadStorageUsed)
}

/** The Downloads tab: what the profile has on this device, what is on its way, and the controls for both. */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val connection: ConnectionManager,
    private val downloads: DownloadRepository,
    offline: OfflineMode,
    profileSettings: ProfileSettings,
) : ViewModel() {
    private val flow = DownloadFlow(viewModelScope, downloads) { connection.state.value.profileOrNull() }

    private val content: Flow<DownloadsUiState> = connection.profile.flatMapLatest { profile ->
        if (profile == null) flowOf(DownloadsUiState(loading = false)) else load(profile)
    }

    // What waiting downloads wait for: no network, a network they may not use (mobile data), or a server that does not answer.
    private val waiting: Flow<DownloadRunner.Waiting> = combine(offline.networkState, profileSettings.downloadOverMobile) { network, mobile ->
        when {
            network == NetworkState.None -> DownloadRunner.Waiting.Network
            network == NetworkState.Metered && mobile != true -> DownloadRunner.Waiting.Wifi
            else -> DownloadRunner.Waiting.Server
        }
    }

    val uiState: StateFlow<DownloadsUiState> = combine(content, waiting, flow.removal, flow.message, connection.sessionEnded()) { state, waiting, removal, message, exit ->
        state.copy(waiting = waiting, removal = removal, message = message, exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    private fun load(profile: ProfileRef): Flow<DownloadsUiState> = combine(
        downloads.observeAll(profile),
        downloads.observeParents(profile),
        downloads.observeBytesUsed(profile),
    ) { all, parents, bytes ->
        val movies = all.filter { it.seriesId == null }.sortedBy { it.item.title.lowercase() }
        val shows = all.filter { it.seriesId != null }.groupBy { it.seriesId!! }.map { (seriesId, episodes) ->
            val show = parents[seriesId]
            DownloadedShow(
                seriesId = seriesId,
                title = show?.title ?: episodes.firstNotNullOfOrNull { it.item.seriesName }.orEmpty(),
                posterUrl = show?.posterUrl,
                posterBlurHash = show?.posterBlurHash,
                episodes = episodes.sortedWith(compareBy({ it.item.seasonNumber ?: 0 }, { it.item.episodeNumber ?: 0 })),
            )
        }.sortedBy { it.title.lowercase() }
        DownloadsUiState(loading = false, movies = movies, shows = shows, bytesUsed = bytes)
    }

    fun onAction(download: Download, action: DownloadAction) = flow.act(download, action)

    /** Resumes every paused or failed episode of [show]. */
    fun onResumeShow(show: DownloadedShow) = flow.resumeAll(show.episodes)

    /** Resumes every paused or failed episode of [show] that sits in the season [seasonId]. */
    fun onResumeSeason(show: DownloadedShow, seasonId: String) = flow.resumeAll(show.episodes.filter { it.seasonId == seasonId })

    /** Asks before removing every episode of [show]. */
    fun onRemoveShow(show: DownloadedShow) = flow.askRemove(show.episodes, show.title)

    /** Asks before removing everything. */
    fun onRemoveAll() {
        val state = uiState.value
        flow.askRemove(state.movies + state.shows.flatMap { it.episodes }, Strings.removeAllDownloads)
    }

    fun onConfirmRemove() = flow.confirmRemove()

    fun onDismissRemoval() = flow.dismissRemoval()

    fun onDismissMessage() = flow.dismissMessage()
}
