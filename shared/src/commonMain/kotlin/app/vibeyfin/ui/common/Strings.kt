package app.vibeyfin.ui.common

import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.SegmentType
import kotlin.math.roundToInt

/**
 * Every piece of text the apps show: titles, labels, buttons, help text, empty states and
 * accessibility descriptions. Screens on both platforms read from here and hold no string
 * literals of their own, so wording and sentence case stay in step. Errors live in
 * `LoadMessages`, `SignInMessages` and `ConnectionIssueMessage`.
 *
 * Strings are plain Kotlin for now. If the app is ever translated, this object is the one place
 * that changes.
 */
object Strings {
    val appName = "VibeyFin"

    // Actions used on several screens
    val tryAgain = "Try again"
    val cancel = "Cancel"
    val back = "Back"
    val searchAgain = "Search again"
    val searching = "Searching"
    val addServer = "Add server"
    val signIn = "Sign in"
    val signingIn = "Signing in"
    val signOut = "Sign out"
    val settings = "Settings"

    // Who's watching
    val whoIsWatching = "Who's watching?"
    val otherUser = "Other user"
    val needsPassword = "Needs a password"
    val cantReachServerTitle = "Can't reach this server"
    val cantReachServerHelp = "Profiles saved on this device still show, but signing in needs the server."
    val username = "Username"
    val password = "Password"
    val signInAsAnotherUser = "Sign in as another user"

    fun signInAs(name: String) = "Sign in as $name"

    // Quick Connect
    val quickConnect = "Quick Connect"
    val quickConnectHelp =
        "On a device that's signed in to Jellyfin, open your user menu, choose Quick Connect and enter this code."
    val quickConnectWaiting = "Waiting for approval"
    val quickConnectGettingCode = "Getting a code"

    // Servers
    val servers = "Servers"
    val onYourNetwork = "On your network"
    val addToServers = "Add"
    val noServersFound = "No servers found on your network."
    val noNewServersFound = "No new servers found."

    fun jellyfinVersion(version: String) = "Jellyfin $version"

    // Add server
    val addAServer = "Add a server"
    val serverAddress = "Server address"
    val serverAddressExample = "192.168.1.20:8096"
    val connect = "Connect"
    val connecting = "Connecting"
    val found = "Found"
    val cantConnectTitle = "Can't connect"
    val saveAddress = "Save address"
    val done = "Done"

    fun alsoReachableAt(addresses: List<String>) = "Also reachable at " + addresses.joinToString(", ")

    fun alreadySaved(addressIsNew: Boolean) =
        if (addressIsNew) {
            "You already have this server. This adds the new address to it."
        } else {
            "You already have this server at this address."
        }

    // Home and browsing
    val home = "Home"
    /** The row of the user's libraries at the end of Home. */
    val libraries = "My media"

    /** The header of a library's row of what was added most recently. */
    fun recentlyAddedIn(library: String) = "Recently added in $library"

    /** The action in a row's header that opens the whole list, and what a screen reader says for it. */
    val seeAll = "See all"
    fun seeAllIn(row: String) = "See all, $row"

    /** The main navigation bar: its labels, and the name a screen reader gives the bar. */
    val tabHome = "Home"
    val tabLibrary = "Library"
    val tabDownloads = "Downloads"
    val mainNavigation = "Main navigation"

    /** The category buttons under Home's header, in the order they are shown. */
    val filterShows = "Shows"
    val filterMovies = "Movies"
    val filterNew = "New"
    val filterCategories = "Categories"

    /** What a screen reader says for the group of category buttons. */
    val filterHome = "Filter Home"

    /** What a screen reader says for a button that opens a list of genres. */
    fun opensList(label: String) = "$label, opens a list"

    /** The Sort and Genre buttons on a category page, and the rows of their sheets. */
    val sortName = "Name"
    val sortAdded = "Recently added"
    val sortRating = "Rating"
    val sortTitle = "Sort by"
    val genreTitle = "Genre"
    val genreButton = "Genre"
    val allGenres = "All genres"
    fun sortChip(label: String) = "Sort: $label"
    fun genreChip(name: String) = "Genre: $name"

