package app.vibeyfin.ui.tile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadTarget
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.WatchRepository
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadBadge
import app.vibeyfin.ui.downloads.DownloadFlow
import app.vibeyfin.ui.downloads.DownloadPrompt
import app.vibeyfin.ui.downloads.RemovePrompt
import app.vibeyfin.ui.downloads.badge
import app.vibeyfin.ui.downloads.downloadParts
import app.vibeyfin.ui.downloads.label
import app.vibeyfin.ui.downloads.profileOrNull
import app.vibeyfin.ui.downloads.progress
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.item.Trailer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the menu of a poster offers: the poster's item, and whether it has a trailer (known a moment after the menu opens). */
data class TileMenuUiState(
    /** The poster whose menu is open; null when none is. */
    val item: MediaItem? = null,
    /** The trailer to offer: one the server holds (it plays in the app) or a page on the web; null when there is none (yet). */
    val trailer: Trailer? = null,
    /** The server is being asked whether the title has a trailer; a grey line holds the row's place until it answers. */
    val checkingTrailer: Boolean = false,
    /** A change that couldn't be saved and was taken back. */
    val actionMessage: String? = null,
    /** Set when the session ended and the menu must close. */
    val exit: HomeExit? = null,
    /** The profile's downloads by item id: what the Download row shows of the item. */
    val downloads: Map<String, Download> = emptyMap(),
    /** The server lets this user download. Taken as yes until it says otherwise, so the row is not missing while that is read. */
    val canDownload: Boolean = true,
    /** The question before a show is downloaded (how many episodes, how much room); null when none is asked. */
    val downloadPrompt: DownloadPrompt? = null,
    /** The question before a download is deleted; null when none is asked. */
    val removePrompt: RemovePrompt? = null,
) {
    private val isSeries: Boolean get() = item?.kind == ItemKind.Series

    /** The tile's own movie or episode as a download, or null when it is not one. */
    val download: Download? get() = item?.let { downloads[it.id] }

    /** The downloads of a show's episodes, whatever their state. */
    val seriesDownloads: List<Download> get() = item?.takeIf { isSeries }?.let { show -> downloads.values.filter { it.seriesId == show.id } }.orEmpty()

    /** Whether the menu offers Download: for a movie, an episode or a show, when the server lets the user. */
    val offersDownload: Boolean get() = canDownload && (item?.canPlay == true || isSeries)

    /** What the row's icon shows: the tile's own download, or for a show all its episodes' together. */
    val downloadBadge: DownloadBadge get() = if (isSeries) seriesDownloads.badge() else download?.badge ?: DownloadBadge.None

    /** How far the downloads are, for the row's ring; null when no size is known. */
    val downloadProgress: Float? get() = if (isSeries) seriesDownloads.progress() else download?.progress

    /** The row's words: what a tap does while there is no download, and where it is once there is one. */
    val downloadLabel: String
        get() = when {
            downloadBadge == DownloadBadge.None -> if (isSeries) Strings.downloadAllEpisodes else Strings.download
            isSeries && downloadBadge == DownloadBadge.Done -> Strings.downloadedCount(seriesDownloads.size)
            else -> downloadBadge.label
        }
}

/** The line under the title in the poster menu: its year, then what it is ("1995 · Movie"). Only a movie or a show says what it is. */
val MediaItem.menuFacts: String
    get() = listOfNotNull(
        year?.toString(),
        when (kind) {
            ItemKind.Movie -> Strings.heroMovie
            ItemKind.Series -> Strings.heroShow
            else -> null
        },
    ).joinToString("  ·  ")

/**
 * The long-press menu of a poster, anywhere in the app: Details, Trailer (only when the title has one) and My List. A poster in a list
 * does not carry its trailers (the server leaves them out of lists), so they are looked up when the menu opens.
 */
