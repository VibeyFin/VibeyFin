package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.downloadRemoteSubtitle
import app.vibeyfin.data.api.map
import app.vibeyfin.data.api.remoteSubtitles
import app.vibeyfin.data.api.valueOrNull
import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One subtitle a provider offers for an item. */
data class RemoteSubtitle(
    val id: String,
    val name: String,
    val provider: String?,
    val format: String?,
    val rating: Double?,
    val isHashMatch: Boolean,
)

/** What came of asking the server to add a subtitle. */
sealed interface SubtitleDownload {
    /** The item now lists the track, at this index. */
    data class Added(val streamIndex: Int) : SubtitleDownload

    /** The server took the request, but no track appeared in time: a provider refusing looks the same as a success to the client. */
    data object NotArrived : SubtitleDownload

    data class Failed(val error: ApiError) : SubtitleDownload
}

/** Searching for subtitles through the server's providers, and adding one to an item for every client. */
class SubtitleRepository(
    private val connection: ConnectionManager,
    private val library: LibraryRepository,
    profileSettings: ProfileSettings,
    offlineMode: OfflineMode,
    private val deviceInfo: DeviceInfo,
    private val pollEvery: Duration = 2.seconds,
    private val polls: Int = 15,
) {
    // A 403 means the policy changed after it was read; that profile's entry stays hidden until the app next starts.
    private val refused = MutableStateFlow(emptySet<ProfileRef>())

    /** Whether to offer the search: online, not a kids profile, and the user's own policy allows it. Being an admin doesn't count. */
    val available: Flow<Boolean> = combine(offlineMode.isOffline, profileSettings.kidsProfile, refused, connection.state) { offline, kids, refusedProfiles, state ->
        !offline && kids == false && state.profileOrNull() !in refusedProfiles
    }.distinctUntilChanged().map { it && allowedByPolicy() }

    private suspend fun allowedByPolicy(): Boolean = connection.asConnected(false) { it.api.me().valueOrNull()?.policy?.enableSubtitleManagement == true }

    /** The language to search first: the profile's subtitle language, else the device's. */
    fun startingLanguage(prefs: LanguagePrefs?, languages: List<Language>): String = prefs?.subtitleLanguage
        ?: languages.firstOrNull { it.twoLetterCode.equals(deviceInfo.language, ignoreCase = true) }?.code
        ?: deviceInfo.language

    /** What the providers offer in [language], hash matches first, then the highest rated. */
    suspend fun search(itemId: String, language: String): ApiResult<List<RemoteSubtitle>> = connection.asConnected<ApiResult<List<RemoteSubtitle>>>(ApiResult.Failure(ApiError.Unauthorized)) { connected ->
        noteRefusal(connected.api.remoteSubtitles(itemId, language)).map { results ->
            results.map { RemoteSubtitle(it.id, it.name?.takeIf { name -> name.isNotBlank() } ?: it.id, it.providerName, it.format, it.communityRating, it.isHashMatch == true) }
                .sortedWith(compareByDescending<RemoteSubtitle> { it.isHashMatch }.thenByDescending { it.rating ?: -1.0 })
        }
    }

    /** Asks the server to add [subtitleId] to the item, and waits for the track to be listed. */
    suspend fun download(itemId: String, subtitleId: String): SubtitleDownload = connection.asConnected<SubtitleDownload>(SubtitleDownload.Failed(ApiError.Unauthorized)) { connected ->
        val before = when (val read = library.item(itemId)) {
            is ApiResult.Success -> read.value.subtitleTracks
            is ApiResult.Failure -> return@asConnected SubtitleDownload.Failed(read.error)
        }
        val sent = noteRefusal(connected.api.downloadRemoteSubtitle(itemId, subtitleId))
        if (sent is ApiResult.Failure) return@asConnected SubtitleDownload.Failed(sent.error)
        repeat(polls) {
            delay(pollEvery)
            when (val read = library.item(itemId)) {
                is ApiResult.Success -> newTrack(before, read.value.subtitleTracks)?.index?.let { return@asConnected SubtitleDownload.Added(it) }
                is ApiResult.Failure -> if (read.error == ApiError.Unauthorized) return@asConnected SubtitleDownload.Failed(read.error)
            }
        }
        SubtitleDownload.NotArrived
    }

    // Compared by name, because adding a track can move the indexes of the others. A same-named pair still counts as a new one.
    private fun newTrack(before: List<StreamTrack>, after: List<StreamTrack>): StreamTrack? {
        if (after.size <= before.size) return null
        val known = before.map { it.displayTitle }.toMutableList()
        return after.filterNot { known.remove(it.displayTitle) }.lastOrNull()
    }

    private fun <T> noteRefusal(result: ApiResult<T>): ApiResult<T> {
        if ((result as? ApiResult.Failure)?.error == ApiError.Forbidden) connection.state.value.profileOrNull()?.let { profile -> refused.update { it + profile } }
        return result
    }
}
