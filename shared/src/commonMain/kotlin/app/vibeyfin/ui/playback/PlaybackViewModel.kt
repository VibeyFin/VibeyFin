package app.vibeyfin.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.BingeCounter
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.DynamicRange
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.LiveDiagnostics
import app.vibeyfin.domain.NowPlaying
import app.vibeyfin.domain.NowPlayingState
import app.vibeyfin.domain.PlaybackDiagnostics
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.toDiagnostics
import app.vibeyfin.domain.withLive
import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.PlaybackNegotiation
import app.vibeyfin.domain.PlaybackPlan
import app.vibeyfin.domain.PlaybackReporter
import app.vibeyfin.domain.PlaybackRepository
import app.vibeyfin.domain.MediaSegment
import app.vibeyfin.domain.PlaybackTrack
import app.vibeyfin.domain.SegmentType
import app.vibeyfin.domain.SubtitleFile
import app.vibeyfin.domain.TrickplayTrack
import app.vibeyfin.domain.WatchRepository
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.item.badge
import app.vibeyfin.ui.item.episodeLabel
import kotlinx.coroutines.Job
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
    /**
     * The picture's range as the server describes the file. A player that can draw HDR only through its
     * own surface (Android's `SurfaceView`) chooses that for anything but [DynamicRange.Sdr].
     */
    val dynamicRange: DynamicRange = DynamicRange.Sdr,
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

/** The card offering the next episode in the last seconds of this one. */
data class UpNext(
    val id: String,
    /** "S2 E6"; null when the server gives no numbers. */
    val label: String?,
    /** The episode's own title. */
    val title: String,
    /** A 16:9 picture, or null when the server has none. */
    val imageUrl: String?,
    /** The BlurHash of [imageUrl]'s picture, drawn while it loads; null when the server sent none. */
    val imageBlurHash: String?,
    /** Whole seconds until it starts on its own. */
    val secondsLeft: Int,
)

/** The next-episode card appears this long before the end of an episode (the last 30 s when there are no credits to find). */
private const val UP_NEXT_WINDOW_MS = 30_000L

/** How long the card counts down before the next episode starts by itself. */
private const val UP_NEXT_SECONDS = 10

/** A video shorter than this never offers the card early: it would start the next one almost at once. */
private const val MIN_AUTO_ADVANCE_RUNTIME_MS = 180_000L

/** Seeking back this far outside the window takes the card away again. */
private const val UP_NEXT_SEEK_SLACK_MS = 5_000L

/** One row in the audio or subtitle picker. */
data class TrackOption(
    /** What to pass back to `onSelectAudio` or `onSelectSubtitle`; [SUBTITLES_OFF] is "no subtitles". */
    val index: Int,
    val label: String,
    val selected: Boolean,
)

/** The index of the "Off" row in the subtitle picker. */
const val SUBTITLES_OFF = PlaybackRepository.SUBTITLES_OFF

