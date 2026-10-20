package com.liamgleeson.vibeyfin.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.LibraryRepository
import com.liamgleeson.vibeyfin.domain.PlayMethod
import com.liamgleeson.vibeyfin.domain.PlaybackNegotiation
import com.liamgleeson.vibeyfin.domain.PlaybackPlan
import com.liamgleeson.vibeyfin.domain.PlaybackReporter
import com.liamgleeson.vibeyfin.domain.PlaybackRepository
import com.liamgleeson.vibeyfin.domain.PlaybackTrack
import com.liamgleeson.vibeyfin.domain.SubtitleFile
import com.liamgleeson.vibeyfin.ui.common.LoadMessages
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.common.sessionEnded
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import com.liamgleeson.vibeyfin.ui.item.episodeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One stream for the native player to open. [id] changes whenever there is a new one (the first
 * stream, a retry, the transcode fallback), so a player that sees a new [id] opens [url] afresh.
 */
data class PlaybackStream(
    val id: Int,
    val url: String,
    /** Sent with every request the player makes for [url]. */
    val headers: Map<String, String>,
    /** Where to start, in milliseconds. */
    val startPositionMs: Long,
    /**
     * Every subtitle the server sends as a file. The player loads them all with the video and shows
     * the one the state marks as selected (`PlaybackUiState.subtitleTracks`), so switching is instant.
     */
    val subtitleFiles: List<SubtitleFile> = emptyList(),
    /**
     * The file itself is playing, so the server can't change its audio: the player must pick the
     * audio track the state marks as selected (`PlaybackUiState.audioTracks`) by its position in that
     * list, which is the file's order. When false, the server already chose the audio in the stream.
     */
    val audioInPlayer: Boolean = false,
)

/**
 * Why the native player couldn't play a stream. Each player maps its own error codes to one of
 * these, so the decisions stay here and no platform error code does.
 */
enum class PlaybackFailure {
    /** The connection dropped or timed out, or the server answered with an error: the same stream is worth another try. */
    Network,

    /** The server rejected the token on the stream itself. */
    Unauthorized,

    /** The device can't play it, or the player couldn't say why. */
    Unsupported,
}

/** How long to wait before each new try at a stream that failed for want of a connection. */
internal val NETWORK_RETRY_DELAYS: List<Duration> = listOf(1.seconds, 2.seconds)

/** Playing this far past where a failure happened counts as the connection, or the token, being good again. */
private const val RECOVERED_AFTER_MS = 5_000L

/** One row in the audio or subtitle picker. */
data class TrackOption(
    /** What to pass back to `onSelectAudio` or `onSelectSubtitle`; [SUBTITLES_OFF] is "no subtitles". */
    val index: Int,
    val label: String,
    val selected: Boolean,
)

/** The index of the "Off" row in the subtitle picker. */
const val SUBTITLES_OFF = PlaybackRepository.SUBTITLES_OFF

data class PlaybackUiState(
    /** Asking the server how to play the item. */
    val loading: Boolean = true,
    val title: String = "",
    /** For an episode: the show and "S1 E3". */
    val subtitle: String? = null,
    /** Null until the server has answered, and after a failure. */
    val stream: PlaybackStream? = null,
    /** The audio tracks to pick from; empty when there is only one. */
    val audioTracks: List<TrackOption> = emptyList(),
    /** The subtitle tracks to pick from, "Off" first; empty when the file has none. */
    val subtitleTracks: List<TrackOption> = emptyList(),
    /** Why nothing is playing, with what to try. */
    val message: String? = null,
    /** Playback reached the end; the screen should close. */
    val finished: Boolean = false,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
) {
    /** Whether there is anything to choose between, so the player shows its audio and subtitles control. */
    val hasTrackChoices: Boolean get() = audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()
}

/**
 * Plays one item: asks the server how, hands the native player a [PlaybackStream], and reports to
 * the server what the player tells it. The player is native (Media3, AVPlayer); it pushes its events
 * into the `on…` functions with its position in milliseconds, and nothing here knows about it.
 *
 * When the player fails, [onFailed] says why. A lost connection ([PlaybackFailure.Network]) tries the
 * same stream again from where it was, twice, and then says so; a token the server rejects on the
 * stream ([PlaybackFailure.Unauthorized]) asks the server again once, which sends a dead token through
 * the usual 401 handling; anything else ([PlaybackFailure.Unsupported]) means the device can't play
 * what the server offered by direct play or direct stream, so the server is asked again with those
 * turned off, and the player resumes from where it was in a transcode.
 *
 * Audio and subtitles: until the user chooses, the server's own settings for them decide. A choice is
 * made in the player when it can be ([PlaybackStream.audioInPlayer] for the audio of a direct play,
 * and any subtitle the server sends as a file), and costs nothing. Otherwise (a subtitle burned into
 * the video, or the audio of a stream the server prepares) it is a new negotiation, and the player
 * opens the new stream at the position it was at.
 */
