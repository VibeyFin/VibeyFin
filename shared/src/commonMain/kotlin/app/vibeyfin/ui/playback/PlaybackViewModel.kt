package app.vibeyfin.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.domain.BingeCounter
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.DynamicRange
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.LiveDiagnostics
import app.vibeyfin.domain.LocalPlayable
import app.vibeyfin.domain.LocalPlaybackReporter
import app.vibeyfin.domain.MediaSegment
import app.vibeyfin.domain.OfflineLibrary
import app.vibeyfin.domain.OfflineMode
import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.PlayReporter
import app.vibeyfin.domain.PlaybackDiagnostics
import app.vibeyfin.domain.PlaybackEngineChoice
import app.vibeyfin.domain.PlaybackNegotiation
import app.vibeyfin.domain.PlaybackPlan
import app.vibeyfin.domain.PlaybackReporter
import app.vibeyfin.domain.PlaybackRepository
import app.vibeyfin.domain.PlaybackTrack
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SegmentType
import app.vibeyfin.domain.SubtitleFile
import app.vibeyfin.domain.SubtitleRepository
import app.vibeyfin.domain.TrickplayTrack
import app.vibeyfin.domain.VideoFormat
import app.vibeyfin.domain.WatchRepository
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.profileOrNull
import app.vibeyfin.domain.toDiagnostics
import app.vibeyfin.domain.withLive
import app.vibeyfin.i18n.Texts
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.common.sessionEnded
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.item.badge
import app.vibeyfin.ui.item.episodeLabel
import app.vibeyfin.ui.subtitles.SubtitleAdded
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    /** The length the server knows, in milliseconds; null when it has none. Some players report a shorter one. */
    val runtimeMs: Long? = null,
    /** The player this stream is for. */
    val engine: PlaybackEngineChoice = PlaybackEngineChoice.Native,
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
     * The `TrackOption.index` of each subtitle inside the file that the player can show itself, in the
     * file's order. The player shows the one the state marks as selected (its place in this list) and none
     * when no such track is selected.
     */
    val embeddedSubtitles: List<Int> = emptyList(),
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

// Playing this far past where a failure happened counts as the connection, or the token, being good again.
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

// The next-episode card appears this long before the end of an episode when there are no credits to find.
private const val UP_NEXT_WINDOW_MS = 30_000L

// How long the card counts down before the next episode starts by itself.
private const val UP_NEXT_SECONDS = 10

// A video shorter than this never offers the card early: it would start the next one almost at once.
private const val MIN_AUTO_ADVANCE_RUNTIME_MS = 180_000L

// Seeking back this far outside the window takes the card away again.
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

/**
 * Skips in a row to one side, while their total shows: [forward] or back, [movedMs] how far they went
 * together (less than the steps add up to at the start or the end), [taps] how many, so a screen can
 * animate each one.
 */
data class SkipRun(val forward: Boolean, val movedMs: Long, val taps: Int) {
    /** [movedMs] in whole seconds, for the label. */
    val seconds: Int get() = ((movedMs + 500) / 1000).toInt()
}