/** The button to jump over the part of the video that is playing: [label] says what, [toMs] is where it ends. */
data class SkipOffer(val label: String, val toMs: Long)

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
    /** The next episode is about to start; null when there is none or nobody is being offered it. */
    val upNext: UpNext? = null,
    /** Playback reached the end and nothing follows; the screen should close. */
    val finished: Boolean = false,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
    /** What the stats overlay shows for the current stream; null while there is none. */
    val diagnostics: PlaybackDiagnostics? = null,
    /** The signed-in profile wants the stats overlay. */
    val showDiagnostics: Boolean = false,
    /** Seconds of video the player should load ahead; 0 is Auto, the platform player's own choice. */
    val bufferAheadSeconds: Int = ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
    /** Subtitles use the platform's caption style, so the four values below are ignored. */
    val captionsFollowSystem: Boolean = true,
    /** Subtitle size: 0 small, 1 medium, 2 large, 3 extra large. */
    val subtitleSize: Int = 1,
    /** Subtitle text colour: 0 white, 1 yellow, 2 light blue. */
    val subtitleTextColor: Int = 0,
    /** Subtitle background: 0 none, 1 shadow, 2 box. */
    val subtitleBackground: Int = 1,
    /** Set while an intro, recap or the like is playing; stays for the whole segment. */
    val skipOffer: SkipOffer? = null,
    /** Where the player should seek to now (set by `onSkip`); it clears this with `onSeekHandled`. */
    val pendingSeekMs: Long? = null,
    /** How far the skip-back button jumps, from the profile. */
    val skipBackSeconds: Int = ProfileSettings.DEFAULT_SKIP_BACK_SECONDS,
    /** How far the skip-forward button jumps, from the profile. */
    val skipForwardSeconds: Int = ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS,
    /** The scrub-preview thumbnails of the playing media source; null when the server made none or can't be asked. */
    val trickplay: TrickplayTrack? = null,
    /** The speed the user chose, one of [PLAYBACK_SPEEDS]; the player plays at it. */
    val speed: Float = 1f,
    /** The one badge for the picture ("4K", "HDR10"); null when there is none to show. */
    val badge: String? = null,
    /** The player is in a picture-in-picture window; the screen is gone but the session goes on. */
    val inPictureInPicture: Boolean = false,
    /** Several episodes in a row started by themselves: the player is paused and asks whether to go on. */
    val stillWatching: Boolean = false,
) {
    /** The speeds to pick from. */
    val speeds: List<Float> get() = PLAYBACK_SPEEDS

    /** Whether there is anything to choose between, so the player shows its audio and subtitles control. */
    val hasTrackChoices: Boolean get() = audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()
}

