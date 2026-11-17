package app.vibeyfin.ui.subtitles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.valueOrNull
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.Language
import app.vibeyfin.domain.LanguagePreferences
import app.vibeyfin.domain.LanguagePrefs
import app.vibeyfin.domain.RemoteSubtitle
import app.vibeyfin.domain.StreamTrack
import app.vibeyfin.domain.SubtitleDownload
import app.vibeyfin.domain.SubtitleRepository
import app.vibeyfin.domain.namedBy
import app.vibeyfin.i18n.Texts
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.settings.languageChoices
import app.vibeyfin.ui.settings.matchesLanguageSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One result in the search sheet. */
data class SubtitleResultRow(
    val id: String,
    val name: String,
    /** The line under the name: provider, format and rating. */
    val details: String,
    val isMatch: Boolean,
    /** Already on the server for this item: it can't be chosen again. */
    val added: Boolean,
    /** What a screen reader says for the row. */
    val spoken: String,
)

/** One row of the language chooser; [code] is what to pass back to `onSelectLanguage`. */
data class SubtitleLanguageRow(val code: String, val name: String, val selected: Boolean)

/** Why the sheet has no results to show, in place of the list. */
data class SubtitleSearchProblem(val title: String, val text: String, val canRetry: Boolean)

/** A download that failed, shown over the results. */
data class SubtitleSearchMessage(val text: String, val canRetry: Boolean)

/** A subtitle now on the server; the player turns on [streamIndex], the page reads the item again. */
data class SubtitleAdded(val itemId: String, val streamIndex: Int, val languageName: String)

data class SubtitleSearchUiState(
    val languageName: String = "",
    /** The language chooser's rows, filtered by [languageQuery]. */
    val languages: List<SubtitleLanguageRow> = emptyList(),
    val languageQuery: String = "",
    val loading: Boolean = true,
    val results: List<SubtitleResultRow> = emptyList(),
    val problem: SubtitleSearchProblem? = null,
    /** The result being added; the other rows can't be chosen meanwhile. */
    val downloadingId: String? = null,
    val message: SubtitleSearchMessage? = null,
    /** A subtitle was added: the screen acts on it, closes the sheet and calls `onAddedHandled`. */
    val added: SubtitleAdded? = null,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
)

private enum class DownloadFault { NotArrived, NotArrivedHasFile, AddFailed }

private sealed interface SearchFault {
    data class NoResults(val language: String) : SearchFault

    data object NotAllowed : SearchFault

    data object Unreachable : SearchFault

    data class Failed(val error: ApiError) : SearchFault
}

private data class SearchFindings(
    val remote: List<RemoteSubtitle> = emptyList(),
    val possiblyAddedIds: Set<String> = emptySet(),
    val addedIds: Set<String> = emptySet(),
    val fault: SearchFault? = null,
    val downloadFault: DownloadFault? = null,
)

private fun SubtitleSearchUiState.localized(findings: SearchFindings): SubtitleSearchUiState = copy(
    results = findings.remote.map { it.toResultRow(findings) },
    problem = findings.fault?.problem(),
    message = findings.downloadFault?.message(),
)

private fun RemoteSubtitle.toResultRow(findings: SearchFindings): SubtitleResultRow {
    val possiblyAdded: Boolean = id in findings.possiblyAddedIds
    val added: Boolean = id in findings.addedIds
    val details: String = Strings.subtitleResultDetails(provider, format, rating, possiblyAdded)
    return SubtitleResultRow(id, name, details, isHashMatch, added, Strings.subtitleResultDescription(name, details, isHashMatch, added))
}

private fun SearchFault.problem(): SubtitleSearchProblem = when (this) {
    is SearchFault.NoResults -> SubtitleSearchProblem(Strings.noSubtitlesFound(language), Strings.noSubtitlesHint, canRetry = false)
    SearchFault.NotAllowed -> SubtitleSearchProblem(LoadMessages.SUBTITLES_NOT_ALLOWED_TITLE, LoadMessages.SUBTITLES_NOT_ALLOWED_TEXT, canRetry = false)
    SearchFault.Unreachable -> SubtitleSearchProblem(LoadMessages.SUBTITLES_UNREACHABLE_TITLE, LoadMessages.SUBTITLES_UNREACHABLE_TEXT, canRetry = true)
    is SearchFault.Failed -> SubtitleSearchProblem(LoadMessages.SUBTITLES_SEARCH_FAILED_TITLE, LoadMessages.forError(error), canRetry = true)
}

private fun DownloadFault.message(): SubtitleSearchMessage = when (this) {
    DownloadFault.NotArrived -> SubtitleSearchMessage(LoadMessages.SUBTITLES_DID_NOT_ARRIVE, canRetry = false)
    DownloadFault.NotArrivedHasFile -> SubtitleSearchMessage(LoadMessages.SUBTITLES_DID_NOT_ARRIVE_HAS_FILE, canRetry = false)
    DownloadFault.AddFailed -> SubtitleSearchMessage(LoadMessages.SUBTITLES_ADD_FAILED, canRetry = true)
}

/**
 * The "Search for subtitles" sheet of a screen, which lives as long as the screen, so a download goes on when the sheet closes.
 * The language chosen here applies to this search only; opening the sheet again starts from the profile's language.
 */