data class PlaybackUiState(
    /** Asking the server how to play the item. */
    val loading: Boolean = true,
    val title: String = "",
    /** For an episode: the show and "S1 E3". */
    val subtitle: String? = null,
    /** A picture of the item for the system's Now Playing card: its landscape image, else its backdrop or poster. Null when it has none. */
    val artworkUrl: String? = null,
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
    /**
     * The episode after this one ("S2:E5 · Low Tide"), once it is known; null for a movie, the last episode, or before the
     * lookup is back. The player's Next episode button shows while this is set and [upNext] isn't; it calls `onPlayNow`.
     */
    val nextEpisode: String? = null,
    /** Playback reached the end and nothing follows; the screen should close. */
    val finished: Boolean = false,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
    /** What the stats overlay shows for the current stream; null while there is none. */
    val diagnostics: PlaybackDiagnostics? = null,
    /** The signed-in profile wants the stats overlay. */
    val showDiagnostics: Boolean = false,
    /** The player offers picture-in-picture and starts it when the app is left; off, leaving the app pauses. */
    val pictureInPicture: Boolean = true,
    /** A vertical swipe on the player changes the volume (right half) and the brightness (left half). */
    val swipeGestures: Boolean = true,
    /** Seconds of video the player should load ahead; 0 is Auto, the platform player's own choice. */
    val bufferAheadSeconds: Int = ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
    /** Subtitles use the platform's caption style, so the four values below are ignored. */
    val captionsFollowSystem: Boolean = true,
    /** Subtitle size: 0 small, 1 medium, 2 large, 3 extra large. */
    val subtitleSize: Int = ProfileSettings.DEFAULT_SUBTITLE_SIZE,
    /** Subtitle text colour: 0 white, 1 yellow, 2 light blue. */
    val subtitleTextColor: Int = ProfileSettings.DEFAULT_SUBTITLE_TEXT_COLOR,
    /** Subtitle background: 0 none, 1 shadow, 2 box. */
    val subtitleBackground: Int = ProfileSettings.DEFAULT_SUBTITLE_BACKGROUND,
    /** Set while an intro, recap or the like is playing; stays for the whole segment. */
    val skipOffer: SkipOffer? = null,
    /** Where the player should seek to now (set by `onSkip`); it clears this with `onSeekHandled`. */
    val pendingSeekMs: Long? = null,
    /** How far the skip-back button jumps, from the profile. */
    val skipBackSeconds: Int = ProfileSettings.DEFAULT_SKIP_BACK_SECONDS,
    /** How far the skip-forward button jumps, from the profile. */
    val skipForwardSeconds: Int = ProfileSettings.DEFAULT_SKIP_FORWARD_SECONDS,
    /** The skips just made in a row to one side; null once [SKIP_RUN_WINDOW] passes without another. */
    val skipRun: SkipRun? = null,
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
    /** The server lets this user add subtitles, and the device is online. */
    val canSearchSubtitles: Boolean = false,
    /** The movie or episode "Search for subtitles" would search for; null for a trailer, or what plays from a download. */
    val subtitleSearchItemId: String? = null,
    /** A short message over the picture that goes by itself. */
    val notice: String? = null,
) {
    /** The speeds to pick from. */
    val speeds: List<Float> get() = PLAYBACK_SPEEDS

    /** Whether there is anything to choose between, so the player shows its audio and subtitles control. */
    val hasTrackChoices: Boolean get() = audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty() || offersSubtitleSearch

    /** Whether the Subtitles list ends with "Search for subtitles". */
    val offersSubtitleSearch: Boolean get() = canSearchSubtitles && subtitleSearchItemId != null

    /** The rows of the Subtitles list: a title with no subtitles has just "Off" when the search can add one. */
    val subtitleChoices: List<TrackOption>
        get() = subtitleTracks.ifEmpty { if (offersSubtitleSearch) listOf(TrackOption(SUBTITLES_OFF, Strings.subtitlesOff, selected = true)) else emptyList() }
}

/** The speeds the player offers. */
val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** A skip within this long of the last one, to the same side, adds to its [SkipRun]. */
val SKIP_RUN_WINDOW = 1.seconds

// How long a download waits for the server's record of where the user stopped before using the device's.
private val SERVER_WAIT = 3.seconds

/** Resuming starts this long before where the user stopped, so the scene they left is in view again. */
const val RESUME_REWIND_MS = 5_000L

private val NOTICE_SHOWN = 4.seconds

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
 * Auto-advance: for an episode, the next one is looked up in the background. When its credits (an Outro
 * segment) start, or in the last 30 s if there are none, [PlaybackUiState.upNext] takes the place of
 * "Skip credits" and counts down 10 s, standing still while the video is paused. Then it plays the next
 * episode in this same ViewModel, so neither native player is rebuilt: the finished one is reported
 * stopped at its end, so the server marks it watched, and the stream changes.
 * [onWatchCredits] dismisses the card for this episode; [onPlayNow] doesn't wait. A video under three
 * minutes only counts down once it has ended. [autoAdvance] is on by default.
 */
