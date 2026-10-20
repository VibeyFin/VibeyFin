package com.liamgleeson.vibeyfin.ui.common

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

    /** The Resume button when the length isn't known. */
    val resume = "Resume"

    /** The Resume button: how much is left to watch, "Resume · 38m left" or "Resume · 1h 52m left". */
    fun resumeTimeLeft(remainingMs: Long): String {
        val minutes = ((remainingMs + 30_000) / 60_000).coerceAtLeast(1)
        val hours = minutes / 60
        val rest = minutes % 60
        val left = when {
            hours == 0L -> "${rest}m"
            rest == 0L -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
        return "Resume · $left left"
    }
    val pause = "Pause"
    val close = "Close"
    val playbackPosition = "Playback position"
    val audio = "Audio"
    val subtitles = "Subtitles"
    val subtitlesOff = "Off"
    val audioAndSubtitles = "Audio and subtitles"

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

    /** How much of the video is left, shown opposite the elapsed time: `-1:12`. */
    fun timeLeft(positionMs: Long, durationMs: Long) = "-" + clock((durationMs - positionMs).coerceAtLeast(0))

    /** What a screen reader says for the scrubber. */
    fun positionDescription(positionMs: Long, durationMs: Long) = "${clock(positionMs)} of ${clock(durationMs)}"

    // Settings
    val alwaysAskWhoIsWatchingTitle = "Always ask who's watching"
    val alwaysAskWhoIsWatchingHelp =
        "Show the profile screen every time the app opens instead of going straight to the last profile."
    val allowTranscodingTitle = "Allow video transcoding"
    val allowTranscodingHelp =
        "Let the server re-encode video your device can't play. This is hard on a small server. " +
            "When off, the server still repackages files and converts audio. Applies to this profile."
}
