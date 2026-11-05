package app.vibeyfin.ui.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadTarget
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.profileOrNull
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.downloads.DownloadFlow
import app.vibeyfin.ui.downloads.DownloadPrompt
import app.vibeyfin.ui.downloads.RemovePrompt
import app.vibeyfin.ui.downloads.downloadParts
import app.vibeyfin.ui.downloads.isDone
import app.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildrenUiState(
    val items: List<MediaItem> = emptyList(),
    /** The first page is on its way. */
    val loading: Boolean = true,
    /** Why the first page didn't load, with what to try. */
    val message: String? = null,
    /** The list is longer than what is loaded; ask for the next page with `onLoadMore`. */
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** The next page didn't load; the items so far stay, and `onRetry` asks again. */
    val loadMoreFailed: Boolean = false,
    /** On a season's list: the show it belongs to, so the page can lead back to it. Null for anything else. */
    val series: SeriesLink? = null,
    /** When the list is a collection's members: the collection itself, for its picture, title and overview. Null for anything else. */
    val collection: MediaItem? = null,
    /** How many items the list holds in all, when the server said (a category page shows it under its title). */
    val total: Int? = null,
    /** A new list is on its way (a filter changed) while the old one is still shown. */
    val refreshing: Boolean = false,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
    /** What an empty list says: that there is nothing, or that the filters left nothing. */
    val emptyText: String = Strings.emptyFolder,
    /** The profile's downloads by item id; on a season's list, the state of each episode. */
    val downloads: Map<String, Download> = emptyMap(),
    /** The server lets this user download. */
    val canDownload: Boolean = true,
    /** The question before the season is downloaded; null when none is asked. */
    val downloadPrompt: DownloadPrompt? = null,
    /** The question before the season's downloads are deleted; null when none is asked. */
    val removePrompt: RemovePrompt? = null,
    /** A download that could not be started, with what to try. */
    val downloadMessage: String? = null,
) {
    /** Whether the list is a season's episodes and the user may download them: the page offers "Download season". */
    val offersSeasonDownload: Boolean get() = canDownload && series != null && items.isNotEmpty()

    /** How many of the season's episodes (of those loaded) are downloaded. */
    val seasonDownloaded: Int get() = items.count { downloads[it.id]?.isDone == true }

    /** The season's downloads, whatever their state, for removing them. */
    val seasonDownloads: List<Download> get() = items.mapNotNull { downloads[it.id] }

    val isEmpty: Boolean get() = !loading && message == null && items.isEmpty()

    /**
     * A collection's members under sub-headings: its movies, then its shows, then whatever else it holds. Each group is oldest first, as on
     * Home, with the title breaking a tie; a group with nothing in it is left out. Screens show it for a collection's page, whether or not
     * [collection] itself has been read yet.
     */
    val collectionGroups: List<CollectionGroup>
        get() {
            val oldestFirst = compareBy<MediaItem>({ it.year ?: Int.MAX_VALUE }, { it.title })

            return listOf(
                CollectionGroup(Strings.collectionMovies, items.filter { it.kind == ItemKind.Movie }.sortedWith(oldestFirst)),
                CollectionGroup(Strings.collectionShows, items.filter { it.kind == ItemKind.Series }.sortedWith(oldestFirst)),
                CollectionGroup(Strings.collectionOther, items.filter { it.kind != ItemKind.Movie && it.kind != ItemKind.Series }.sortedWith(oldestFirst)),
            ).filter { it.items.isNotEmpty() }
        }

    /** A collection's facts line, "2 movies · 1 show · 1999–2026"; empty for anything else or before the members are in. */
    val collectionFacts: List<String>
        get() {
            if (collection == null || items.isEmpty()) return emptyList()
            val movies = items.count { it.kind == ItemKind.Movie }
            val shows = items.count { it.kind == ItemKind.Series }
            val years = items.mapNotNull { it.year }
            val span = when {
                years.isEmpty() -> null
                years.min() == years.max() -> "${years.min()}"
                else -> "${years.min()}\u2013${years.max()}"
            }
            return listOfNotNull(
                movies.takeIf { it > 0 }?.let(Strings::collectionMovieCount),
                shows.takeIf { it > 0 }?.let(Strings::collectionShowCount),
                span,
            ).ifEmpty { listOf(Strings.collectionTitles(items.size)) }
        }
}

/** One sub-heading of a collection and the members under it. */
data class CollectionGroup(val title: String, val items: List<MediaItem>)