class PlaybackViewModel(
    private var itemId: String,
    private val library: LibraryRepository,
    private val playback: PlaybackRepository,
    private val reporter: PlaybackReporter,
    private val connection: ConnectionManager,
    private val watch: WatchRepository,
    private val profileSettings: ProfileSettings,
    private val binge: BingeCounter,
    /** What is downloaded: an item that is on the device plays from it, with or without the server. */
    private val offline: OfflineLibrary,
    private val offlineMode: OfflineMode,
    /** Where a download's progress goes while it plays: the device, and the server later. */
    private val localReporter: LocalPlaybackReporter,
    subtitles: SubtitleRepository,
    private val retryDelays: List<Duration> = NETWORK_RETRY_DELAYS,
    private val startOver: Boolean = false,
    private val autoAdvance: Boolean = true,
    /** How long one second of the countdown takes; tests make it short. */
    private val countdownStep: Duration = 1.seconds,
) : ViewModel() {
    private val content = MutableStateFlow(PlaybackUiState())
    private val textSource = MutableStateFlow(PlaybackText())
    private val rendered: Flow<PlaybackUiState> = combine(content, textSource, Texts.language) { state, source, _ -> state.withText(source) }

    // The four subtitle style settings, in the order of the [PlaybackUiState] fields.
    private val subtitleStyle = combine(
        profileSettings.captionsFollowSystem,
        profileSettings.subtitleSize,
        profileSettings.subtitleTextColor,
        profileSettings.subtitleBackground,
    ) { follow, size, color, background -> SubtitleSettings(follow, size, color, background) }

    val uiState: StateFlow<PlaybackUiState> = combine(
        rendered,
        connection.sessionEnded(),
        profileSettings.showPlaybackStats,
        profileSettings.bufferAheadSeconds,
        subtitleStyle,
    ) { content, exit, showStats, bufferAhead, subtitle ->
        content.copy(
            exit = exit,
            showDiagnostics = showStats ?: false,
            bufferAheadSeconds = bufferAhead ?: ProfileSettings.DEFAULT_BUFFER_AHEAD_SECONDS,
            captionsFollowSystem = subtitle.followSystem ?: true,
            subtitleSize = subtitle.size ?: ProfileSettings.DEFAULT_SUBTITLE_SIZE,
            subtitleTextColor = subtitle.textColor ?: ProfileSettings.DEFAULT_SUBTITLE_TEXT_COLOR,
            subtitleBackground = subtitle.background ?: ProfileSettings.DEFAULT_SUBTITLE_BACKGROUND,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackUiState())

    private var lastPositionMs = 0L

    // Where this item's events go: the server, or the device while a download plays.
    private var active: PlayReporter = reporter

    // The download being played; null when the item comes from the server.
    private var local: LocalPlayable? = null

    // The item has been read, so where to start is known and a retry doesn't need to ask again.
    private var started = false

    // What is playing, as the server describes it.
    private var item: MediaItem? = null

    // The episode after [item], once looked up; null while unknown, and for anything that has no next one.
    private var next: MediaItem? = null

    // The user chose "Watch credits": no card for the rest of this episode.
    private var upNextDismissed = false

    // Whether the next episode may follow by itself: it is on and the viewer has not chosen "Watch credits"
    private val autoAdvanceOpen get() = autoAdvance && !upNextDismissed

    // The item played to its end, so a dismissed card now means leaving.
    private var ended = false

    private var inPictureInPicture = false

    // Episodes in a row before the player asks "Are you still watching?"; kept up to date from the profile's settings.
    private var bingeThreshold = ProfileSettings.DEFAULT_BINGE_THRESHOLD

    private var countdown: Job? = null
    private val videoPlaying: MutableStateFlow<Boolean> = MutableStateFlow(true)

    // Clears [PlaybackUiState.skipRun] after [SKIP_RUN_WINDOW]; each skip starts it again.
    private var skipRunEnd: Job? = null
    private var method: PlayMethod? = null
    private var streams = 0

    // The item's marked segments; loaded once, by the first [open].
    private var segments: List<MediaSegment> = emptyList()
    private var segmentsRequested = false

    // The profile jumps over intros and recaps by itself.
    private var autoSkip = false

    // Segments (start, end) already jumped over by auto-skip; each is skipped once, so going back stays possible.
    private val autoSkipped = mutableSetOf<Pair<Long, Long>>()

    // The item and media source the trickplay was asked for, so a re-opened stream of the same one doesn't ask again.
    private var trickplayFor: Pair<String, String>? = null

    // What the current stream plays. Null while there is none.
    private var plan: PlaybackPlan? = null

    // What the player last measured for this stream; cleared with a new one.
    private var liveDiagnostics: LiveDiagnostics? = null

    // The audio and subtitle tracks selected now; a null subtitle is none.
    private var audioNow: Int? = null
    private var subtitleNow: Int? = null

    // What the user picked, to send with the next negotiation; null until they pick, leaving it to the server.
    private var choice: TrackChoice? = null

    // Set once direct play failed on this device, so a later track change doesn't try it again.
    private var transcodeOnly = false

    // What has gone wrong since playback last got going: the tries at the same stream used up, whether
    // the server was already asked again after a rejected token, and where it first failed. Playing
    // [RECOVERED_AFTER_MS] past that point clears it (see [onPosition]); the player says it is playing the
    // moment it opens a stream, so that can't be the signal.
    private var networkRetries = 0
    private var reauthorized = false
    private var failedAtMs = 0L

    // The error on screen is a lost connection, which says nothing about what the device can play.
    private var lostConnection = false

    private var noticeJob: Job? = null

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
            profileSettings.pictureInPicture.collect { on ->
                content.update { it.copy(pictureInPicture = on ?: true) }
            }
        }
        viewModelScope.launch {
            profileSettings.swipeGestures.collect { on ->
                content.update { it.copy(swipeGestures = on ?: true) }
            }
        }
        viewModelScope.launch {
            profileSettings.autoSkipSegments.collect { autoSkip = it ?: false }
        }
        viewModelScope.launch {
            subtitles.available.collect { can -> content.update { it.copy(canSearchSubtitles = can) } }
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
        val inPlayer = !current.subtitleBurnedIn && (
            target == null ||
                current.subtitleFiles.any { it.index == target } ||
                (current.method == PlayMethod.DirectPlay && target in current.embeddedSubtitles)
            )
        if (inPlayer) {
            subtitleNow = target
            applySelection(current)
        } else {
            reopen(current, audio = audioNow, subtitle = target)
        }
    }

    /** A subtitle reached the server: it is turned on from where the video is now. One for an episode no longer playing changes nothing. */
    fun onSubtitleAdded(added: SubtitleAdded) {
        if (added.itemId != itemId) return
        val current = plan ?: return
        reopen(current, audio = audioNow, subtitle = added.streamIndex)
        onNotice(Strings.subtitlesAdded(added.languageName))
    }

    /** Shows [text] over the picture for a few seconds. */
    fun onNotice(text: String) {
        noticeJob?.cancel()
        content.update { it.copy(notice = text) }
        noticeJob = viewModelScope.launch {
            delay(NOTICE_SHOWN)
            content.update { it.copy(notice = null) }
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
        videoPlaying.value = true
        active.playing(positionMs)
    }

    fun onPaused(positionMs: Long) {
        lastPositionMs = positionMs
        videoPlaying.value = false
        active.paused(positionMs)
    }

    fun onSeeked(positionMs: Long) {
        lastPositionMs = positionMs
        active.seeked(positionMs)
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
        val from = basePosition(positionMs)
        val to = (from - content.value.skipBackSeconds * 1000L).coerceAtLeast(0L)
        seekTo(to)
        addToSkipRun(forward = false, movedMs = from - to)
    }

    /** Seeks forward by the profile's skip length from [positionMs], not past [durationMs]; does nothing while the duration is unknown. */
    fun onSkipForward(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val from = basePosition(positionMs)
        val to = (from + content.value.skipForwardSeconds * 1000L).coerceAtMost(durationMs)
        seekTo(to)
        addToSkipRun(forward = true, movedMs = (to - from).coerceAtLeast(0L))
    }

    // Adds a skip to the run to the same side, or starts a new run, and shows it for [SKIP_RUN_WINDOW] more.
    private fun addToSkipRun(forward: Boolean, movedMs: Long) {
        content.update { state ->
            val run = state.skipRun?.takeIf { it.forward == forward }
            state.copy(
                skipRun = SkipRun(forward, (run?.movedMs ?: 0L) + movedMs, (run?.taps ?: 0) + 1),
            )
        }
        skipRunEnd?.cancel()
        skipRunEnd = viewModelScope.launch {
            delay(SKIP_RUN_WINDOW)
            content.update { it.copy(skipRun = null) }
        }
    }

    // A seek still waiting for the player is where the video is going, so a second tap builds on it.
    // Stacking works on [PlaybackUiState.pendingSeekMs] while it is not cleared. On a device the position
    // passed in is already right after a seek, because both native players update their own position at once.
    private fun basePosition(positionMs: Long) = content.value.pendingSeekMs ?: positionMs

    // Asks the player to seek. A second skip before the player has done it stacks through [basePosition].
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
        active.position(positionMs)
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
        active.stop(positionMs)
        ended = true
        val upcoming = next
        if (upcoming != null && autoAdvanceOpen) {
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
        active.stop(positionMs)
        cancelCountdown()
        if (ended) content.update { it.copy(finished = true) }
    }

    /** The user closed the player. */
    fun onClosed(positionMs: Long) {
        lastPositionMs = positionMs
        active.stop(positionMs)
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
        if (ended && upcoming != null && autoAdvanceOpen) {
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
        // A file on the device has no other way to be offered: whatever went wrong, it won't play.
        if (local != null) return giveUp(PlaybackProblem.Failed)
        when (failure) {
            PlaybackFailure.Network -> retrySameStream(failed)
            PlaybackFailure.Unauthorized -> askAgainAfterRejection()
            PlaybackFailure.Unsupported -> fallBackToTranscode()
        }
    }

    fun onToggleDiagnostics() {
        viewModelScope.launch { profileSettings.setShowPlaybackStats(!uiState.value.showDiagnostics) }
    }

    fun onRetry() {
        // A transcode that was needed still is after a lost connection; after any other failure start afresh.
        if (!lostConnection) transcodeOnly = false
        lostConnection = false
        networkRetries = 0
        reauthorized = false
        if (started) open(startAtMs = lastPositionMs) else start()
    }

    // The connection was lost: the player is given the same stream again from where it was, after a short
    // wait with the spinner showing, and the server hears the video is paused meanwhile. When the tries
    // are used up it is an error with "Try again", not a transcode, which would need the same connection.
    private fun retrySameStream(failed: PlaybackStream) {
        val wait = retryDelays.getOrNull(networkRetries) ?: return giveUp(PlaybackProblem.Network, lostConnection = true)
        if (networkRetries == 0 && !reauthorized) failedAtMs = lastPositionMs
        cancelCountdown()
        networkRetries++
        active.paused(lastPositionMs)
        content.update { it.copy(loading = true, stream = null, message = null, pendingSeekMs = null) }
        viewModelScope.launch {
            delay(wait)
            content.update {
                it.copy(loading = false, pendingSeekMs = null, stream = failed.copy(id = ++streams, startPositionMs = lastPositionMs))
            }
        }
    }

    // The server rejected the token on the stream, which the player fetches itself and so bypasses the
    // usual 401 handling. Asking again goes through it: a dead token ends the session there, and one
    // that is only stale gets a fresh stream address. Only once, so a stream that keeps being refused
    // ends as an error.
    private fun askAgainAfterRejection() {
        if (reauthorized) return giveUp(PlaybackProblem.Failed)
        if (networkRetries == 0) failedAtMs = lastPositionMs
        reauthorized = true
        active.stop(lastPositionMs, failed = true)
        open(startAtMs = lastPositionMs)
    }

    // The device can't play it: once more as a transcode, then an error.
    private fun fallBackToTranscode() {
        if (method == PlayMethod.Transcode) return giveUp(PlaybackProblem.Failed)
        active.stop(lastPositionMs, failed = true)
        transcodeOnly = true
        open(startAtMs = lastPositionMs)
    }

    private fun giveUp(problem: PlaybackProblem, lostConnection: Boolean = false) {
        cancelCountdown()
        active.stop(lastPositionMs, failed = true)
        method = null
        this.lostConnection = lostConnection
        content.update { it.copy(stream = null, loading = false, message = problemMessage(problem), pendingSeekMs = null) }
    }

    // Kept as the problem, so the message is written again when the language changes.
    private fun problemMessage(problem: PlaybackProblem): String {
        textSource.update { it.copy(problem = problem) }
        return problem.message()
    }

    override fun onCleared() {
        // The player has usually said so already; this covers a screen that left without it. Whatever
        // is queued, `Stopped` included, is still sent after the screen's scope is gone.
        active.stop(lastPositionMs)
        reporter.close()
        localReporter.close()
    }

    // Reads the item for its title and where the user stopped, then opens it there.
    private fun start() {
        content.update { it.copy(loading = true, stream = null, message = null) }
        viewModelScope.launch {
            val profile = connection.state.value.profileOrNull()
            val downloaded = profile?.let { offline.playable(it, itemId) }
            when {
                // On the device: it plays from there. With the server in reach its own record of where the user stopped is the one
                // that counts (they may have watched on another device); without it, or when it is slow, the device's own.
                downloaded != null -> {
                    val server = if (offlineMode.isOffline.value) null else withTimeoutOrNull(SERVER_WAIT) { (library.item(itemId) as? ApiResult.Success)?.value }
                    begin(server ?: downloaded.item, honourStartOver = true, downloaded = downloaded)
                }

                offlineMode.isOffline.value -> content.update { it.copy(loading = false, message = problemMessage(PlaybackProblem.NotDownloaded)) }

                else -> when (val result = library.item(itemId)) {
                    is ApiResult.Success -> begin(result.value, honourStartOver = true)
                    is ApiResult.Failure -> content.update { it.copy(loading = false, message = problemMessage(PlaybackProblem.Load(result.error))) }
                }
            }
        }
    }

    // Starts [found] where the user left off (or at the beginning), and looks up what comes after it.
    private fun begin(found: MediaItem, honourStartOver: Boolean, downloaded: LocalPlayable? = null) {
        local = downloaded
        active = if (downloaded != null) localReporter else reporter
        val startAtMs = if ((honourStartOver && startOver) || found.played) 0L else (found.resumePositionMs - RESUME_REWIND_MS).coerceAtLeast(0)
        item = found
        started = true
        lastPositionMs = startAtMs
        textSource.update { it.copy(item = found) }
        content.update {
            it.copy(
                title = found.title,
                subtitle = Strings.metaLine(found.seriesName, found.episodeLabel()),
                artworkUrl = found.landscapeUrl ?: found.backdropUrl ?: found.posterUrl,
                subtitleSearchItemId = found.id.takeIf { downloaded == null && found.canPlay },
            )
        }
        open(startAtMs)
        lookUpNext(found)
    }

    private fun lookUpNext(current: MediaItem) {
        // Looked up whether or not the episode plays on by itself: the Next episode button needs it either way.
        if (current.kind != ItemKind.Episode || current.seriesId == null) return
        viewModelScope.launch {
            // A download plays on into the next one downloaded; the server's own order is for what comes from the server.
            val found = if (local != null) {
                connection.state.value.profileOrNull()?.let { offline.nextAfter(it, current) }
            } else {
                (watch.nextEpisode(current) as? ApiResult.Success)?.value
            } ?: return@launch
            // Only if the user is still on the episode this was asked for.
            if (item?.id == current.id) {
                next = found
                textSource.update { it.copy(next = found) }
                content.update { it.copy(nextEpisode = Strings.metaLine(found.episodeLabel(), found.title)) }
            }
        }
    }

    // Shows the card when the credits start (or the end is near enough, [UP_NEXT_WINDOW_MS]) and it is worth it,
    // or takes it away again after a seek back before that. Called with every new position.
    private fun checkUpNext(positionMs: Long) {
        val upcoming = next ?: return
        val runtime = item?.runtimeMs ?: return
        if (!autoAdvanceOpen || ended || content.value.stillWatching) return
        val startMs: Long = upNextStartMs(runtime)
        if (countdown == null) {
            if (runtime >= MIN_AUTO_ADVANCE_RUNTIME_MS && positionMs >= startMs) startCountdown(upcoming)
        } else if (positionMs < startMs - UP_NEXT_SEEK_SLACK_MS) {
            cancelCountdown()
        }
    }

    private fun upNextStartMs(runtimeMs: Long): Long {
        val outroStartMs: Long? = segments.filter { it.type == SegmentType.Outro && it.endMs > it.startMs }.minOfOrNull { it.startMs }
        return outroStartMs ?: (runtimeMs - UP_NEXT_WINDOW_MS)
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
                awaitPlaying()
                delay(countdownStep)
                awaitPlaying()
            }
            countdown = null
            playNext(byItself = true)
        }
    }

    // An ended video can't be paused, so the countdown never waits on it then.
    private suspend fun awaitPlaying() {
        if (!ended) videoPlaying.first { it }
    }

    private fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
        content.update { it.copy(upNext = null, skipOffer = skipOfferNow(cardShowing = false)) }
    }

    /** "Play now" on the card, or the player's Next episode button: the next episode starts without waiting for the countdown. */
    fun onPlayNow() {
        if (next == null) return
        binge.interacted()
        playNext(byItself = false)
    }

    // Moves on to [next]. [byItself]: nobody asked for it, so it counts towards "Are you still watching?".
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

    // Plays [next] in place of the episode that is ending. The finished one is reported stopped at its
    // end, whatever the countdown cut short, so the server counts it as watched.
    private fun advance() {
        val target = next ?: return
        active.stop(item?.runtimeMs ?: lastPositionMs)
        val wasLocal = local != null
        local = null
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
            it.copy(loading = true, stream = null, upNext = null, nextEpisode = null, skipOffer = null, trickplay = null, speed = 1f, badge = null, stillWatching = false, audioTracks = emptyList(), subtitleTracks = emptyList(), message = null, finished = false)
        }
        viewModelScope.launch {
            // A download goes on into the next one, which is downloaded too: it is found there again to play from the device.
            val downloaded = if (wasLocal) connection.state.value.profileOrNull()?.let { offline.playable(it, target.id) } else null
            begin(downloaded?.item ?: target, honourStartOver = false, downloaded = downloaded)
        }
    }

    private fun MediaItem.toUpNext(secondsLeft: Int) = UpNext(
        id = id,
        label = episodeLabel(),
        title = title,
        imageUrl = landscapeUrl,
        imageBlurHash = landscapeBlurHash,
        secondsLeft = secondsLeft,
    )

    // The player does the switching: only the picker and the reports change, the stream stays.
    private fun applySelection(current: PlaybackPlan) {
        remember(current)
        val updated = current.copy(audioStreamIndex = audioNow, subtitleStreamIndex = subtitleNow)
        plan = updated
        active.begin(updated)
        refreshDiagnostics()
        content.update {
            it.copy(
                audioTracks = it.audioTracks.map { track -> track.copy(selected = track.index == audioNow) },
                subtitleTracks = it.subtitleTracks.map { track -> track.copy(selected = track.index == (subtitleNow ?: SUBTITLES_OFF)) },
            )
        }
    }

    // The current stream ends where it is and the server is asked again with the new choice.
    private fun reopen(current: PlaybackPlan, audio: Int?, subtitle: Int?) {
        if (content.value.stream == null) return
        audioNow = audio
        subtitleNow = subtitle
        remember(current)
        active.stop(lastPositionMs)
        open(startAtMs = lastPositionMs)
    }

    // The plan's diagnostics with whatever the player measured on top.
    private fun refreshDiagnostics() {
        val current = plan ?: return
        val base = current.toDiagnostics(audioNow)
        val merged = liveDiagnostics?.let(base::withLive) ?: base
        content.update { it.copy(diagnostics = merged) }
    }

    // Keeps what is selected, so the next negotiation (a retry, the transcode fallback) asks for the same.
    private fun remember(current: PlaybackPlan) {
        choice = TrackChoice(current.mediaSourceId, audioNow, subtitleNow ?: SUBTITLES_OFF)
    }

    // Off the critical path: a slow or failed call only means there is nothing to skip. Once per item.
    private fun loadSegments() {
        if (segmentsRequested) return
        segmentsRequested = true
        viewModelScope.launch {
            segments = playback.segments(itemId)
            // While the stream is still being set up, Ready does this once it has cleared the pending seek.
            if (content.value.stream != null) refreshSkipOffer()
        }
    }

    // Off the critical path, like [loadSegments]: a slow or failed call only means no scrub preview. Once per item and media source.
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

    // Where to jump now: the end of an intro or recap that is playing and not skipped before; never credits. Marks it skipped.
    private fun autoSkipTarget(): Long? {
        val playing = segments.firstOrNull {
            (it.type == SegmentType.Intro || it.type == SegmentType.Recap) &&
                lastPositionMs >= it.startMs && lastPositionMs < it.endMs && (it.startMs to it.endMs) !in autoSkipped
        } ?: return null
        autoSkipped += playing.startMs to playing.endMs
        return playing.endMs
    }

    // The offer for [lastPositionMs]: the skippable segment playing now, the one that ends last if several.
    // No "Skip credits" while the up-next card shows ([cardShowing]): the card already offers what comes after them.
    private fun skipOfferNow(cardShowing: Boolean): SkipOffer? {
        val playing = segments
            .filter { it.type != SegmentType.Unknown && lastPositionMs >= it.startMs && lastPositionMs < it.endMs }
            .filter { !(cardShowing && it.type == SegmentType.Outro) }
            .maxByOrNull { it.endMs }
        return playing?.let { SkipOffer(Strings.skip(it.type), it.endMs) }
    }

    // Opens the file on the device: there is nothing to ask the server, no scrub preview, and no segments to skip.
    private fun openLocal(downloaded: LocalPlayable, startAtMs: Long) {
        val ready = downloaded.plan
        plan = ready
        method = ready.method
        audioNow = ready.audioStreamIndex
        subtitleNow = ready.subtitleStreamIndex
        active.begin(ready)
        liveDiagnostics = null
        textSource.update { it.copy(format = ready.videoFormat, audioTracks = ready.audioTracks, subtitleTracks = ready.subtitleTracks) }
        content.update {
            it.copy(
                loading = false,
                pendingSeekMs = null,
                stream = PlaybackStream(
                    id = ++streams,
                    url = ready.url,
                    headers = emptyMap(),
                    startPositionMs = startAtMs,
                    engine = ready.engine,
                    subtitleFiles = ready.subtitleFiles,
                    audioInPlayer = true,
                    dynamicRange = ready.videoFormat?.dynamicRange ?: DynamicRange.Sdr,
                ),
                diagnostics = ready.toDiagnostics(audioNow),
                badge = ready.videoFormat?.badge(),
                audioTracks = ready.audioTracks.toAudioOptions(),
                subtitleTracks = ready.subtitleTracks.toSubtitleOptions(),
            )
        }
    }

    private fun open(startAtMs: Long) {
        // A stream that restarts (a retry, a track change) is not the end of the episode.
        cancelCountdown()
        content.update { it.copy(loading = true, stream = null, message = null, pendingSeekMs = null) }
        val downloaded = local
        if (downloaded != null) return openLocal(downloaded, startAtMs)
        loadSegments()
        viewModelScope.launch {
            val picked = choice
            when (val negotiation = playback.negotiate(itemId, transcodeOnly, picked?.mediaSourceId, picked?.audio, picked?.subtitle)) {
                is PlaybackNegotiation.Ready -> {
                    val ready = negotiation.plan
                    plan = ready
                    method = ready.method
                    audioNow = ready.audioStreamIndex
                    subtitleNow = ready.subtitleStreamIndex
                    active.begin(ready)
                    liveDiagnostics = null
                    loadTrickplay(ready.mediaSourceId)
                    textSource.update { it.copy(format = ready.videoFormat, audioTracks = ready.audioTracks, subtitleTracks = ready.subtitleTracks) }
                    content.update {
                        it.copy(
                            loading = false,
                            pendingSeekMs = null,
                            stream = PlaybackStream(
                                id = ++streams,
                                url = ready.url,
                                headers = ready.headers,
                                startPositionMs = startAtMs,
                                runtimeMs = ready.runtimeMs,
                                engine = ready.engine,
                                subtitleFiles = ready.subtitleFiles,
                                audioInPlayer = ready.method == PlayMethod.DirectPlay,
                                embeddedSubtitles = ready.embeddedSubtitles.takeIf { ready.method == PlayMethod.DirectPlay }.orEmpty(),
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
                    content.update { it.copy(loading = false, message = problemMessage(PlaybackProblem.NoCompatibleStream)) }

                is PlaybackNegotiation.Failed ->
                    content.update { it.copy(loading = false, message = problemMessage(PlaybackProblem.Load(negotiation.error))) }
            }
        }
    }
}

// What the player shows as text, kept as the things it is made of, so it is written again in the language that is current.
private data class PlaybackText(
    val item: MediaItem? = null,
    val next: MediaItem? = null,
    val problem: PlaybackProblem? = null,
    val format: VideoFormat? = null,
    val audioTracks: List<PlaybackTrack> = emptyList(),
    val subtitleTracks: List<PlaybackTrack> = emptyList(),
)

private sealed interface PlaybackProblem {
    data object Failed : PlaybackProblem

    data object Network : PlaybackProblem

    data object NotDownloaded : PlaybackProblem

    data object NoCompatibleStream : PlaybackProblem

    data class Load(val error: ApiError) : PlaybackProblem

    fun message(): String = when (this) {
        Failed -> LoadMessages.PLAYBACK_FAILED
        Network -> LoadMessages.PLAYBACK_NETWORK
        NotDownloaded -> LoadMessages.NOT_DOWNLOADED
        NoCompatibleStream -> LoadMessages.NO_COMPATIBLE_STREAM
        is Load -> LoadMessages.forError(error)
    }
}

// Only the text changes: the flags and the stream stay as the content state has them.
private fun PlaybackUiState.withText(source: PlaybackText): PlaybackUiState = copy(
    subtitle = subtitle?.let { source.item?.let(::subtitleLine) },
    nextEpisode = nextEpisode?.let { source.next?.let(::nextLine) },
    message = message?.let { source.problem?.message() },
    badge = badge?.let { source.format?.badge() },
    audioTracks = audioTracks.map { option -> option.copy(label = source.audioTracks.labelOf(option.index)) },
    subtitleTracks = subtitleTracks.map { option ->
        option.copy(label = if (option.index == SUBTITLES_OFF) Strings.subtitlesOff else source.subtitleTracks.labelOf(option.index))
    },
)

private fun subtitleLine(item: MediaItem): String? = Strings.metaLine(item.seriesName, item.episodeLabel())

private fun nextLine(item: MediaItem): String? = Strings.metaLine(item.episodeLabel(), item.title)

private fun List<PlaybackTrack>.labelOf(index: Int): String = firstOrNull { it.index == index }?.name ?: Strings.track(index)

// Picking between audio tracks needs at least two.
private fun List<PlaybackTrack>.toAudioOptions(): List<TrackOption> = if (size < 2) emptyList() else map { TrackOption(it.index, it.name ?: Strings.track(it.index), it.selected) }

// "Off" first, selected when no subtitle is. Empty when the file has no subtitles.
private fun List<PlaybackTrack>.toSubtitleOptions(): List<TrackOption> = if (isEmpty()) {
    emptyList()
} else {
    listOf(TrackOption(SUBTITLES_OFF, Strings.subtitlesOff, selected = none { it.selected })) +
        map { TrackOption(it.index, it.name ?: Strings.track(it.index), it.selected) }
}

/** The four subtitle style settings as read from [ProfileSettings]; null while nobody is signed in. */
private data class SubtitleSettings(val followSystem: Boolean?, val size: Int?, val textColor: Int?, val background: Int?)
