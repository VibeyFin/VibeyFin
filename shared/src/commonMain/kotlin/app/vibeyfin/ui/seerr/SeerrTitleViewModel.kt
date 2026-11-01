package app.vibeyfin.ui.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.seerr.QualityChoice
import app.vibeyfin.data.seerr.SeasonChoice
import app.vibeyfin.domain.RequestOutcome
import app.vibeyfin.domain.SeerrConnection
import app.vibeyfin.domain.SeerrRepository
import app.vibeyfin.domain.SeerrState
import app.vibeyfin.domain.model.QualityOptions
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrCastMember
import app.vibeyfin.domain.model.SeerrEpisode
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrRequest
import app.vibeyfin.domain.model.SeerrSeason
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.domain.model.SeerrTitleDetails
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.item.runtimeText
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** The page's one primary button. [enabled] false is the quiet button that holds the state, so the page doesn't jump. */
data class SeerrPrimary(val label: String, val enabled: Boolean)

/** A season on a show's Seerr page, with where it stands. Not tappable. */
data class SeerrSeasonRow(
    val number: Int,
    val name: String,
    val episodes: String,
    val year: Int?,
    val state: RequestState,
    /** The state's word ("Available", "Requested"); null for a season nobody asked for. */
    val stateLabel: String?,
)

/** A season in the picker. A [locked] one is available or already asked for, shows its state and can't be picked. */
data class SeerrPickerSeason(
    val number: Int,
    val name: String,
    val episodes: String,
    val selected: Boolean,
    val locked: Boolean,
    val state: RequestState,
    val stateLabel: String?,
    /** On its tile: "3", "SP" for Specials. */
    val tile: String = "",
    /** Under the tile: "10 ep". */
    val tileEpisodes: String = "",
    /** What a screen reader says for the tile: "Season 3, 10 episodes, Available". */
    val description: String = "",
)

/** A quality in the request sheet: a Radarr or Sonarr quality profile. */
data class SeerrQualityRow(val id: Int, val name: String, val selected: Boolean)

/**
 * The request sheet: a show's season picker, and for whoever may choose, the quality to ask for. A movie only opens it when
 * there is a quality to choose; otherwise its Request sends at once.
 */
data class SeerrSeasonPicker(
    val title: String,
    val subtitle: String,
    /** A show: the season list and "All missing seasons" show. */
    val showsSeasons: Boolean = true,
    /** The quality profiles to choose from; empty when the profile may not choose (Seerr's default quality is used). */
    val qualities: List<SeerrQualityRow> = emptyList(),
    /** The qualities are being read. */
    val qualitiesLoading: Boolean = false,
    /** "All missing seasons": every season that can be asked for except Specials. On by default. */
    val allMissing: Boolean,
    /** The profile may ask for 4K here, so a Standard / 4K choice shows above the seasons. */
    val offer4k: Boolean,
    val is4k: Boolean,
    val seasons: List<SeerrPickerSeason>,
    /** "Request 2 seasons". */
    val confirmLabel: String,
    val canConfirm: Boolean,
)

enum class SeerrNoticeAction { View, TryAgain }

/** A one-shot message after a request or a cancel: an Android snackbar, the iOS banner capsule. Never a dialog. */
data class SeerrNotice(
    val text: String,
    val action: SeerrNoticeAction? = null,
    val actionLabel: String? = null,
    val failure: Boolean = false,
)