class TileMenuViewModel(
    private val library: LibraryRepository,
    private val watch: WatchRepository,
    private val connection: ConnectionManager,
    private val downloads: DownloadRepository,
) : ViewModel() {
    private val content = MutableStateFlow(TileMenuUiState())
    private val downloadFlow = DownloadFlow(viewModelScope, downloads) { connection.state.value.profileOrNull() }
    private val canDownload = MutableStateFlow(true)
    private var permissionRead = false
    private var lookup: Job? = null
    private var writing = false

    // The user changed My List in this menu: the lookup's older answer must not undo it.
    private var toggled = false

    val uiState: StateFlow<TileMenuUiState> = combine(
        // Progress is only drawn while a menu is open; closed, the menu must not rebuild whatever shows it for every byte that arrives.
        content,
        connection.sessionEnded(),
        downloadParts(connection, downloads, downloadFlow, canDownload, live = content.map { it.item != null }),
    ) { content, exit, parts ->
        content.copy(
            exit = exit,
            actionMessage = content.actionMessage ?: parts.message,
            downloads = parts.byId,
            canDownload = parts.canDownload,
            downloadPrompt = parts.prompt,
            removePrompt = parts.removal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TileMenuUiState())

    /** A poster was long-pressed: the menu opens at once, and the trailer row fills in when the server has answered. */
    fun onOpen(item: MediaItem) {
        lookup?.cancel()
        toggled = false
        readDownloadPermission()
        // The same poster again (iOS asks early) keeps what the first look-up found.
        val kept = content.value.takeIf { it.item?.id == item.id }
        content.value = TileMenuUiState(
            item = kept?.item ?: item,
            trailer = kept?.trailer,
            checkingTrailer = kept == null && item.canHaveTrailer,
        )
        lookup = viewModelScope.launch {
            val detail = (library.item(item.id) as? ApiResult.Success)?.value
            val trailer = detail?.takeIf { item.canHaveTrailer }?.let { findTrailer(it) }
            content.update { current ->
                if (current.item?.id != item.id) {
                    current
                } else {
                    // The server's word on My List replaces the poster's own, which a search result does not carry.
                    current.copy(
                        item = if (detail != null && !toggled) current.item.copy(isFavorite = detail.isFavorite) else current.item,
                        trailer = trailer,
                        checkingTrailer = false,
                    )
                }
            }
        }
    }

    fun onClose() {
        lookup?.cancel()
        content.value = TileMenuUiState()
    }

    /** "My List" in the menu: shown at once, taken back with a message if it can't be saved. */
    fun onToggleFavorite() {
        val before = content.value.item ?: return
        if (writing) return
        writing = true
        toggled = true
        val target = !before.isFavorite
        content.update { it.copy(item = before.copy(isFavorite = target), actionMessage = null) }
        viewModelScope.launch {
            val result = watch.setFavorite(before.id, target)
            writing = false
            if (result is ApiResult.Failure) {
                content.update { it.copy(item = it.item?.copy(isFavorite = before.isFavorite), actionMessage = failureMessage(result.error, target)) }
            }
        }
    }

    fun onDismissMessage() {
        content.update { it.copy(actionMessage = null) }
        downloadFlow.dismissMessage()
    }

    /** The Download row: a movie or an episode goes into the queue at once; a show asks how many episodes first. */
    fun onDownload() {
        val item = content.value.item ?: return
        when {
            item.kind == ItemKind.Series -> downloadFlow.start(DownloadTarget.Series(item.id), item.title, ask = true)
            item.canPlay -> downloadFlow.start(DownloadTarget.Item(item.id), item.title, ask = false)
        }
    }

    /** Pause, resume or remove (after asking) the tile's own download. */
    fun onDownloadAction(action: DownloadAction) {
        val download = uiState.value.download ?: return
        downloadFlow.act(download, action)
    }

    fun onToggleUnwatchedOnly() = downloadFlow.toggleUnwatchedOnly()

    fun onConfirmDownload() = downloadFlow.confirm()

    fun onDismissDownloadPrompt() = downloadFlow.dismiss()

    fun onConfirmRemove() = downloadFlow.confirmRemove()

    fun onDismissRemoval() = downloadFlow.dismissRemoval()

    // Whether this user may download is read once, quietly: it only decides whether the row is there.
    private fun readDownloadPermission() {
        if (permissionRead) return
        permissionRead = true
        viewModelScope.launch {
            val profile = connection.state.value.profileOrNull() ?: run {
                permissionRead = false
                return@launch
            }
            downloads.canDownload(profile)?.let { canDownload.value = it }
        }
    }

    // A trailer the server holds first (it plays in the app), else one on the web; null when the title has neither.
    private suspend fun findTrailer(detail: MediaItem): Trailer? {
        if (detail.localTrailerCount > 0) {
            (library.localTrailer(detail.id) as? ApiResult.Success)?.value?.let { return Trailer.Local(it.id) }
        }
        return detail.remoteTrailerUrl?.let { Trailer.Remote(it) }
    }

    private fun failureMessage(error: ApiError, added: Boolean) = when {
        error == ApiError.Unauthorized -> null
        added -> LoadMessages.ADD_TO_MY_LIST_FAILED
        else -> LoadMessages.REMOVE_FROM_MY_LIST_FAILED
    }
}