    /** What a screen reader says for a sheet row that holds [count] titles. */
    fun titleCount(name: String, count: Int) = if (count == 1) "$name, 1 title" else "$name, $count titles"

    /** In the Categories sheet when the server has none. */
    val noCategories = "This server has no categories."

    /** On the Library and Downloads tabs until their features land. */
    val notAvailableYet = "Not available yet"
    val switchProfile = "Switch profile"
    val switchServer = "Switch server"
    val noLibraries = "This account has no libraries yet. Ask the server's admin to add one."
    val emptyFolder = "There's nothing in here yet."
    val loadMoreFailed = "Couldn't load the rest of this list. Check your connection and try again."

    fun greeting(name: String) = "Hi, $name"

    val continueWatching = "Continue watching"
    /** The label of the Watched button under Play; its state is the tick. */
    val watchedAction = "Watched"
    val markSeriesWatched = "Mark series as watched"
    val markSeriesUnwatched = "Mark series as unwatched"
    val markWatched = "Mark as watched"
    val markUnwatched = "Mark as unwatched"
    val details = "Details"

    /** In an item's facts, once the server says the user has watched it. */
    val watched = "Watched"

    /** What a screen reader says for the "…" menu on the item page. */
    val moreActions = "More actions"

    /** What a screen reader says while the Continue watching row is still loading. */
    val loadingContinueWatching = "Loading Continue watching"

    /** What a screen reader says while a screen shows grey placeholders instead of its content. */
    val loadingPage = "Loading"

    /**
     * What a screen reader says for a Continue watching card: the title, what it is (the episode, or
     * a movie's year) and, for something in progress, how far in the user is.
     */
    fun continueCardLabel(title: String, detail: String?, percentWatched: Int?) =
        listOfNotNull(title, detail, percentWatched?.let { "$it% watched" }).joinToString(", ")

    /** What a screen reader says for a poster: the title and, when known, the year. */
    fun cardLabel(title: String, year: Int?) = listOfNotNull(title, year?.toString()).joinToString(", ")

    // Playback
    val play = "Play"
    val startOver = "Start over"

    /** The card in the last seconds of an episode that offers the next one. */
    val upNext = "Up next"
    val playNow = "Play now"

    /** Dismisses the card and plays the episode out to the end of its credits. */
    val watchCredits = "Watch credits"

    fun upNextIn(seconds: Int) = "Starts in $seconds"

    /** The Resume button when the length isn't known. */
    val resume = "Resume"