/** The episodes of the season chosen on a show's Seerr page. */
data class SeerrEpisodes(
    val season: Int,
    val episodes: List<SeerrEpisode> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

data class SeerrTitleUiState(
    /** The title is being read: the page shows its skeleton, a poster menu its header with nothing under it yet. */
    val loading: Boolean = true,
    /** The title couldn't be read; the page offers Try again. */
    val message: String? = null,
    val title: SeerrTitle? = null,
    /** Year, certification and length, joined by the facts separator. */
    val facts: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val overview: String? = null,
    /** The whole status sentence ("Requested by you · Waiting for approval"), for screen readers and the 4K line. */
    val status: String? = null,
    /** The tag at the start of the facts: the state in a word or two ("Not in your library", "Requested"), with the state's icon. */
    val tag: String = "",
    /** Under the overview once someone asked: who, when and what is next ("By you, today · Waiting for approval"). */
    val statusDetail: String? = null,
    val state: RequestState = RequestState.NotRequested,
    /** A second line when a 4K request exists. */
    val status4k: String? = null,
    val primary: SeerrPrimary? = null,
    val trailerUrl: String? = null,
    /** A movie the profile may ask for in 4K: "Request in 4K" shows beside Trailer. A show offers 4K in the picker. */
    val offer4k: Boolean = false,
    /** The profile's own request still waiting for approval: "Cancel request" shows. */
    val cancelRequestId: Int? = null,
    val seasons: List<SeerrSeasonRow> = emptyList(),
    /** A show: the season whose episodes show under the season buttons, and those episodes. */
    val episodes: SeerrEpisodes? = null,
    val cast: List<SeerrCastMember> = emptyList(),
    val moreLikeThis: List<SeerrTitle> = emptyList(),
    val picker: SeerrSeasonPicker? = null,
    /** A request or a cancel is on its way: its button shows a spinner and ignores taps. */
    val sending: Boolean = false,
    val notice: SeerrNotice? = null,
    /** One-shot: the title is in the library, so the native screen opens the library's page in place of this one. */
    val openItem: String? = null,
    /** The poster menu's line under the title: "2025 · Movie · Not in your library". */
    val menuSubtitle: String = "",
    /** The poster menu's status row once someone asked for it ("Requested"); null while nobody has. */
    val menuStatus: String? = null,
    /** Under [menuStatus]: "By you, 2 days ago · Waiting for approval". */
    val menuStatusDetail: String? = null,
    /** The poster menu offers Request (or Request again). */
    val canRequest: Boolean = false,
)

/**
 * A title that isn't in the library, from Seerr: its page, the poster menu a long press opens, the season picker, and asking for
 * it. A request is a write, so it is sent once and never repeated by itself; Try again on its failure sends it again.
 */
class SeerrTitleViewModel(
    private val type: SeerrMediaType,
    private val tmdbId: Int,
    private val repository: SeerrRepository,
    private val seerr: SeerrConnection,
    private val clock: Clock,
) : ViewModel() {
    private val content = MutableStateFlow(SeerrTitleUiState())
    val uiState: StateFlow<SeerrTitleUiState> = content.asStateFlow()

    private var details: SeerrTitleDetails? = null

    // IMDb's rating, read beside the page (Seerr looks it up, which can be slow) and added to the facts when it comes.
    private var imdb: Double? = null

    // Whether Seerr answered the IMDb lookup, a "none" included, so a movie without a rating isn't looked up on every reload.
    private var imdbAsked = false
    private var lastAttempt: Triple<SeasonChoice?, Boolean, QualityChoice?>? = null

    init {
        load(showSkeleton = true)
    }

    /** A season button: its episodes replace the ones shown. */
    fun onSelectSeason(number: Int) {
        if (content.value.episodes?.season == number) return
        loadEpisodes(number)
    }

    /** Try again after a season's episodes couldn't be read. */
    fun onRetryEpisodes() {
        content.value.episodes?.season?.let(::loadEpisodes)
    }

    private var episodesLoad: kotlinx.coroutines.Job? = null

    private fun loadEpisodes(season: Int) {
        episodesLoad?.cancel()
        content.update { it.copy(episodes = SeerrEpisodes(season)) }
        episodesLoad = viewModelScope.launch {
            val result = repository.season(tmdbId, season)
            content.update { state ->
                if (state.episodes?.season != season) return@update state
                state.copy(
                    episodes = when (result) {
                        is ApiResult.Success -> SeerrEpisodes(season, result.value, loading = false)
                        is ApiResult.Failure -> SeerrEpisodes(season, loading = false, message = SeerrMessages.UNREACHABLE_NOW)
                    },
                )
            }
        }
    }

    /** Try again after the title couldn't be read. */
    fun onRetry() = load(showSkeleton = true)

    /** The primary button, or Request in the poster menu: a movie is asked for at once, a show opens the season picker. */
    fun onRequest() = request(is4k = false)

    /** "Request in 4K": a movie at once; a show's picker opens on 4K. */
    fun onRequest4k() = request(is4k = true)

    fun onPickerAllMissing(on: Boolean) {
        val picker = content.value.picker ?: return
        val chosen = if (on) defaultSeasons(picker.is4k) else emptySet()
        content.update { it.copy(picker = picker(picker.is4k, chosen, allMissing = on)) }
    }

    fun onPickerSeason(number: Int) {
        val picker = content.value.picker ?: return
        val season = picker.seasons.firstOrNull { it.number == number }?.takeIf { !it.locked } ?: return
        val chosen = picker.seasons.filter { it.selected }.map { it.number }.toSet().let { if (season.selected) it - number else it + number }
        content.update { it.copy(picker = picker(picker.is4k, chosen, allMissing = chosen == defaultSeasons(picker.is4k))) }
    }

    /** Standard or 4K. The qualities on offer are that tier's server's. */
    fun onPickerQuality(is4k: Boolean) {
        chosenProfile = null
        content.update { it.copy(picker = picker(is4k, defaultSeasons(is4k), allMissing = true)) }
        loadQualities(is4k)
    }

    /** A quality profile in the request sheet. */
    fun onPickerProfile(id: Int) {
        val picker = content.value.picker ?: return
        chosenProfile = id
        content.update { it.copy(picker = picker(picker.is4k, picker.seasons.filter { season -> season.selected }.map { season -> season.number }.toSet(), picker.allMissing)) }
    }

    fun onPickerConfirm() {
        val picker = content.value.picker ?: return
        val quality = qualityChoice(picker.is4k)
        if (!picker.showsSeasons) {
            send(null, picker.is4k, quality)
            return
        }
        val chosen = picker.seasons.filter { it.selected }.map { it.number }
        if (chosen.isEmpty()) return
        send(SeasonChoice.Only(chosen), picker.is4k, quality)
    }

    fun onPickerDismiss() {
        content.update { it.copy(picker = null) }
    }

    /** "Cancel request": withdraws the profile's own request while it waits for approval. */
    fun onCancelRequest() {
        val id = content.value.cancelRequestId ?: return
        if (content.value.sending) return
        content.update { it.copy(sending = true) }
        viewModelScope.launch {
            val result = repository.cancel(id)
            content.update {
                it.copy(
                    sending = false,
                    notice = if (result is ApiResult.Success) SeerrNotice(Strings.seerrRequestCancelled) else SeerrNotice(SeerrMessages.CANCEL_FAILED, failure = true),
                )
            }
            if (result is ApiResult.Success) load(showSkeleton = false)
        }
    }

    /** "Try again" on a request that couldn't reach Seerr: the same request once more. */
    fun onRetryRequest() {
        val (seasons, is4k, quality) = lastAttempt ?: return
        send(seasons, is4k, quality)
    }

    /** The notice was shown. */
    fun onNoticeShown() {
        content.update { it.copy(notice = null) }
    }

    /** The native screen opened the library's page instead. */
    fun onItemOpened() {
        content.update { it.copy(openItem = null) }
    }

    private fun request(is4k: Boolean) {
        if (content.value.sending || details == null) return
        val choosesQuality = (seerr.state.value as? SeerrState.Ready)?.user?.permissions?.canChooseQuality == true
        if (type == SeerrMediaType.Movie && !choosesQuality) {
            send(null, is4k, null)
        } else {
            chosenProfile = null
            content.update { it.copy(picker = picker(is4k, defaultSeasons(is4k), allMissing = true)) }
            if (choosesQuality) loadQualities(is4k)
        }
    }

    // The quality profiles of each tier (false standard, true 4K), read once; null where Seerr has no server for it.
    private val qualities = mutableMapOf<Boolean, QualityOptions?>()
    private val qualitiesLoading = mutableSetOf<Boolean>()
    private var chosenProfile: Int? = null

    private fun loadQualities(is4k: Boolean) {
        if (is4k in qualities || is4k in qualitiesLoading) return
        qualitiesLoading += is4k
        refreshPicker()
        viewModelScope.launch {
            val result = repository.qualityOptions(type, is4k)
            qualitiesLoading -= is4k
            // Only an answer is kept: after a failure the next time the sheet opens asks again (this time Seerr's default is used)
            if (result is ApiResult.Success) qualities[is4k] = result.value
            refreshPicker()
        }
    }

    private fun refreshPicker() {
        content.update { state ->
            val picker = state.picker ?: return@update state
            state.copy(picker = picker(picker.is4k, picker.seasons.filter { it.selected }.map { it.number }.toSet(), picker.allMissing))
        }
    }

    // What the request asks for: the chosen profile, or the server's default, on that tier's server; null when there is nothing to choose.
    private fun qualityChoice(is4k: Boolean): QualityChoice? {
        val options = qualities[is4k] ?: return null
        val profile = chosenProfile ?: options.defaultProfileId ?: return null
        return QualityChoice(options.serverId, profile)
    }

    private fun send(seasons: SeasonChoice?, is4k: Boolean, quality: QualityChoice?) {
        if (content.value.sending) return
        lastAttempt = Triple(seasons, is4k, quality)
        content.update { it.copy(sending = true) }
        viewModelScope.launch {
            val outcome = repository.request(type, tmdbId, seasons, is4k, quality)
            val name = content.value.title?.title.orEmpty()
            val notice = when (outcome) {
                is RequestOutcome.Sent -> SeerrNotice(Strings.seerrRequested(name), SeerrNoticeAction.View, Strings.seerrView)

                is RequestOutcome.QuotaReached -> {
                    val left = outcome.quota?.forType(type)
                    val text = if (left != null && left.remaining > 0) {
                        SeerrMessages.tooManySeasons(left.remaining)
                    } else {
                        SeerrMessages.quotaReached(left?.limit, left?.days, movies = type == SeerrMediaType.Movie)
                    }
                    SeerrNotice(text, failure = true)
                }

                RequestOutcome.AlreadyRequested -> SeerrNotice(SeerrMessages.ALREADY_REQUESTED, failure = true)

                RequestOutcome.NotAllowed -> SeerrNotice(SeerrMessages.NOT_ALLOWED, failure = true)

                is RequestOutcome.Failed -> if (outcome.error == ApiError.Network) {
                    SeerrNotice(SeerrMessages.REQUEST_UNREACHABLE, SeerrNoticeAction.TryAgain, Strings.tryAgain, failure = true)
                } else {
                    SeerrNotice(SeerrMessages.REQUEST_FAILED, SeerrNoticeAction.TryAgain, Strings.tryAgain, failure = true)
                }
            }
            content.update { it.copy(sending = false, picker = null, notice = notice) }
            // What Seerr now says about the title: requested, approved, or already asked for by someone.
            if (outcome is RequestOutcome.Sent || outcome == RequestOutcome.AlreadyRequested) load(showSkeleton = false)
        }
    }

    private fun load(showSkeleton: Boolean) {
        if (showSkeleton) content.update { it.copy(loading = true, message = null) }
        if (type == SeerrMediaType.Movie && !imdbAsked) {
            viewModelScope.launch {
                val rating = repository.imdbRating(tmdbId) as? ApiResult.Success ?: return@launch
                imdbAsked = true
                imdb = rating.value ?: return@launch
                content.update { state -> details?.let { state.copy(facts = facts(it)) } ?: state }
            }
        }
        viewModelScope.launch {
            val more = async { repository.recommendations(type, tmdbId) }
            when (val result = repository.title(type, tmdbId)) {
                is ApiResult.Success -> {
                    details = result.value
                    // The page shows as soon as the title is read (keeping the More like this it had); the recommendations, slow at
                    // times, follow on their own
                    content.update { show(it, result.value, it.moreLikeThis) }
                    launch {
                        val similar = (more.await() as? ApiResult.Success)?.value?.titles ?: return@launch
                        content.update { it.copy(moreLikeThis = similar) }
                    }
                    // A show opens on its first season (Specials only when it has nothing else); a reload keeps the chosen one.
                    if (type == SeerrMediaType.Show && content.value.episodes == null) {
                        val numbers = result.value.seasons.map { it.number }
                        (numbers.firstOrNull { it > 0 } ?: numbers.firstOrNull())?.let(::loadEpisodes)
                    }
                }

                is ApiResult.Failure -> {
                    more.cancel()
                    val message = if (result.error == ApiError.NotFound) SeerrMessages.TITLE_NOT_FOUND else SeerrMessages.UNREACHABLE_NOW
                    content.update { it.copy(loading = false, message = message) }
                }
            }
        }
    }

    private fun show(current: SeerrTitleUiState, details: SeerrTitleDetails, similar: List<SeerrTitle>): SeerrTitleUiState {
        val title = details.title
        val ready = seerr.state.value as? SeerrState.Ready
        val permissions = ready?.user?.permissions
        val mayRequest = permissions?.canRequest(type) != false
        val offer4k = ready != null && ready.features.has4k(type) && permissions?.canRequest4k(type) == true &&
            (details.state4k == RequestState.NotRequested || details.state4k == RequestState.Declined)
        val latest = details.requests.filter { !it.is4k }.maxByOrNull { it.id }
        val latest4k = details.requests.filter { it.is4k }.maxByOrNull { it.id }
        val asker = latest?.takeIf { !it.byMe }?.requestedBy
        val inLibrary = title.jellyfinItemId != null && (title.state == RequestState.Available || title.state == RequestState.PartlyAvailable)
        return current.copy(
            loading = false,
            message = null,
            title = title,
            facts = facts(details),
            genres = details.genres,
            overview = title.overview,
            status = Strings.seerrStatusLine(title.state, latest?.byMe == true, asker),
            tag = Strings.seerrTag(title.state),
            statusDetail = if (title.state == RequestState.NotRequested) null else menuDetail(title.state, latest).takeIf { it.isNotEmpty() },
            state = title.state,
            status4k = details.state4k.takeIf { it != RequestState.NotRequested }
                ?.let { Strings.seerr4kLine(Strings.seerrStatusLine(it, latest4k?.byMe == true, latest4k?.takeIf { request -> !request.byMe }?.requestedBy)) },
            primary = primary(title.state, mayRequest, missing = defaultSeasons(is4k = false).isNotEmpty()),
            trailerUrl = details.trailerUrl,
            offer4k = offer4k && type == SeerrMediaType.Movie,
            cancelRequestId = details.requests.filter { it.byMe && it.pendingApproval }.maxByOrNull { it.id }?.id,
            seasons = details.seasons.map { season ->
                SeerrSeasonRow(season.number, season.label(), Strings.seerrEpisodeCount(season.episodeCount), season.year, season.state, Strings.seerrStateLabel(season.state))
            },
            cast = details.cast,
            moreLikeThis = similar,
            openItem = if (inLibrary) title.jellyfinItemId else null,
            menuSubtitle = Strings.seerrMenuSubtitle(Strings.seerrTitleMeta(title.year, title.type), title.state),
            menuStatus = Strings.seerrStateLabel(title.state),
            menuStatusDetail = if (title.state == RequestState.NotRequested) null else menuDetail(title.state, latest),
            canRequest = mayRequest && primary(title.state, mayRequest = true, missing = defaultSeasons(is4k = false).isNotEmpty()).enabled,
            // The picker keeps 4K open after a reload only if it is still offered
            picker = current.picker?.takeIf { !it.is4k || offer4k },
        )
    }

    // [missing]: a show has seasons nobody asked for yet, which a partly available show can still request.
    private fun facts(details: SeerrTitleDetails) = listOfNotNull(
        details.title.year?.toString(),
        details.certification,
        details.runtimeMinutes?.let(::runtimeText),
        imdb?.let(Strings::starRating),
    )

    private fun primary(state: RequestState, mayRequest: Boolean, missing: Boolean): SeerrPrimary {
        val ask = if (type == SeerrMediaType.Movie) Strings.seerrRequest else Strings.seerrRequestSeasons
        return when {
            state == RequestState.NotRequested -> SeerrPrimary(ask, enabled = mayRequest)
            state == RequestState.Declined -> SeerrPrimary(Strings.seerrRequestAgain, enabled = mayRequest)
            type == SeerrMediaType.Show && missing && state != RequestState.Available -> SeerrPrimary(ask, enabled = mayRequest)
            else -> SeerrPrimary(Strings.seerrStateLabel(state) ?: ask, enabled = false)
        }
    }

    private fun menuDetail(state: RequestState, latest: SeerrRequest?): String {
        val days = daysSince(clock, latest?.createdAt)
        return Strings.seerrMenuStatusDetail(state, latest?.byMe == true, latest?.takeIf { !it.byMe }?.requestedBy, days)
    }

    // The seasons that can be asked for, in the quality given: nobody asked, or the request was declined.
    private fun requestable(is4k: Boolean): List<SeerrSeason> = details?.seasons.orEmpty().filter {
        val state = if (is4k) it.state4k else it.state
        state == RequestState.NotRequested || state == RequestState.Declined
    }

    // "All missing seasons": every requestable season but Specials. Request stays offered on a show only while one of these is left:
    // Specials alone, never asked for by default, doesn't keep it.
    private fun defaultSeasons(is4k: Boolean): Set<Int> = requestable(is4k).map { it.number }.filter { it != 0 }.toSet()

    private fun picker(is4k: Boolean, chosen: Set<Int>, allMissing: Boolean): SeerrSeasonPicker {
        val details = details
        val open = requestable(is4k).map { it.number }.toSet()
        val ready = seerr.state.value as? SeerrState.Ready
        val offer4k = ready != null && ready.features.has4k(type) && ready.user.permissions.canRequest4k(type)
        // Specials last, so the grid starts at season 1
        val seasons = details?.seasons.orEmpty().sortedBy { if (it.number == 0) Int.MAX_VALUE else it.number }.map { season ->
            val state = if (is4k) season.state4k else season.state
            SeerrPickerSeason(
                number = season.number,
                name = season.label(),
                episodes = Strings.seerrEpisodeCount(season.episodeCount),
                selected = season.number in chosen,
                locked = season.number !in open,
                state = state,
                stateLabel = if (season.number in open) null else Strings.seerrStateLabel(state),
                tile = Strings.seerrSeasonTile(season.number),
                tileEpisodes = Strings.seerrEpisodeCountShort(season.episodeCount),
                description = Strings.seerrSeasonTileDescription(
                    season.label(),
                    Strings.seerrEpisodeCount(season.episodeCount),
                    if (season.number in open) null else Strings.seerrStateLabel(state),
                ),
            )
        }
        val count = chosen.size
        val movie = type == SeerrMediaType.Movie
        val options = qualities[is4k]
        val selectedProfile = chosenProfile ?: options?.defaultProfileId
        return SeerrSeasonPicker(
            title = if (movie) Strings.seerrRequest else Strings.seerrRequestSeasons,
            subtitle = if (movie) {
                details?.title?.let { Strings.seerrPickerMovieSubtitle(it.title, Strings.seerrTitleMeta(it.year, it.type)) }.orEmpty()
            } else {
                Strings.seerrPickerSubtitle(details?.title?.title.orEmpty(), details?.seasons.orEmpty().count { it.number != 0 })
            },
            showsSeasons = !movie,
            qualities = options?.profiles.orEmpty().map { SeerrQualityRow(it.id, it.name ?: Strings.seerrQualityNumber(it.id), selected = it.id == selectedProfile) },
            qualitiesLoading = is4k in qualitiesLoading,
            allMissing = allMissing,
            offer4k = offer4k,
            is4k = is4k,
            seasons = seasons,
            confirmLabel = if (movie) Strings.seerrRequest else Strings.seerrRequestSeasonCount(count),
            canConfirm = movie || count > 0,
        )
    }

    private fun SeerrSeason.label(): String = if (number == 0) Strings.specials else name?.takeIf { it.isNotBlank() } ?: Strings.season(number)
}
