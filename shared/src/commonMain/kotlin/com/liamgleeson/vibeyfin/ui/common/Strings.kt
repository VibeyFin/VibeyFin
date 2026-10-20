package com.liamgleeson.vibeyfin.ui.common

import com.liamgleeson.vibeyfin.domain.PlayMethod
import com.liamgleeson.vibeyfin.domain.SegmentType
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
    val libraries = "Libraries"
    val switchProfile = "Switch profile"
    val switchServer = "Switch server"
    val noLibraries = "This account has no libraries yet. Ask the server's admin to add one."
    val emptyFolder = "There's nothing in here yet."
    val loadMoreFailed = "Couldn't load the rest of this list. Check your connection and try again."

    fun greeting(name: String) = "Hi, $name"

    val continueWatching = "Continue watching"
    val markWatched = "Mark as watched"
    val markUnwatched = "Mark as unwatched"
    val details = "Details"

    /** In an item's facts, once the server says the user has watched it. */
    val watched = "Watched"

    /** What a screen reader says for the "…" menu on the item page. */
    val moreActions = "More actions"

    /** What a screen reader says while the Continue watching row is still loading. */
    val loadingContinueWatching = "Loading Continue watching"

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
    fun stillWatchingAfter(n: Int) = "After $n episodes"
}