class SubtitleSearchViewModel(
    private val subtitles: SubtitleRepository,
    private val languages: LanguagePreferences,
    connection: ConnectionManager,
) : ViewModel() {
    private val content = MutableStateFlow(SubtitleSearchUiState())
    private val findings = MutableStateFlow(SearchFindings())

    private val localizedContent: Flow<SubtitleSearchUiState> = combine(content, findings, Texts.language) { content, findings, _ -> content.localized(findings) }

    val uiState: StateFlow<SubtitleSearchUiState> = combine(localizedContent, connection.sessionEnded()) { content, exit -> content.copy(exit = exit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleSearchUiState())

    private var itemId = ""
    private var languageCode = ""
    private var known: List<Language> = emptyList()
    private var prefs: LanguagePrefs? = null
    private var searching: Job? = null
    private var downloading: Job? = null
    private var failedDownload: String? = null
    private var chosenLanguage = false
    private var existing: List<StreamTrack> = emptyList()

    /** The sheet opens on [item]: it searches straight away. A download still going for that item is simply shown again. */
    fun onOpen(item: String) {
        if (item == itemId && downloading?.isActive == true) return
        // A subtitle for the item left behind is on its way to the server, but nothing here is waiting for it any more.
        downloading?.cancel()
        itemId = item
        failedDownload = null
        chosenLanguage = false
        searching?.cancel()
        findings.update { SearchFindings(addedIds = it.addedIds) }
        content.update { SubtitleSearchUiState(added = it.added) }
        searching = viewModelScope.launch {
            readLanguages()
            search()
        }
    }

    private suspend fun readLanguages() {
        known = languages.languages()
        prefs = languages.load().valueOrNull()
        if (!chosenLanguage) languageCode = subtitles.startingLanguage(prefs, known)
    }

    /** Searches again in [code], for this search only: the profile's setting stays as it is. */
    fun onSelectLanguage(code: String) {
        languageCode = code
        chosenLanguage = true
        failedDownload = null
        searching?.cancel()
        content.update { it.copy(languageQuery = "") }
        searching = viewModelScope.launch { search() }
    }

    fun onSearchLanguages(query: String) {
        content.update { it.copy(languageQuery = query, languages = languageRows(query)) }
    }

    /** Adds the result [id] to the item on the server. */
    fun onChoose(id: String) {
        if (downloading?.isActive == true || id in findings.value.addedIds) return
        val item = itemId
        val language = languageName()
        failedDownload = null
        content.update { it.copy(downloadingId = id) }
        findings.update { it.copy(downloadFault = null) }
        downloading = viewModelScope.launch {
            val outcome = subtitles.download(item, id)
            content.update { it.copy(downloadingId = null) }
            when (outcome) {
                is SubtitleDownload.Added -> {
                    findings.update { it.copy(addedIds = it.addedIds + id) }
                    content.update { it.copy(added = SubtitleAdded(item, outcome.streamIndex, language)) }
                }

                SubtitleDownload.NotArrived -> findings.update { it.copy(downloadFault = if (existing.isEmpty()) DownloadFault.NotArrived else DownloadFault.NotArrivedHasFile) }

                is SubtitleDownload.Failed -> onDownloadFailed(id, outcome.error)
            }
        }
    }

    private fun onDownloadFailed(id: String, error: ApiError) {
        when (error) {
            ApiError.Unauthorized -> Unit

            ApiError.Forbidden -> findings.update { it.copy(remote = emptyList(), fault = SearchFault.NotAllowed) }

            else -> {
                failedDownload = id
                findings.update { it.copy(downloadFault = DownloadFault.AddFailed) }
            }
        }
    }

    /** "Try again": repeats the download that failed, or else the search. */
    fun onRetry() {
        val failed = failedDownload
        if (failed != null) {
            onChoose(failed)
        } else {
            searching?.cancel()
            searching = viewModelScope.launch {
                if (known.isEmpty() || prefs == null) readLanguages()
                search()
            }
        }
    }

    fun onDismissMessage() {
        failedDownload = null
        findings.update { it.copy(downloadFault = null) }
    }

    fun onAddedHandled() {
        content.update { it.copy(added = null) }
    }

    private suspend fun search() {
        content.update { it.copy(loading = true, languageName = languageName(), languages = languageRows(it.languageQuery)) }
        findings.update { it.copy(remote = emptyList(), fault = null, downloadFault = null) }
        existing = subtitles.externalTracks(itemId).filter { inSearchLanguage(it) }
        val addedIds: Set<String> = if (existing.isEmpty()) emptySet() else subtitles.addedTo(itemId)
        findings.update { it.copy(addedIds = addedIds) }
        when (val result = subtitles.search(itemId, languageCode)) {
            is ApiResult.Success -> {
                val possiblyAddedIds: Set<String> = result.value.filter { subtitle -> existing.any(subtitle::namedBy) }.map { it.id }.toSet()
                val fault: SearchFault? = if (result.value.isEmpty()) SearchFault.NoResults(languageName()) else null
                findings.update { it.copy(remote = result.value, possiblyAddedIds = possiblyAddedIds, fault = fault) }
                content.update { it.copy(loading = false) }
            }

            is ApiResult.Failure -> {
                findings.update { it.copy(fault = faultFor(result.error)) }
                content.update { it.copy(loading = false) }
            }
        }
    }

    private fun faultFor(error: ApiError): SearchFault? = when (error) {
        ApiError.Unauthorized -> null
        ApiError.Forbidden -> SearchFault.NotAllowed
        ApiError.Network -> SearchFault.Unreachable
        else -> SearchFault.Failed(error)
    }

    private fun languageName() = known.firstOrNull { it.matches(languageCode) }?.name ?: languageCode

    private fun languageRows(query: String): List<SubtitleLanguageRow> = languageChoices(known, prefs ?: LanguagePrefs())
        .filter { matchesLanguageSearch(it.name, it.code, query) }
        .map { SubtitleLanguageRow(it.code, it.name, it.matches(languageCode)) }

    private fun inSearchLanguage(track: StreamTrack): Boolean {
        val language = track.language ?: return false

        return known.firstOrNull { it.matches(languageCode) }?.matches(language) ?: language.equals(languageCode, ignoreCase = true)
    }
}