class PlaybackViewModel(
    private val itemId: String,
    private val library: LibraryRepository,
    private val playback: PlaybackRepository,
    private val reporter: PlaybackReporter,
    connection: ConnectionManager,
    private val retryDelays: List<Duration> = NETWORK_RETRY_DELAYS,
) : ViewModel() {
    private val content = MutableStateFlow(PlaybackUiState())

    val uiState: StateFlow<PlaybackUiState> = combine(content, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackUiState())

    private var lastPositionMs = 0L
    private var method: PlayMethod? = null
    private var streams = 0

    /** What the current stream plays. Null while there is none. */
    private var plan: PlaybackPlan? = null

    /** The audio and subtitle tracks selected now; a null subtitle is none. */
    private var audioNow: Int? = null
    private var subtitleNow: Int? = null

    /** What the user picked, to send with the next negotiation; null until they pick, leaving it to the server. */
    private var choice: TrackChoice? = null

    /** Set once direct play failed on this device, so a later track change doesn't try it again. */
    private var transcodeOnly = false

    /**
     * What has gone wrong since playback last got going: the tries at the same stream used up, whether
     * the server was already asked again after a rejected token, and where it first failed. Playing
     * [RECOVERED_AFTER_MS] past that point clears it (see [onPosition]); the player says it is playing the
     * moment it opens a stream, so that can't be the signal.
     */
    private var networkRetries = 0
    private var reauthorized = false
    private var failedAtMs = 0L

    /** The error on screen is a lost connection, which says nothing about what the device can play. */
    private var lostConnection = false

    private class TrackChoice(val mediaSourceId: String, val audio: Int?, val subtitle: Int)

    init {
        loadTitle()
        open(startAtMs = 0)
    }

    /** Plays the audio track [index] (a [TrackOption.index]) from where the video is now. */
    fun onSelectAudio(index: Int) {
        val current = plan ?: return
        if (index == audioNow) return
        if (current.method == PlayMethod.DirectPlay) {
            audioNow = index
            applySelection(current)
        } else {
            reopen(current, audio = index, subtitle = subtitleNow)
        }
    }

    /** Shows the subtitle track [index], or none for [SUBTITLES_OFF], from where the video is now. */
    fun onSelectSubtitle(index: Int) {
        val current = plan ?: return
        val target = index.takeIf { it != SUBTITLES_OFF }
        if (target == subtitleNow) return
        // A subtitle in the video can't be taken out again, and one that isn't sent as a file can't be shown.
        val inPlayer = !current.subtitleBurnedIn && (target == null || current.subtitleFiles.any { it.index == target })
        if (inPlayer) {
            subtitleNow = target
            applySelection(current)
        } else {
            reopen(current, audio = audioNow, subtitle = target)
        }
    }

    /** The player is playing: it started, or resumed. */
    fun onPlaying(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.playing(positionMs)
    }

    fun onPaused(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.paused(positionMs)
    }

    fun onSeeked(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.seeked(positionMs)
    }

    /** The current position, as often as the player knows it (about once a second). */
    fun onPosition(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.position(positionMs)
        if ((networkRetries > 0 || reauthorized) && positionMs - failedAtMs >= RECOVERED_AFTER_MS) {
            networkRetries = 0
            reauthorized = false
        }
    }

    /** The item played to its end. */
    fun onEnded(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.stop(positionMs)
        content.update { it.copy(finished = true) }
    }

    /** The app went to the background (the native player has paused). Reported as a stop; playing again starts anew. */
    fun onBackgrounded(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.stop(positionMs)
    }

    /** The user closed the player. */
    fun onClosed(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.stop(positionMs)
    }

    /** The player couldn't play the stream, for the reason [failure]. Ignored when no stream is open. */
    fun onFailed(failure: PlaybackFailure) {
        val failed = content.value.stream ?: return
        when (failure) {
            PlaybackFailure.Network -> retrySameStream(failed)
            PlaybackFailure.Unauthorized -> askAgainAfterRejection()
            PlaybackFailure.Unsupported -> fallBackToTranscode()
        }
    }

    fun onRetry() {
        // A transcode that was needed still is after a lost connection; after any other failure start afresh.
        if (!lostConnection) transcodeOnly = false
        lostConnection = false
        networkRetries = 0
        reauthorized = false
        open(startAtMs = lastPositionMs)
    }

    /**
     * The connection was lost: the player is given the same stream again from where it was, after a short
     * wait with the spinner showing, and the server hears the video is paused meanwhile. When the tries
     * are used up it is an error with "Try again", not a transcode, which would need the same connection.
     */
    private fun retrySameStream(failed: PlaybackStream) {
        val wait = retryDelays.getOrNull(networkRetries) ?: return giveUp(LoadMessages.PLAYBACK_NETWORK, lostConnection = true)
        if (networkRetries == 0 && !reauthorized) failedAtMs = lastPositionMs
        networkRetries++
        reporter.paused(lastPositionMs)
        content.update { it.copy(loading = true, stream = null, message = null) }
        viewModelScope.launch {
            delay(wait)
            content.update {
                it.copy(loading = false, stream = failed.copy(id = ++streams, startPositionMs = lastPositionMs))
            }
        }
    }

    /**
     * The server rejected the token on the stream, which the player fetches itself and so bypasses the
     * usual 401 handling. Asking again goes through it: a dead token ends the session there, and one
     * that is only stale gets a fresh stream address. Only once, so a stream that keeps being refused
     * ends as an error.
     */
    private fun askAgainAfterRejection() {
        if (reauthorized) return giveUp(LoadMessages.PLAYBACK_FAILED)
        if (networkRetries == 0) failedAtMs = lastPositionMs
        reauthorized = true
        reporter.stop(lastPositionMs, failed = true)
        open(startAtMs = lastPositionMs)
    }

    /** The device can't play it: once more as a transcode, then an error. */
    private fun fallBackToTranscode() {
        if (method == PlayMethod.Transcode) return giveUp(LoadMessages.PLAYBACK_FAILED)
        reporter.stop(lastPositionMs, failed = true)
        transcodeOnly = true
        open(startAtMs = lastPositionMs)
    }

    private fun giveUp(message: String, lostConnection: Boolean = false) {
        reporter.stop(lastPositionMs, failed = true)
        method = null
        this.lostConnection = lostConnection
        content.update { it.copy(stream = null, loading = false, message = message) }
    }

    override fun onCleared() {
        // The player has usually said so already; this covers a screen that left without it. Whatever
        // is queued, `Stopped` included, is still sent after the screen's scope is gone.
        reporter.stop(lastPositionMs)
        reporter.close()
    }

    private fun loadTitle() {
        viewModelScope.launch {
            val item = (library.item(itemId) as? ApiResult.Success)?.value ?: return@launch
            content.update {
                it.copy(
                    title = item.title,
                    subtitle = listOfNotNull(item.seriesName, item.episodeLabel()).joinToString("  ·  ").ifEmpty { null },
                )
            }
        }
    }

    /** The player does the switching: only the picker and the reports change, the stream stays. */
    private fun applySelection(current: PlaybackPlan) {
        remember(current)
        val updated = current.copy(audioStreamIndex = audioNow, subtitleStreamIndex = subtitleNow)
        plan = updated
        reporter.begin(updated)
        content.update {
            it.copy(
                audioTracks = it.audioTracks.map { track -> track.copy(selected = track.index == audioNow) },
                subtitleTracks = it.subtitleTracks.map { track -> track.copy(selected = track.index == (subtitleNow ?: SUBTITLES_OFF)) },
            )
        }
    }

    /** The current stream ends where it is and the server is asked again with the new choice. */
    private fun reopen(current: PlaybackPlan, audio: Int?, subtitle: Int?) {
        if (content.value.stream == null) return
        audioNow = audio
        subtitleNow = subtitle
        remember(current)
        reporter.stop(lastPositionMs)
        open(startAtMs = lastPositionMs)
    }

    /** Keeps what is selected, so the next negotiation (a retry, the transcode fallback) asks for the same. */
    private fun remember(current: PlaybackPlan) {
        choice = TrackChoice(current.mediaSourceId, audioNow, subtitleNow ?: SUBTITLES_OFF)
    }

    private fun open(startAtMs: Long) {
        content.update { it.copy(loading = true, stream = null, message = null) }
        viewModelScope.launch {
            val picked = choice
            val negotiation = playback.negotiate(itemId, transcodeOnly, picked?.mediaSourceId, picked?.audio, picked?.subtitle)
            when (val result = negotiation) {
                is PlaybackNegotiation.Ready -> {
                    val ready = result.plan
                    plan = ready
                    method = ready.method
                    audioNow = ready.audioStreamIndex
                    subtitleNow = ready.subtitleStreamIndex
                    reporter.begin(ready)
                    content.update {
                        it.copy(
                            loading = false,
                            stream = PlaybackStream(
                                id = ++streams,
                                url = ready.url,
                                headers = ready.headers,
                                startPositionMs = startAtMs,
                                subtitleFiles = ready.subtitleFiles,
                                audioInPlayer = ready.method == PlayMethod.DirectPlay,
                            ),
                            audioTracks = ready.audioTracks.toAudioOptions(),
                            subtitleTracks = ready.subtitleTracks.toSubtitleOptions(),
                        )
                    }
                }
                PlaybackNegotiation.NoCompatibleStream ->
                    content.update { it.copy(loading = false, message = LoadMessages.NO_COMPATIBLE_STREAM) }
                is PlaybackNegotiation.Failed ->
                    content.update { it.copy(loading = false, message = LoadMessages.forError(result.error)) }
            }
        }
    }
}

/** Picking between audio tracks needs at least two. */
private fun List<PlaybackTrack>.toAudioOptions(): List<TrackOption> =
    if (size < 2) emptyList() else map { TrackOption(it.index, it.name ?: Strings.track(it.index), it.selected) }

/** "Off" first, selected when no subtitle is. Empty when the file has no subtitles. */
private fun List<PlaybackTrack>.toSubtitleOptions(): List<TrackOption> =
    if (isEmpty()) {
        emptyList()
    } else {
        listOf(TrackOption(SUBTITLES_OFF, Strings.subtitlesOff, selected = none { it.selected })) +
            map { TrackOption(it.index, it.name ?: Strings.track(it.index), it.selected) }
    }