    /** How much is left to watch: "38m left", or "1h 52m left" from an hour. Never under a minute. */
    fun timeLeftShort(remainingMs: Long): String {
        val minutes = ((remainingMs + 30_000) / 60_000).coerceAtLeast(1)
        val hours = minutes / 60
        val rest = minutes % 60
        val left = when {
            hours == 0L -> "${rest}m"
            rest == 0L -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
        return "$left left"
    }

    /** The Resume button: "Resume · 38m left". */
    fun resumeTimeLeft(remainingMs: Long) = "Resume · ${timeLeftShort(remainingMs)}"
    val pause = "Pause"
    val close = "Close"
    val playbackPosition = "Playback position"
    val audio = "Audio"
    val subtitles = "Subtitles"
    val subtitlesOff = "Off"
    val audioAndSubtitles = "Audio and subtitles"

    /** The player button that turns the video into a small window over other apps. */
    val pictureInPicture = "Picture in picture"

    /** The name of the Android notification channel for the media controls shown while a video plays. */
    val playbackNotificationChannel = "Playback"

    /** One spoken phrase for a label and its value, "Bitrate, 4.5 Mbps", where the two are drawn apart. */
    fun labelled(label: String, value: String) = "$label, $value"

    // Playback stats
    fun statsMethod(method: PlayMethod) = when (method) {
        PlayMethod.DirectPlay -> "Direct play"
        PlayMethod.DirectStream -> "Direct stream"
        PlayMethod.Transcode -> "Transcode"
    }
    val statsLabelMethod = "Method"
    val statsLabelReasons = "Reasons"
    val statsLabelVideo = "Video"
    val statsLabelAudio = "Audio"
    val statsLabelResolution = "Resolution"
    val statsLabelFrameRate = "Frame rate"
    val statsLabelBitrate = "Bitrate"
    val statsLabelDroppedFrames = "Dropped frames"
    val statsLabelThroughput = "Throughput"
    val statsLabelBuffer = "Buffer"

    /** Shown for a stat nobody can tell. */
    val statsUnavailable = "–"

    /**
     * The short codec name the stats overlay shows ("h264", "eac3 atmos"), the same on both platforms.
     * [code] is what the player reports: a MIME type on Android (`video/avc`), a FourCC on iOS (`avc1`).
     * An unknown one is shown as the player gave it, without its `video/` or `x-` prefix; null for none.
     */
    fun codecName(code: String?): String? {
        val key = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return CODEC_NAMES[key] ?: key.substringAfter('/').removePrefix("x-")
    }

    private val CODEC_NAMES = mapOf(
        // Android MIME types
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/av01" to "av1",
        "video/x-vnd.on2.vp9" to "vp9",
        "video/x-vnd.on2.vp8" to "vp8",
        "video/mp4v-es" to "mpeg4",
        "video/dolby-vision" to "dolby vision",
        "audio/mp4a-latm" to "aac",
        "audio/ac3" to "ac3",
        "audio/eac3" to "eac3",
        "audio/eac3-joc" to "eac3 atmos",
        "audio/true-hd" to "truehd",
        "audio/vnd.dts" to "dts",
        "audio/vnd.dts.hd" to "dts-hd",
        "audio/mpeg" to "mp3",
        "audio/opus" to "opus",
        "audio/vorbis" to "vorbis",
        "audio/flac" to "flac",
        // Apple FourCCs
        "avc1" to "h264",
        "avc3" to "h264",
        "hvc1" to "hevc",
        "hev1" to "hevc",
        "dvh1" to "dolby vision",
        "dvhe" to "dolby vision",
        "av01" to "av1",
        "vp09" to "vp9",
        "vp08" to "vp8",
        "mp4v" to "mpeg4",
        "mp4a" to "aac",
        "ac-3" to "ac3",
        "ec-3" to "eac3",
        ".mp3" to "mp3",
    )

    fun statsResolution(width: Int, height: Int) = "$width × $height"

    /** `24 fps`, `23.98 fps`: two decimals at most, none when the rate is whole. */
    fun statsFrameRate(framesPerSecond: Float): String {
        val hundredths = (framesPerSecond * 100).roundToInt()
        val text = if (hundredths % 100 == 0) (hundredths / 100).toString() else (hundredths / 100f).toString()
        return "$text fps"
    }

    /** `850 kbps` below a megabit, `4.5 Mbps` from there. Used for bitrate and throughput. */
    fun statsBitrate(kbps: Int) =
        if (kbps < 1000) "$kbps kbps" else "${kbps / 1000}.${(kbps % 1000) / 100} Mbps"

    /** `12.3 s` of video buffered ahead. */
    fun statsBuffer(milliseconds: Long): String {
        val tenths = (milliseconds.coerceAtLeast(0) / 100)
        return "${tenths / 10}.${tenths % 10} s"
    }

    /** The button that jumps over a marked part of the video. */
    fun skip(type: SegmentType): String = when (type) {
        SegmentType.Intro -> "Skip intro"
        SegmentType.Outro -> "Skip credits"
        SegmentType.Recap -> "Skip recap"
        SegmentType.Preview -> "Skip preview"
        SegmentType.Commercial -> "Skip ad"
        SegmentType.Unknown -> ""
    }

    // Format badges
    val badgeDolbyVision = "Dolby Vision"
    val badgeHdr10 = "HDR10"
    val badgeHlg = "HLG"
    val badge4k = "4K"
    val badgeHd = "HD"

    val speed = "Speed"

    /** `1x`, `0.75x`, `1.5x`. */
    fun speedValue(speed: Float): String {
        val hundredths = (speed * 100).roundToInt()
        val text = when {
            hundredths % 100 == 0 -> (hundredths / 100).toString()
            hundredths % 10 == 0 -> "${hundredths / 100}.${(hundredths % 100) / 10}"
            else -> "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
        }
        return "${text}x"
    }

    /** The page of a movie, an episode or a show: its sections, and what the Play button says for a show. */
    val cast = "Cast"
    val inCollections = "Collections"

    /** The actions under Play on an item's page. My List is the server's favourites. */
    val myList = "My List"
    fun myListAction(onList: Boolean) = if (onList) "Remove from My List" else "Add to My List"
    val trailer = "Trailer"
    val download = "Download"

    /** What a season's button says: always "Season 3"; the server's own names ("Series 3", "Season 3") are not used. 0 is Specials. */
    val specials = "Specials"
    fun season(number: Int) = "Season $number"
    val moreLikeThis = "More like this"
    val technicalInfo = "Technical info"
    val more = "More"
    val less = "Less"
    /** The sub-headings of a collection's members. */
    val collectionMovies = "Movies"
    val collectionShows = "Shows"
    val collectionOther = "More"
    fun collectionMovieCount(count: Int) = if (count == 1) "1 movie" else "$count movies"
    fun collectionShowCount(count: Int) = if (count == 1) "1 show" else "$count shows"
    fun collectionTitles(count: Int) = if (count == 1) "1 title" else "$count titles"
    fun seasonCount(count: Int) = if (count == 1) "1 season" else "$count seasons"
    fun playEpisode(episode: String) = "Play $episode"
    fun resumeEpisodeFrom(episode: String, positionMs: Long) = "Resume $episode from ${clock(positionMs)}"

    /** The link from an episode's or a season's page to its show: what a screen reader says for it. */
    fun goToSeries(name: String) = "Go to $name"

    /** An episode's length in its row. */
    fun minutesShort(minutes: Int) = "$minutes min"

    /** The label of the video line in the collapsed technical info, and the long-press entry on an episode. */
    val technicalVideo = "Video"

    /** An episode in a season's list: "4. Title". */
    fun episodeTitle(number: Int?, title: String) = if (number == null) title else "$number. $title"

    /** What a screen reader says for a season button, and for the row of them. */
    fun seasonDescription(name: String, selected: Boolean) = if (selected) "$name, selected" else name
    fun seasonsDescription(count: Int) = "Seasons, $count"

    /** In the season's list when it holds nothing, and when the show has no seasons. */
    val noEpisodes = "There are no episodes in this season yet."
    val noSeasons = "This show has no seasons yet."

    /** A track the file gives no name or language for. */
    fun track(index: Int) = "Track ${index + 1}"

    /** A time in a video as a clock: `4:07`, or `1:24:30` from an hour on. */
    fun clock(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val mm = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
        val ss = seconds.toString().padStart(2, '0')
        return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
    }

    /** How much of the video is left, shown opposite the elapsed time: `−1:12`, with a true minus sign (U+2212), not a hyphen. */
    fun timeLeft(positionMs: Long, durationMs: Long) = "\u2212" + clock((durationMs - positionMs).coerceAtLeast(0))

    /** What a screen reader says for the scrubber. */
    fun positionDescription(positionMs: Long, durationMs: Long) = "${clock(positionMs)} of ${clock(durationMs)}"

    // Settings
    val alwaysAskWhoIsWatchingTitle = "Always ask who's watching"
    val alwaysAskWhoIsWatchingHelp =
        "Show the profile screen every time the app opens instead of going straight to the last profile."
    val playbackStatsTitle = "Show playback stats"
    val playbackStatsHelp =
        "Show codec, bitrate and how the server is delivering the video in the player. Applies to this profile."
    val stillWatchingTitle = "Ask if you're still watching"
    val stillWatchingHelp =
        "Pause and ask after several episodes in a row so the next one doesn't play to an empty room. Applies to this profile."
    val stillWatchingOff = "Off"

    /** The question that pauses the player after several episodes in a row. */
    val stillWatchingPrompt = "Are you still watching?"
    val stillWatchingContinue = "Keep watching"
    val stillWatchingStop = "Stop watching"
    fun stillWatchingAfter(n: Int) = "After $n episodes"
}