/** The speeds the player offers. */
val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Resuming starts this long before where the user stopped, so the scene they left is in view again. */
const val RESUME_REWIND_MS = 5_000L

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
 *
 * It starts where the server says the user stopped, [RESUME_REWIND_MS] earlier so they pick up the
 * thread, unless [startOver] is set or the server says the item was finished. The item is read first for
 * that: if that read fails, playback doesn't start (with "Try again"), because starting at 0 would later
 * report a small position and overwrite the real one.
 *
 * Auto-advance: for an episode, the next one is looked up in the background. In the last 30 s of an
 * episode (always that window; an Outro segment doesn't move it) [PlaybackUiState.upNext] counts down
 * 10 s and then plays the next episode in this same ViewModel, so neither native player is rebuilt: the
 * finished one is reported stopped at its end, so the server marks it watched, and the stream changes.
 * [onWatchCredits] dismisses the card for this episode; [onPlayNow] doesn't wait. A video under three
 * minutes only counts down once it has ended. [autoAdvance] is on by default (Phase 7 makes it a setting).
 */
class PlaybackViewModel(
    private var itemId: String,
    private val library: LibraryRepository,
    private val playback: PlaybackRepository,
    private val reporter: PlaybackReporter,
    connection: ConnectionManager,
    private val watch: WatchRepository,
    private val profileSettings: ProfileSettings,
    private val nowPlaying: NowPlaying,
    private val binge: BingeCounter,
    private val retryDelays: List<Duration> = NETWORK_RETRY_DELAYS,
    private val startOver: Boolean = false,
    private val autoAdvance: Boolean = true,
    /** How long one second of the countdown takes; tests make it short. */
    private val countdownStep: Duration = 1.seconds,
) : ViewModel() {
    private val content = MutableStateFlow(PlaybackUiState())

    /** The four subtitle style settings, in the order of the [PlaybackUiState] fields. */
    private val subtitleStyle = combine(
        profileSettings.captionsFollowSystem,
        profileSettings.subtitleSize,
        profileSettings.subtitleTextColor,
        profileSettings.subtitleBackground,
    ) { follow, size, color, background -> listOf(follow, size, color, background) }

    val uiState: StateFlow<PlaybackUiState> = combine(
        content,
        connection.sessionEnded(),
        profileSettings.showPlaybackStats,
        profileSettings.bufferAheadSeconds,
        subtitleStyle,
    ) { content, exit, showStats, bufferAhead, subtitle ->
        content.copy(
            exit = exit,
            showDiagnostics = showStats ?: false,
            bufferAheadSeconds = bufferAhead ?: ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
            captionsFollowSystem = subtitle[0] as Boolean? ?: true,
            subtitleSize = subtitle[1] as Int? ?: 1,
            subtitleTextColor = subtitle[2] as Int? ?: 0,
            subtitleBackground = subtitle[3] as Int? ?: 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackUiState())

    private var lastPositionMs = 0L

    /** The player is playing (not paused), as last told; what [nowPlaying] shows. */
    private var playingNow = false

    /** The item has been read, so where to start is known and a retry doesn't need to ask again. */
    private var started = false

    /** What is playing, as the server describes it. */
    private var item: MediaItem? = null

    /** The episode after [item], once looked up; null while unknown, and for anything that has no next one. */
    private var next: MediaItem? = null

    /** The user chose "Watch credits": no card for the rest of this episode. */
    private var upNextDismissed = false

    /** The item played to its end, so a dismissed card now means leaving. */
    private var ended = false

    private var inPictureInPicture = false

    /** Episodes in a row before the player asks "Are you still watching?"; kept up to date from the profile's settings. */
    private var bingeThreshold = ProfileSettings.DEFAULT_BINGE_THRESHOLD

    private var countdown: Job? = null
    private var method: PlayMethod? = null
    private var streams = 0

    /** The item's marked segments; loaded once, by the first [open]. */
    private var segments: List<MediaSegment> = emptyList()
    private var segmentsRequested = false

    /** The profile jumps over intros and recaps by itself. */
    private var autoSkip = false

    /** Segments (start, end) already jumped over by auto-skip; each is skipped once, so going back stays possible. */
    private val autoSkipped = mutableSetOf<Pair<Long, Long>>()

    /** The item and media source the trickplay was asked for, so a re-opened stream of the same one doesn't ask again. */
    private var trickplayFor: Pair<String, String>? = null

    /** What the current stream plays. Null while there is none. */
    private var plan: PlaybackPlan? = null

    /** What the player last measured for this stream; cleared with a new one. */
    private var liveDiagnostics: LiveDiagnostics? = null

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
        viewModelScope.launch {
            profileSettings.bingeThreshold.collect { bingeThreshold = it ?: ProfileSettings.DEFAULT_BINGE_THRESHOLD }
        }
        viewModelScope.launch {
            profileSettings.skipBackSeconds.collect { seconds ->
                content.update { it.copy(skipBackSeconds = seconds ?: ProfileSettings.DEFAULT_SKIP_BACK_SECONDS) }
            }
        }
        viewModelScope.launch {
            profileSettings.skipForwardSeconds.collect { seconds ->
                content.update { it.copy(skipForwardSeconds = seconds ?: ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS) }
            }
        }
        viewModelScope.launch {
            profileSettings.autoSkipSegments.collect { autoSkip = it ?: false }
        }
        start()
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

    /** Plays at [speed], if it is one of [PLAYBACK_SPEEDS]. Position reports stay in media time. */
    fun onSelectSpeed(speed: Float) {
        if (speed !in PLAYBACK_SPEEDS) return
        binge.interacted()
        content.update { if (it.speed == speed) it else it.copy(speed = speed) }
    }

    /** What the player measures about the stream, as often as it likes; values it can't give are null. */
    fun onDiagnostics(live: LiveDiagnostics) {
        liveDiagnostics = live
        refreshDiagnostics()
    }

    /** The player is playing: it started, or resumed. */
    fun onPlaying(positionMs: Long) {
        lastPositionMs = positionMs
        playingNow = true
        reporter.playing(positionMs)
        publishNowPlaying()
    }

    fun onPaused(positionMs: Long) {
        lastPositionMs = positionMs
        playingNow = false
        reporter.paused(positionMs)
        publishNowPlaying()
    }

    fun onSeeked(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.seeked(positionMs)
        checkUpNext(positionMs)
        refreshSkipOffer()
    }

    /** The user chose the skip button: the player is asked to seek to the end of the segment. */
    fun onSkip() {
        val offer = content.value.skipOffer ?: return
        binge.interacted()
        content.update { it.copy(pendingSeekMs = offer.toMs) }
    }

    /** Seeks back by the profile's skip length from [positionMs], not below the start. */
    fun onSkipBack(positionMs: Long) {
        seekTo((basePosition(positionMs) - content.value.skipBackSeconds * 1000L).coerceAtLeast(0L))
    }

    /** Seeks forward by the profile's skip length from [positionMs], not past [durationMs]; does nothing while the duration is unknown. */
    fun onSkipForward(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        seekTo((basePosition(positionMs) + content.value.skipForwardSeconds * 1000L).coerceAtMost(durationMs))
    }

    /**
     * A seek still waiting for the player is where the video is going, so a second tap builds on it.
     * Stacking works on [PlaybackUiState.pendingSeekMs] while it is not cleared. On a device the position
     * passed in is already right after a seek, because both native players update their own position at once.
     */
    private fun basePosition(positionMs: Long) = content.value.pendingSeekMs ?: positionMs

    /** Asks the player to seek. A second skip before the player has done it stacks through [basePosition]. */
    private fun seekTo(targetMs: Long) {
        binge.interacted()
        lastPositionMs = targetMs
        content.update { it.copy(pendingSeekMs = targetMs) }
    }

    /** The player has done the seek it was asked for. */
    fun onSeekHandled() {
        content.update { if (it.pendingSeekMs == null) it else it.copy(pendingSeekMs = null) }
    }

    /** The current position, as often as the player knows it (about once a second). */
    fun onPosition(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.position(positionMs)
        publishNowPlaying()
        checkUpNext(positionMs)
        refreshSkipOffer()
        if ((networkRetries > 0 || reauthorized) && positionMs - failedAtMs >= RECOVERED_AFTER_MS) {
            networkRetries = 0
            reauthorized = false
        }
    }

    /**
     * The item played to its end. If an episode follows (and the user didn't ask to watch the credits),
     * the next one starts now, or after the countdown if it hasn't run yet; otherwise the screen closes.
     */
    fun onEnded(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.stop(positionMs)
        nowPlaying.clear()
        ended = true
        val upcoming = next
        if (autoAdvance && upcoming != null && !upNextDismissed) {
            // Already counting down: the video ended under it, so there is nothing left to wait for.
            if (countdown != null) playNext(byItself = true) else startCountdown(upcoming)
            return
        }
        content.update { it.copy(finished = true) }
    }

    /**
     * The app went to the background (the native player has paused). Reported as a stop; playing again
     * starts anew, and a card that was counting goes with it (it returns if the end is still near).
     */
    fun onBackgrounded(positionMs: Long) {
        // A picture-in-picture window keeps playing; it ends the session when it is closed.
        if (inPictureInPicture) return
        lastPositionMs = positionMs
        reporter.stop(positionMs)
        nowPlaying.clear()
        cancelCountdown()
        if (ended) content.update { it.copy(finished = true) }
    }

    /** The user closed the player. */
    fun onClosed(positionMs: Long) {
        lastPositionMs = positionMs
        reporter.stop(positionMs)
        nowPlaying.clear()
        binge.reset()
        cancelCountdown()
    }

    /** The viewer touched or pressed something: whatever was counted towards "Are you still watching?" starts over. */
    fun onUserInteraction() = binge.interacted()

    /**
     * "Keep watching" on the prompt: the count starts over, the player carries on, and the card comes back
     * in its window. If the episode already ended (the prompt came at its end), the card counts down to
     * the next one now, since there is nothing left to play.
     */
    fun onContinueWatching() {
        binge.reset()
        content.update { it.copy(stillWatching = false) }
        val upcoming = next
        if (ended && autoAdvance && upcoming != null && !upNextDismissed) {
            if (countdown == null) startCountdown(upcoming)
            return
        }
        checkUpNext(lastPositionMs)
    }

    /** "Stop" on the prompt: the same as closing the player. */
    fun onStopWatching() {
        onClosed(lastPositionMs)
        content.update { it.copy(stillWatching = false, finished = true) }
    }

    /** The screen became a picture-in-picture window: playback and reporting carry on, nothing is stopped. */
    fun onEnteredPictureInPicture(positionMs: Long) {
        lastPositionMs = positionMs
        inPictureInPicture = true
        content.update { it.copy(inPictureInPicture = true) }
    }

    /**
     * The picture-in-picture window went away. [closed]: the user dismissed it, which ends the session
     * like closing the player. Otherwise it was expanded back to the full player and nothing is reported.
     */
    fun onLeftPictureInPicture(positionMs: Long, closed: Boolean) {
        inPictureInPicture = false
        content.update { it.copy(inPictureInPicture = false) }
        if (closed) onClosed(positionMs)
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
        if (started) open(startAtMs = lastPositionMs) else start()
    }

    /**
     * The connection was lost: the player is given the same stream again from where it was, after a short
     * wait with the spinner showing, and the server hears the video is paused meanwhile. When the tries
     * are used up it is an error with "Try again", not a transcode, which would need the same connection.
     */
    private fun retrySameStream(failed: PlaybackStream) {
        val wait = retryDelays.getOrNull(networkRetries) ?: return giveUp(LoadMessages.PLAYBACK_NETWORK, lostConnection = true)
        if (networkRetries == 0 && !reauthorized) failedAtMs = lastPositionMs
        cancelCountdown()
        networkRetries++
        reporter.paused(lastPositionMs)
        content.update { it.copy(loading = true, stream = null, message = null, pendingSeekMs = null) }
        viewModelScope.launch {
            delay(wait)
            content.update {
                it.copy(loading = false, pendingSeekMs = null, stream = failed.copy(id = ++streams, startPositionMs = lastPositionMs))
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
        cancelCountdown()
        reporter.stop(lastPositionMs, failed = true)
        method = null
        this.lostConnection = lostConnection
        content.update { it.copy(stream = null, loading = false, message = message, pendingSeekMs = null) }
    }

    override fun onCleared() {
        // The player has usually said so already; this covers a screen that left without it. Whatever
        // is queued, `Stopped` included, is still sent after the screen's scope is gone.
        reporter.stop(lastPositionMs)
        reporter.close()
        nowPlaying.clear(itemId)
    }

    /** Reads the item for its title and where the user stopped, then opens it there. */
    private fun start() {
        content.update { it.copy(loading = true, stream = null, message = null) }
        viewModelScope.launch {
            when (val result = library.item(itemId)) {
                is ApiResult.Success -> begin(result.value, honourStartOver = true)
                is ApiResult.Failure -> content.update { it.copy(loading = false, message = LoadMessages.forError(result.error)) }
            }
        }
    }

    /** Starts [found] where the user left off (or at the beginning), and looks up what comes after it. */
    private fun begin(found: MediaItem, honourStartOver: Boolean) {
        val startAtMs = if ((honourStartOver && startOver) || found.played) 0L else (found.resumePositionMs - RESUME_REWIND_MS).coerceAtLeast(0)
        item = found
        started = true
        lastPositionMs = startAtMs
        content.update {
            it.copy(
                title = found.title,
                subtitle = Strings.metaLine(found.seriesName, found.episodeLabel()),
            )
        }
        open(startAtMs)
        lookUpNext(found)
    }

    private fun lookUpNext(current: MediaItem) {
        if (!autoAdvance || current.kind != ItemKind.Episode || current.seriesId == null) return
        viewModelScope.launch {
            val found = (watch.nextEpisode(current) as? ApiResult.Success)?.value ?: return@launch
            // Only if the user is still on the episode this was asked for.
            if (item?.id == current.id) next = found
        }
    }

    /**
     * Shows the card when the end is near enough ([UP_NEXT_WINDOW_MS]) and it is worth it, or takes it
     * away again after a seek back out of the window. Called with every new position.
     */
    private fun checkUpNext(positionMs: Long) {
        val upcoming = next ?: return
        val runtime = item?.runtimeMs ?: return
        if (!autoAdvance || upNextDismissed || ended || content.value.stillWatching) return
        val remaining = runtime - positionMs
        if (countdown == null) {
            if (runtime >= MIN_AUTO_ADVANCE_RUNTIME_MS && remaining <= UP_NEXT_WINDOW_MS) startCountdown(upcoming)
        } else if (remaining > UP_NEXT_WINDOW_MS + UP_NEXT_SEEK_SLACK_MS) {
            cancelCountdown()
        }
    }

    private fun startCountdown(upcoming: MediaItem) {
        // Enough episodes in a row on their own: ask instead of counting down to another one.
        if (binge.shouldAsk(bingeThreshold)) {
            content.update { it.copy(stillWatching = true, upNext = null) }
            return
        }
        countdown = viewModelScope.launch {
            for (left in UP_NEXT_SECONDS downTo 1) {
                // The card and "Skip credits" sit in the same corner, so the card takes it away in the same update.
                content.update { it.copy(upNext = upcoming.toUpNext(left), skipOffer = skipOfferNow(cardShowing = true)) }
                delay(countdownStep)
            }
            countdown = null
            playNext(byItself = true)
        }
    }

    private fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
        content.update { it.copy(upNext = null, skipOffer = skipOfferNow(cardShowing = false)) }
    }

    /** "Play now" on the card: the next episode starts without waiting for the countdown. */
    fun onPlayNow() {
        if (next == null) return
        binge.interacted()
        playNext(byItself = false)
    }

    /** Moves on to [next]. [byItself]: nobody asked for it, so it counts towards "Are you still watching?". */
    private fun playNext(byItself: Boolean) {
        if (next == null) return
        countdown?.cancel()
        countdown = null
        if (byItself) binge.autoAdvanced()
        advance()
    }

    /** "Watch credits" on the card: it goes, and this episode plays out. If it already ended, the screen closes. */
    fun onWatchCredits() {
        binge.interacted()
        upNextDismissed = true
        cancelCountdown()
        if (ended) content.update { it.copy(finished = true) }
    }

    /**
     * Plays [next] in place of the episode that is ending. The finished one is reported stopped at its
     * end, whatever the countdown cut short, so the server counts it as watched.
     */
    private fun advance() {
        val target = next ?: return
        reporter.stop(item?.runtimeMs ?: lastPositionMs)
        itemId = target.id
        next = null
        item = null
        started = false
        ended = false
        upNextDismissed = false
        plan = null
        method = null
        audioNow = null
        subtitleNow = null
        choice = null
        transcodeOnly = false
        networkRetries = 0
        reauthorized = false
        lostConnection = false
        // Segments and trickplay belong to the item, so the next episode asks for its own.
        segments = emptyList()
        autoSkipped.clear()
        segmentsRequested = false
        trickplayFor = null
        content.update {
            it.copy(loading = true, stream = null, upNext = null, skipOffer = null, trickplay = null, speed = 1f, badge = null, stillWatching = false, audioTracks = emptyList(), subtitleTracks = emptyList(), message = null, finished = false)
        }
        begin(target, honourStartOver = false)
    }

    private fun MediaItem.toUpNext(secondsLeft: Int) =
        UpNext(
            id = id,
            label = episodeLabel(),
            title = title,
            imageUrl = landscapeUrl,
            imageBlurHash = landscapeBlurHash,
            secondsLeft = secondsLeft,
        )

    /** The player does the switching: only the picker and the reports change, the stream stays. */
    private fun applySelection(current: PlaybackPlan) {
        remember(current)
        val updated = current.copy(audioStreamIndex = audioNow, subtitleStreamIndex = subtitleNow)
        plan = updated
        reporter.begin(updated)
        refreshDiagnostics()
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

    /** The plan's diagnostics with whatever the player measured on top. */
    private fun publishNowPlaying() {
        val shown = item ?: return
        val state = content.value
        nowPlaying.update(NowPlayingState(shown.id, state.title, state.subtitle, playingNow, lastPositionMs))
    }

    private fun refreshDiagnostics() {
        val current = plan ?: return
        val base = current.toDiagnostics(audioNow)
        val merged = liveDiagnostics?.let(base::withLive) ?: base
        content.update { it.copy(diagnostics = merged) }
    }

    /** Keeps what is selected, so the next negotiation (a retry, the transcode fallback) asks for the same. */
    private fun remember(current: PlaybackPlan) {
        choice = TrackChoice(current.mediaSourceId, audioNow, subtitleNow ?: SUBTITLES_OFF)
    }

    /** Off the critical path: a slow or failed call only means there is nothing to skip. Once per item. */
    private fun loadSegments() {
        if (segmentsRequested) return
        segmentsRequested = true
        viewModelScope.launch {
            segments = playback.segments(itemId)
            // While the stream is still being set up, Ready does this once it has cleared the pending seek.
            if (content.value.stream != null) refreshSkipOffer()
        }
    }

    /** Off the critical path, like [loadSegments]: a slow or failed call only means no scrub preview. Once per item and media source. */
    private fun loadTrickplay(mediaSourceId: String) {
        val key = itemId to mediaSourceId
        if (trickplayFor == key) return
        trickplayFor = key
        content.update { if (it.trickplay == null) it else it.copy(trickplay = null) }
        viewModelScope.launch {
            val track = playback.trickplay(key.first, mediaSourceId)
            // Only if the user is still on what this was asked for.
            if (trickplayFor == key) content.update { it.copy(trickplay = track) }
        }
    }

    private fun refreshSkipOffer() {
        // Decided outside the update, which may run twice: auto-skip marks the segment and moves the position.
        val skip = if (autoSkip) autoSkipTarget() else null
        if (skip != null) lastPositionMs = skip
        content.update {
            val offer = skipOfferNow(cardShowing = it.upNext != null)
            val seek = skip ?: it.pendingSeekMs
            if (it.skipOffer == offer && it.pendingSeekMs == seek) it else it.copy(skipOffer = offer, pendingSeekMs = seek)
        }
    }

    /** Where to jump now: the end of an intro or recap that is playing and not skipped before; never credits. Marks it skipped. */
    private fun autoSkipTarget(): Long? {
        val playing = segments.firstOrNull {
            (it.type == SegmentType.Intro || it.type == SegmentType.Recap) &&
                lastPositionMs >= it.startMs && lastPositionMs < it.endMs && (it.startMs to it.endMs) !in autoSkipped
        } ?: return null
        autoSkipped += playing.startMs to playing.endMs
        return playing.endMs
    }

    /**
     * The offer for [lastPositionMs]: the skippable segment playing now, the one that ends last if several.
     * No "Skip credits" while the up-next card shows ([cardShowing]): the card already offers what comes after them.
     */
    private fun skipOfferNow(cardShowing: Boolean): SkipOffer? {
        val playing = segments
            .filter { it.type != SegmentType.Unknown && lastPositionMs >= it.startMs && lastPositionMs < it.endMs }
            .filter { !(cardShowing && it.type == SegmentType.Outro) }
            .maxByOrNull { it.endMs }
        return playing?.let { SkipOffer(Strings.skip(it.type), it.endMs) }
    }

    private fun open(startAtMs: Long) {
        // A stream that restarts (a retry, a track change) is not the end of the episode.
        cancelCountdown()
        content.update { it.copy(loading = true, stream = null, message = null, pendingSeekMs = null) }
        loadSegments()
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
                    liveDiagnostics = null
                    loadTrickplay(ready.mediaSourceId)
                    content.update {
                        it.copy(
                            loading = false,
                            pendingSeekMs = null,
                            stream = PlaybackStream(
                                id = ++streams,
                                url = ready.url,
                                headers = ready.headers,
                                startPositionMs = startAtMs,
                                subtitleFiles = ready.subtitleFiles,
                                audioInPlayer = ready.method == PlayMethod.DirectPlay,
                                dynamicRange = ready.videoFormat?.dynamicRange ?: DynamicRange.Sdr,
                            ),
                            diagnostics = ready.toDiagnostics(audioNow),
                            badge = ready.videoFormat?.badge(),
                            audioTracks = ready.audioTracks.toAudioOptions(),
                            subtitleTracks = ready.subtitleTracks.toSubtitleOptions(),
                        )
                    }
                    // Segments that came in before the stream was ready: resuming inside an intro auto-skips it now.
                    refreshSkipOffer()
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
