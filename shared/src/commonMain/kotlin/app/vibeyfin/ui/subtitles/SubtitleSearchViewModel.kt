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
import app.vibeyfin.domain.SubtitleDownload
import app.vibeyfin.domain.SubtitleRepository
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.settings.languageChoices
import app.vibeyfin.ui.settings.matchesLanguageSearch
import kotlinx.coroutines.Job
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
    /** What a screen reader says for the row. */
    val description: String,
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

    val uiState: StateFlow<SubtitleSearchUiState> = combine(content, connection.sessionEnded()) { content, exit -> content.copy(exit = exit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleSearchUiState())

    private var itemId = ""
    private var languageCode = ""
    private var known: List<Language> = emptyList()
    private var prefs: LanguagePrefs? = null
    private var searching: Job? = null
    private var downloading: Job? = null
    private var failedDownload: String? = null

    /** The sheet opens on [item]: it searches straight away. A download still going for that item is simply shown again. */
    fun onOpen(item: String) {
        if (item == itemId && downloading?.isActive == true) return
        itemId = item
        failedDownload = null
        searching?.cancel()
        content.update { SubtitleSearchUiState(added = it.added) }
        searching = viewModelScope.launch {
            known = languages.languages()
            prefs = languages.load().valueOrNull()
            languageCode = subtitles.startingLanguage(prefs, known)
            search()
        }
    }

    /** Searches again in [code], for this search only: the profile's setting stays as it is. */
    fun onSelectLanguage(code: String) {
        languageCode = code
        searching?.cancel()
        content.update { it.copy(languageQuery = "") }
        searching = viewModelScope.launch { search() }
    }

    fun onSearchLanguages(query: String) {
        content.update { it.copy(languageQuery = query, languages = languageRows(query)) }
    }

    /** Adds the result [id] to the item on the server. */
    fun onChoose(id: String) {
        if (downloading?.isActive == true) return
        val item = itemId
        failedDownload = null
        content.update { it.copy(downloadingId = id, message = null) }
        downloading = viewModelScope.launch {
            val outcome = subtitles.download(item, id)
            if (item != itemId) {
                if (outcome is SubtitleDownload.Added) content.update { it.copy(added = SubtitleAdded(item, outcome.streamIndex, languageName())) }
                return@launch
            }
            content.update { it.copy(downloadingId = null) }
            when (outcome) {
                is SubtitleDownload.Added -> content.update { it.copy(added = SubtitleAdded(item, outcome.streamIndex, languageName())) }

                SubtitleDownload.NotArrived -> content.update { it.copy(message = SubtitleSearchMessage(LoadMessages.SUBTITLES_DID_NOT_ARRIVE, canRetry = false)) }

                is SubtitleDownload.Failed -> onDownloadFailed(id, outcome.error)
            }
        }
    }

    private fun onDownloadFailed(id: String, error: ApiError) {
        when (error) {
            ApiError.Unauthorized -> Unit

            ApiError.Forbidden -> content.update { it.copy(results = emptyList(), problem = notAllowed()) }

            else -> {
                failedDownload = id
                content.update { it.copy(message = SubtitleSearchMessage(LoadMessages.SUBTITLES_ADD_FAILED, canRetry = true)) }
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
            searching = viewModelScope.launch { search() }
        }
    }

    fun onDismissMessage() {
        failedDownload = null
        content.update { it.copy(message = null) }
    }

    fun onAddedHandled() {
        content.update { it.copy(added = null) }
    }

    private suspend fun search() {
        content.update {
            it.copy(loading = true, results = emptyList(), problem = null, message = null, languageName = languageName(), languages = languageRows(it.languageQuery))
        }
        when (val result = subtitles.search(itemId, languageCode)) {
            is ApiResult.Success -> content.update {
                val problem = if (result.value.isEmpty()) SubtitleSearchProblem(Strings.noSubtitlesFound(languageName()), Strings.noSubtitlesHint, canRetry = false) else null
                it.copy(loading = false, results = result.value.map { subtitle -> subtitle.toRow() }, problem = problem)
            }

            is ApiResult.Failure -> content.update { it.copy(loading = false, problem = problemFor(result.error)) }
        }
    }

    private fun problemFor(error: ApiError): SubtitleSearchProblem? = when (error) {
        ApiError.Unauthorized -> null
        ApiError.Forbidden -> notAllowed()
        ApiError.Network -> SubtitleSearchProblem(LoadMessages.SUBTITLES_UNREACHABLE_TITLE, LoadMessages.SUBTITLES_UNREACHABLE_TEXT, canRetry = true)
        else -> SubtitleSearchProblem(LoadMessages.SUBTITLES_SEARCH_FAILED_TITLE, LoadMessages.forError(error), canRetry = true)
    }

    private fun notAllowed() = SubtitleSearchProblem(LoadMessages.SUBTITLES_NOT_ALLOWED_TITLE, LoadMessages.SUBTITLES_NOT_ALLOWED_TEXT, canRetry = false)

    private fun languageName() = known.firstOrNull { it.matches(languageCode) }?.name ?: languageCode

    private fun languageRows(query: String): List<SubtitleLanguageRow> = languageChoices(known, prefs ?: LanguagePrefs())
        .filter { matchesLanguageSearch(it.name, it.code, query) }
        .map { SubtitleLanguageRow(it.code, it.name, it.matches(languageCode)) }

    private fun RemoteSubtitle.toRow(): SubtitleResultRow {
        val details = Strings.subtitleResultDetails(provider, format, rating)
        return SubtitleResultRow(id, name, details, isHashMatch, Strings.subtitleResultDescription(name, details, isHashMatch))
    }
}
