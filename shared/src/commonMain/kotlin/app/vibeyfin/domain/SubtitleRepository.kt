package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.UserDto
import app.vibeyfin.data.api.downloadRemoteSubtitle
import app.vibeyfin.data.api.isRetryable
import app.vibeyfin.data.api.map
import app.vibeyfin.data.api.remoteSubtitles
import app.vibeyfin.data.api.valueOrNull
import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
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

private val noiseWords = setOf("srt", "sub", "ass", "ssa", "vtt", "subrip", "external", "default", "forced", "sdh", "cc")

private fun String?.words(): Set<String> = orEmpty().lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it !in noiseWords }.toSet()

/**
 * Whether [track]'s title seems to come from this result: every word of the result's name is in it. The server keeps no link
 * between a result and the track it became, so this only works where the saved file kept the release name.
 */
fun RemoteSubtitle.namedBy(track: StreamTrack): Boolean {
    val mine = name.words()
    val theirs = track.title.words() + track.displayTitle.words()

    return mine.size >= 2 && theirs.containsAll(mine)
}

/** What came of asking the server to add a subtitle. */
sealed interface SubtitleDownload {
    /** The item now lists the track, at this index. */
    data class Added(val streamIndex: Int) : SubtitleDownload

    /** The server took the request, but no track appeared in time: a provider refusing looks the same as a success to the client. */
    data object NotArrived : SubtitleDownload

    data class Failed(val error: ApiError) : SubtitleDownload
}

/** Searching for subtitles through the server's providers, and adding one to an item for every client. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleRepository(
    private val connection: ConnectionManager,
    private val library: LibraryRepository,
    profileSettings: ProfileSettings,
    offlineMode: OfflineMode,
    private val deviceInfo: DeviceInfo,
    private val preferences: DevicePreferences,
    private val pollEvery: Duration = 2.seconds,
    private val polls: Int = 15,
    private val policyRecheckEvery: Duration = 5.seconds,
    private val policyRechecks: Int = 36,
) {
    // A 403 means the policy changed after it was read; that profile's entry stays hidden until the app next starts.
    private val refused = MutableStateFlow(emptySet<ProfileRef>())

    // The server doesn't say which provider result a track came from, so only what this device added is remembered.
    suspend fun addedTo(itemId: String): Set<String> {
        val serverId = connection.state.value.profileOrNull()?.serverId ?: return emptySet()

        return preferences.addedSubtitles(serverId, itemId)
    }

    /** Whether to offer the search: online, not a kids profile, and the user's own policy allows it. Being an admin doesn't count. */
    val available: Flow<Boolean> = combine(offlineMode.isOffline, profileSettings.kidsProfile, refused, connection.state) { offline, kids, refusedProfiles, state ->
        !offline && kids == false && state.profileOrNull() !in refusedProfiles
    }.distinctUntilChanged().mapLatest { it && allowedByPolicy() }

    private suspend fun allowedByPolicy(): Boolean {
        var read = readUser()
        var left = policyRechecks

        while (read.isRetryable() && left-- > 0) {
            delay(policyRecheckEvery)
            read = readUser()
        }
        return read.valueOrNull()?.policy?.enableSubtitleManagement == true
    }

    private suspend fun readUser(): ApiResult<UserDto> = connection.asConnected<ApiResult<UserDto>>(ApiResult.Failure(ApiError.Unauthorized)) { it.api.me() }

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

    /** The subtitle files already next to the item; empty when the item can't be read. */
    suspend fun externalTracks(itemId: String): List<StreamTrack> = (library.item(itemId) as? ApiResult.Success)?.value?.subtitleTracks.orEmpty().filter { it.external }

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
                is ApiResult.Success -> newTrack(before, read.value.subtitleTracks)?.index?.let {
                    connection.state.value.profileOrNull()?.serverId?.let { serverId -> preferences.setSubtitleAdded(serverId, itemId, subtitleId) }
                    return@asConnected SubtitleDownload.Added(it)
                }

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