/** The show a season belongs to. */
data class SeriesLink(val id: String, val name: String)

/** What is inside one library, folder, series or season, a page at a time. */
class ChildrenViewModel(
    private val parentId: String,
    private val library: LibraryRepository,
    private val connection: ConnectionManager,
    private val downloads: DownloadRepository,
) : ViewModel() {
    private val content = MutableStateFlow(ChildrenUiState())
    private val downloadFlow = DownloadFlow(viewModelScope, downloads) { connection.state.value.profileOrNull() }
    private val canDownload = MutableStateFlow(true)
    private val downloadParts = downloadParts(connection, downloads, downloadFlow, canDownload)

    val uiState: StateFlow<ChildrenUiState> = combine(content, connection.sessionEnded(), downloadParts) { content, exit, parts ->
        content.copy(
            exit = exit,
            downloads = parts.byId,
            canDownload = parts.canDownload,
            downloadPrompt = parts.prompt,
            removePrompt = parts.removal,
            downloadMessage = parts.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChildrenUiState())

    init {
        loadFirstPage()
        findSeries()
        viewModelScope.launch {
            val profile = connection.state.value.profileOrNull() ?: return@launch
            downloads.canDownload(profile)?.let { canDownload.value = it }
        }
    }

    /** "Download season" on a season's list: asks first, with how many episodes and how much room. */
    fun onDownloadSeason() {
        val series = content.value.series ?: return
        downloadFlow.start(DownloadTarget.Season(series.id, parentId), Strings.downloadSeasonButton, ask = true)
    }

    /** Asks before removing the season's downloads. */
    fun onRemoveSeasonDownloads() {
        val state = uiState.value
        downloadFlow.askRemove(state.seasonDownloads, Strings.downloadSeasonButton)
    }

    fun onToggleUnwatchedOnly() = downloadFlow.toggleUnwatchedOnly()

    fun onConfirmDownload() = downloadFlow.confirm()

    fun onDismissDownloadPrompt() = downloadFlow.dismiss()

    fun onConfirmRemove() = downloadFlow.confirmRemove()

    fun onDismissRemoval() = downloadFlow.dismissRemoval()

    fun onRetryDownload() = downloadFlow.retry()

    fun onDismissDownloadMessage() = downloadFlow.dismissMessage()

    // A season's list offers a way to its show and a collection's list its own header; for any other folder the lookup finds
    // nothing and the page is as it was.
    private fun findSeries() {
        viewModelScope.launch {
            val parent = (library.item(parentId) as? ApiResult.Success)?.value ?: return@launch
            if (parent.kind == ItemKind.Collection) {
                content.update { it.copy(collection = parent) }
                return@launch
            }
            val seriesId = parent.seriesId ?: return@launch
            if (parent.kind != ItemKind.Season) return@launch
            val link = SeriesLink(seriesId, parent.seriesName ?: parent.title)
            content.update { it.copy(series = link) }
        }
    }

    /** Called as the end of the loaded items comes into view. */
    fun onLoadMore() {
        val current = content.value
        if (!current.hasMore || current.loading || current.loadingMore) return
        content.value = current.copy(loadingMore = true, loadMoreFailed = false)
        viewModelScope.launch {
            when (val result = library.children(parentId, startIndex = current.items.size)) {
                is ApiResult.Success -> content.update {
                    val items = it.items + result.value.items
                    it.copy(
                        items = items,
                        hasMore = result.value.items.isNotEmpty() && items.size < result.value.total,
                        loadingMore = false,
                    )
                }

                is ApiResult.Failure -> content.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    /** Tries again whatever failed: the first page, or the next one. */
    fun onRetry() {
        if (content.value.items.isEmpty()) loadFirstPage() else onLoadMore()
    }

    private fun loadFirstPage() {
        content.update { ChildrenUiState(loading = true, series = it.series, collection = it.collection) }
        viewModelScope.launch {
            // Updated in one step: the lookup of the series or collection may land at any time, and must not be overwritten.
            when (val result = library.children(parentId, startIndex = 0)) {
                is ApiResult.Success -> content.update {
                    ChildrenUiState(
                        items = result.value.items,
                        loading = false,
                        hasMore = result.value.hasMore,
                        series = it.series,
                        collection = it.collection,
                    )
                }

                is ApiResult.Failure -> content.update {
                    ChildrenUiState(loading = false, message = LoadMessages.forError(result.error), series = it.series, collection = it.collection)
                }
            }
        }
    }
}
