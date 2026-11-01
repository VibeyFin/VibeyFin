package app.vibeyfin.ui.common

import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SegmentType
import app.vibeyfin.domain.SubtitleMode
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrMediaType
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

    fun alreadySaved(addressIsNew: Boolean) = if (addressIsNew) {
        "You already have this server. This adds the new address to it."
    } else {
        "You already have this server at this address."
    }

    // Home and browsing
    val home = "Home"

    /** The header of a library's row of what was added most recently. */
    fun recentlyAddedIn(library: String) = "Recently added in $library"

    /** The header of the row that stands in for those when the libraries couldn't be listed. */
    val recentlyAdded = "Recently added"

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

    /** The Library page's extra filters. */
    val yearButton = "Year"
    val yearTitle = "Release year"

    fun yearChip(label: String) = "Year: $label"

    val allYears = "All years"
    val decade2020s = "2020s"
    val decade2010s = "2010s"
    val decade2000s = "2000s"
    val decade1990s = "1990s"
    val decade1980s = "1980s"
    val decadeOlder = "Before 1980"
    val peopleButton = "People"
    val peopleTitle = "People"

    fun peopleChip(name: String) = "With $name"

    val peopleSearchHint = "Search for an actor or a director"
    val peoplePrompt = "Type a name to find someone."
    val noPeopleFound = "Nobody by that name."
    val clearFilters = "Clear"

    /** "1,284 titles" under the Library's title. */
    fun libraryCount(count: Int): String {
        val digits = count.toString()
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return if (count == 1) "1 title" else "$grouped titles"
    }

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
    val emptyFolder = "There's nothing in here yet."
    val nothingMatchesFilters = "Nothing matches these filters. Try taking one off, or tap Clear."
    val loadMoreFailed = "Couldn't load the rest of this list. Check your connection and try again."

    val continueWatching = "Continue watching"

    /** The label of the Watched button under Play; its state is the tick. */
    val watchedAction = "Watched"
    val viewEpisode = "View episode"

    /** "S1 E4" on an episode's card; "E4" when the season is not known. */
    fun episodeNumber(number: Int) = "Episode $number"

    /** The tag at the start of a Seerr page's facts: where the title stands in one or two words. */
    fun seerrTag(state: RequestState): String = seerrStateLabel(state) ?: "Not in your library"

    fun episodeTag(season: Int?, episode: Int?): String? = when {
        season != null && episode != null -> "S$season E$episode"
        episode != null -> "E$episode"
        else -> null
    }

    /** "22 September 2015" from `2015-09-22`; null when it is not a date. */
    fun releaseDate(iso: String?): String? {
        val parts = iso?.split("-") ?: return null
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = parts.getOrNull(2)?.take(2)?.toIntOrNull() ?: return null
        return "$day ${MONTHS[month - 1]} $year"
    }

    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** A rating tag on a picture: "\u2605 8.1". */
    fun ratingTag(rating: Double): String = "\u2605 ${kotlin.math.round(rating * 10) / 10.0}"

    val markSeriesWatched = "Mark series as watched"
    val markSeriesUnwatched = "Mark series as unwatched"
    val markWatched = "Mark as watched"
    val markUnwatched = "Mark as unwatched"
    val details = "Details"

    /** In an item's facts, once the server says the user has watched it. */
    val watched = "Watched"

    /** What a screen reader offers for a long press on a poster, a card or a search result: it opens the poster menu. */
    val showOptions = "Show options"

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
    fun continueCardLabel(title: String, detail: String?, percentWatched: Int?) = listOfNotNull(title, detail, percentWatched?.let { "$it% watched" }).joinToString(", ")

    /** What a screen reader says for a poster: the title and, when known, the year. */
    fun cardLabel(title: String, year: Int?) = listOfNotNull(title, year?.toString()).joinToString(", ")

    // Playback
    val play = "Play"
    val startOver = "Start over"

    /** The card in the last seconds of an episode that offers the next one. */
    val upNext = "Up next"
    val playNow = "Play now"

    /** The player's icon button that starts the next episode now; its hint is the episode's label and title. */
    val nextEpisode = "Next episode"

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

    /** The level a swipe on the player set, for the indicator's screen-reader text: "Volume 60%". */
    fun volumeLevel(percent: Int) = "Volume $percent%"

    fun brightnessLevel(percent: Int) = "Brightness $percent%"

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
    fun statsBitrate(kbps: Int) = if (kbps < 1000) "$kbps kbps" else "${kbps / 1000}.${(kbps % 1000) / 100} Mbps"

    /** `12.3 s` of video buffered ahead. */
    fun statsBuffer(milliseconds: Long): String {
        val tenths = (milliseconds.coerceAtLeast(0) / 100)
        return "${tenths / 10}.${tenths % 10} s"
    }

    /** What sits between the parts of one meta line, such as the series name and the episode. */
    const val META_SEPARATOR = "  ·  "

    /** The player's one meta line: the [subtitle] then the picture [badge], what is there; null when neither is. */
    fun metaLine(subtitle: String?, badge: String?): String? = listOfNotNull(subtitle, badge).filter { it.isNotBlank() }.joinToString(META_SEPARATOR).ifEmpty { null }

    /** The facts under a title ("Show  ·  Offbeat  ·  Comedy"), on one line. */
    fun factsLine(facts: List<String>): String = facts.joinToString(META_SEPARATOR)

    /** A tag and its title on one line ("S1 E4 · Northbound"); the title alone when there is no tag. */
    fun taggedTitle(tag: String?, title: String): String = listOfNotNull(tag, title).joinToString(" · ")

    /** Up to two initials from the first words of a [name], for a picture that is not there. */
    fun initials(name: String): String = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

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

    /** Search. */
    val tabSearch = "Search"
    val searchHint = "Search movies, shows and people"
    val searchPrompt = "Find something to watch"
    val searchPromptHelp = "Type a title, a show or a name."
    val clearSearch = "Clear"

    fun featured(title: String) = "Featured: $title, opens its page"

    /** One page of the hero's carousel, [index] counted from 1. */
    fun featuredPage(title: String, index: Int, count: Int) = "Featured, $index of $count: $title, opens its page"

    val recentSearches = "Recent searches"
    val clearHistory = "Clear all"

    fun removeFromHistory(term: String) = "Remove $term from recent searches"

    fun noSearchResults(query: String) = "Nothing found for \u201C$query\u201D"

    val noSearchResultsHelp = "Check the spelling, or try fewer words."
    val searchAll = "All"
    val searchMovies = "Movies"
    val searchShows = "Shows"
    val searchEpisodes = "Episodes"
    val searchPeople = "People"
    val searchCollections = "Collections"

    fun searchResultDescription(title: String, subtitle: String?) = if (subtitle.isNullOrBlank()) title else "$title, $subtitle"

    /** A cast member's page. */
    fun born(year: Int) = "Born $year"

    val noAppearances = "Nothing in your library yet."
    val noBiography = "No biography on the server."

    fun personDescription(name: String, role: String?) = if (role.isNullOrBlank()) "$name, opens their page" else "$name as $role, opens their page"

    val extras = "Extras"

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

    /** The label of the line in the technical info that says how much storage the file takes. */
    val technicalSize = "Size"

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
    val playbackSection = "Playback"
    val skipBack = "Skip back"
    val skipForward = "Skip forward"

    fun skipSeconds(seconds: Int) = "$seconds seconds"

    fun skipBackLabel(seconds: Int) = "Back $seconds seconds"

    fun skipForwardLabel(seconds: Int) = "Forward $seconds seconds"

    fun seekFlash(seconds: Int) = "${seconds}s"

    val autoSkipSegments = "Auto skip intros and recaps"
    val autoSkipSegmentsHelp = "Jump past marked intros and recaps without asking."
    val bufferAhead = "Buffer ahead"
    val bufferAheadAuto = "Auto"

    fun bufferAheadChoice(seconds: Int) = if (seconds == 0) bufferAheadAuto else "$seconds seconds"

    val bufferAheadHelp =
        "How much video the player loads ahead of where you are. A longer buffer rides out a slow connection but uses more data and memory. Auto leaves it to the device's player."
    val skipHelp = "These set the player's buttons and the double-tap on the left and right of the picture."
    val heroMovie = "Movie"
    val heroShow = "Show"
    val homeHeroItemsTitle = "Featured titles on Home"
    val homeHeroItemsOff = "Off"

    fun homeHeroItemsCount(n: Int) = if (n == 1) "1 title" else "$n titles"

    val homeGenreRowsTitle = "Genre rows on Home"
    val homeGenreRowsHelp = "How many genres get a row on Home, the biggest first."
    val homeGenreRowsAll = "All genres"
    val homeGenreRowsNone = "None"

    fun homeGenreRowsTop(n: Int) = "Top $n"

    val stillWatchingTitle = "Ask if you're still watching"
    val stillWatchingHelp =
        "Pause and ask after several episodes in a row so the next one doesn't play to an empty room. Applies to this profile."
    val stillWatchingOff = "Off"

    /** The question that pauses the player after several episodes in a row. */
    val stillWatchingPrompt = "Are you still watching?"
    val stillWatchingContinue = "Keep watching"
    val stillWatchingStop = "Stop watching"

    fun stillWatchingAfter(n: Int) = "After $n episodes"

    // Settings pages, groups and rows (the data-driven Settings screen)
    val settingsRootTitle = "Settings"
    val settingsProfileCardHelp = "Profile, server and sign-out"

    val settingsGroupHome = "Home"
    val settingsGroupKids = "Kids and PIN"
    val settingsGroupDevice = "This device"
    val settingsGroupAdvanced = "Advanced"
    val settingsKidsFooter = "Applies to this profile only."
    val settingsDeviceHelp = "Otherwise the last profile reopens."
    val settingsAbout = "About VibeyFin"

    // Profile page
    val profilePageTitle = "Profile"
    val profileAccountGroup = "Account"
    val switchProfileHelp = "Asks for the PIN first if one is set."
    val profileServerGroup = "Server"
    val profileServerName = "Server"
    val profileServerAddress = "Address"
    val profileServerAlsoAt = "Also reachable at"
    val profileServerFooter = "Read only. Add or remove servers from \u201CWho\u2019s watching?\u201D."
    val signOutFooter =
        "Asks to confirm. Your tile stays in \u201CWho\u2019s watching?\u201D and asks for the password next time. Not shown on a kids profile."
    val signOutConfirmTitle = "Sign out of this profile?"
    val signOutConfirmMessage = "The downloads of this profile are deleted from this device. Your tile stays in \u201CWho\u2019s watching?\u201D and asks for the password next time."

    // Quality page
    val qualityTitle = "Quality"
    val qualityGroup = "Maximum quality"
    val qualityMaximum = "Maximum"
    val qualityMaximumHelp = "Original quality, direct play when possible."
    val qualityFooter =
        "The cap for everything this profile plays. Lower uses less data; the server may transcode to fit."

    /** The label of one stream-quality cap, given in bits per second; 0 is Maximum. Anything off the list reads as Maximum. */
    fun qualityLabel(bitsPerSecond: Int) = when (bitsPerSecond) {
        40_000_000 -> "4K \u00B7 40 Mbps"
        10_000_000 -> "1080p \u00B7 10 Mbps"
        4_000_000 -> "720p \u00B7 4 Mbps"
        1_500_000 -> "480p \u00B7 1.5 Mbps"
        else -> qualityMaximum
    }

    // Skipping and autoplay page
    val skippingTitle = "Skipping and autoplay"
    val skippingGroup = "Skipping"
    val skippingFooter =
        "Back: 5, 10, 15 or 30 s. Forward: 10, 15, 30, 45 or 60 s. These are the intervals the player's button glyphs come in, so the button and the setting agree."
    val autoSkipHelp = "Off by default. The Skip buttons show either way."
    val autoplayGroup = "Autoplay"
    val stillWatchingAfterTitle = "Still watching after"
    val stillWatchingAfterHelp = "Off, or after 2 to 5 episodes in a row."
    val pictureInPictureHelp =
        "On by default. The player offers picture in picture and starts it when you leave the app. Off, playback pauses when you leave."
    val swipeGesturesGroup = "Gestures"
    val swipeGestures = "Swipe for volume and brightness"
    val swipeGesturesHelp =
        "Swipe up or down on the right of the player to change the volume, on the left to change the brightness. The brightness goes back to normal when the player closes."
    val bufferingGroup = "Buffering"

    fun skipSecondsShort(seconds: Int) = "$seconds s"

    /** The root's one-line summary of the skipping page: "10 s · 10 s · Still watching after 3". */
    fun skippingSummary(back: Int, forward: Int, stillWatching: Int) = "${skipSecondsShort(back)} \u00B7 ${skipSecondsShort(forward)} \u00B7 " +
        if (stillWatching == 0) "Still watching off" else "Still watching after $stillWatching"

    // Audio and subtitles page: the preferred languages, kept on the Jellyfin account
    val audioLanguageTitle = "Audio language"
    val audioLanguageFooter = "Plays the track in this language when the video has one, otherwise the video\u2019s default track."
    val subtitleLanguageTitle = "Subtitle language"
    val subtitleModeTitle = "When subtitles show"
    val rememberTracksTitle = "Remember what I pick in the player"
    val rememberTracksHelp = "The next episode starts with the tracks you chose."
    val languagesFooter =
        "Saved to your Jellyfin account, so your other Jellyfin apps use them too. A track you pick in the player still wins for that video."
    val languagesLoading = "Reading your Jellyfin account\u2026"
    val anyLanguage = "Any language"
    val anyLanguageAudioHelp = "The video\u2019s default track."
    val anyLanguageSubtitleHelp = "Subtitles in whatever language the video has first."
    val languageListGroup = "Languages"
    val languageListFooter = "The languages your Jellyfin server knows, by the names it uses on tracks."
    val searchLanguages = "Search languages"
    val noLanguageMatches = "No language matches"
    val languagesDefaultsSummary = "Server defaults"

    /** The label of one subtitle mode. */
    fun subtitleModeLabel(mode: SubtitleMode) = when (mode) {
        SubtitleMode.Default -> "Default"
        SubtitleMode.Always -> "Always"
        SubtitleMode.OnlyForced -> "Only forced"
        SubtitleMode.Smart -> "Smart"
        SubtitleMode.None -> "Off"
    }

    /** What the mode in use does, under the Subtitles group. */
    fun subtitleModeFooter(mode: SubtitleMode) = when (mode) {
        SubtitleMode.Default -> "Default: subtitles the video marks as default or forced."
        SubtitleMode.Always -> "Always: whenever there are subtitles in your language."
        SubtitleMode.OnlyForced -> "Only forced: just the lines for foreign dialogue."
        SubtitleMode.Smart -> "Smart: only when the audio isn\u2019t in your language."
        SubtitleMode.None -> "Off: never, unless you turn them on in the player."
    }

    /**
     * The root's one-line summary of the languages: "German \u00B7 English subtitles always". [audio] and [subtitles] are language
     * names, null for no preference. Nothing set reads as the server's defaults.
     */
    fun languagesSummary(audio: String?, subtitles: String?, mode: SubtitleMode): String {
        if (audio == null && subtitles == null && mode == SubtitleMode.Default) return languagesDefaultsSummary
        val which = subtitles?.let { "$it subtitles" } ?: "subtitles"
        val subs = when (mode) {
            SubtitleMode.Default -> "$which as marked"
            SubtitleMode.Always -> "$which always"
            SubtitleMode.OnlyForced -> "$which only when forced"
            SubtitleMode.Smart -> "$which when needed"
            SubtitleMode.None -> "subtitles off"
        }
        return "${audio ?: anyLanguage} \u00B7 $subs"
    }

    // Subtitle style, on the Audio and subtitles page
    val captionsFollowSystemTitle = "Use system caption style"
    val captionsFollowSystemHelp = "Follows the OS caption settings."
    val captionsFollowSystemFooter = "Turn off to set your own below."
    val subtitleStyleGroup = "Subtitle style"
    val subtitleStyleIosNote = "Applies to text subtitles only; iOS burns subtitles in for now"
    val subtitleSizeTitle = "Size"
    val subtitleSizeSmall = "S"
    val subtitleSizeMedium = "M"
    val subtitleSizeLarge = "L"
    val subtitleSizeExtraLarge = "XL"
    val subtitleColorTitle = "Text"
    val subtitleColorWhite = "White"
    val subtitleColorYellow = "Yellow"
    val subtitleColorLightBlue = "Light blue"
    val subtitleBackgroundTitle = "Background"
    val subtitleBackgroundNone = "None"
    val subtitleBackgroundShadow = "Shadow"
    val subtitleBackgroundBox = "Box"
    val subtitleReset = "Reset to defaults"
    val subtitlePreviewSample = "This is how subtitles will look."

    // Kids and PIN page
    val kidsPinTitle = "Kids and PIN"
    val kidsProfileTitle = "Kids profile"
    val kidsProfileHelp = "Hides Advanced settings and signing out. The server's own limits still apply."
    val pinGroup = "PIN"
    val pinTitle = "PIN"
    val pinHelp = "4 digits."
    val pinFooter = "A household lock, not security. It keeps the next person on the sofa out of Settings and other profiles."
    val pinSet = "Set PIN"
    val pinChange = "Change PIN"
    val pinRemove = "Remove PIN"
    val pinAskGroup = "Ask for the PIN to"
    val pinAskOpenProfile = "Open this profile"
    val pinAskOpenSettings = "Open Settings"
    val pinAskSwitchProfile = "Switch profile"
    val serverPolicyGroup = "Set by your Jellyfin server"
    val serverPolicyRating = "Highest rating"
    val serverPolicyBlockedTags = "Blocked tags"
    val serverPolicyHours = "Allowed hours"
    val serverPolicyAnyTime = "Any time"
    val serverPolicyFooter = "Read only. An admin changes these in Jellyfin; the server enforces them."
    val serverPolicyLoading = "Checking your Jellyfin server\u2026"
    val serverPolicyFailed = "Couldn\u2019t read the limits from your Jellyfin server."
    val serverPolicyNone = "No limits are set on your Jellyfin server"
    val serverPolicyNoRating = "No limit"
    val serverPolicyNoTags = "None"
    val pinSetDescription = "A 4-digit PIN is set"

    /** A rating the server has no name for: "Level 10". Level 0 is the strictest cap, not "all". */
    fun serverPolicyLevel(level: Int) = "Level $level"

    /** One access window: "Weekdays 08:00 to 20:30", or "Saturdays 22:00 to 06:00 the next day" when it runs overnight. */
    fun serverPolicyWindow(days: String, startMinutes: Int, endMinutes: Int): String {
        val overnight = if (startMinutes > endMinutes) " the next day" else ""
        return "$days ${hhmm(startMinutes)} to ${hhmm(endMinutes)}$overnight"
    }

    private fun hhmm(minutes: Int) = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"

    val serverPolicyEveryday = "Every day"
    val serverPolicyWeekdays = "Weekdays"
    val serverPolicyWeekends = "Weekends"
    val serverPolicySundays = "Sundays"
    val serverPolicyMondays = "Mondays"
    val serverPolicyTuesdays = "Tuesdays"
    val serverPolicyWednesdays = "Wednesdays"
    val serverPolicyThursdays = "Thursdays"
    val serverPolicyFridays = "Fridays"
    val serverPolicySaturdays = "Saturdays"

    // PIN prompt
    val pinPromptEnter = "Enter PIN"
    val pinPromptNew = "Choose a PIN"
    val pinPromptConfirm = "Enter the PIN again"
    val pinPromptWrong = "That PIN is wrong. Try again."
    val pinPromptMismatch = "The PINs don't match. Try again."
    val pinPromptOpenProfile = "Enter this profile's PIN to open it."
    val pinPromptOpenSettings = "Enter your PIN to open Settings."
    val pinPromptSwitchProfile = "Enter your PIN to switch profile."
    val pinPromptChangePin = "Enter your current PIN to change it."
    val pinPromptRemovePin = "Enter your PIN to remove it."
    val pinPromptKidsOff = "Enter your PIN to turn off kids mode."

    fun pinPromptAttemptsLeft(attempts: Int) = if (attempts == 1) "That PIN is wrong. 1 try left before a short wait." else "That PIN is wrong. $attempts tries left before a short wait."

    fun pinPromptLocked(seconds: Int) = if (seconds == 1) "Too many wrong PINs. Try again in 1 second." else "Too many wrong PINs. Try again in $seconds seconds."

    val pinForgot = "Forgot PIN?"

    fun pinForgotMessage(name: String) = "Enter $name's Jellyfin password to remove the PIN. Kids mode stays as it is; the password is not saved."

    val pinForgotSubmit = "Remove PIN"

    /** The keypad's delete key, and the four dots read out by a screen reader. */
    val pinKeypadDelete = "Delete"

    fun pinDigitsEntered(count: Int) = if (count == 1) "1 of 4 digits entered" else "$count of 4 digits entered"

    /** A tile's lock badge when the profile asks for its PIN to open. */
    val needsPin = "Needs a PIN"

    // Notifications page
    val notificationsTitle = "Notifications"
    val notificationsOn = "On"
    val notificationsOff = "Off"
    val notificationsToggle = "Notify me about new episodes and movies"
    val notificationsWhichGroup = "Which shows"
    val notificationsFollowed = "Shows I follow and new movies"
    val notificationsEverything = "Everything new in the library"
    val notificationsBlocked = "Notifications are blocked for VibeyFin. Allow them in system settings, then switch this on again."

    fun notificationsScopeLabel(scope: Int) = if (scope == ProfileSettings.NOTIFY_EVERYTHING) notificationsEverything else notificationsFollowed

    val notificationsFooter =
        "Jellyfin sends no push, so the app checks in the background: expect a delay. Switching this on asks for notification permission."

    // New-title notifications: one per movie, one per episode. No name starts with "new": Kotlin/Native renames those for Swift.
    val addedMovieNotificationTitle = "New movie"

    /** The title of an episode's notification; [seriesName] is null when the server did not say which show. */
    fun addedEpisodeNotificationTitle(seriesName: String?) = if (seriesName.isNullOrBlank()) "New episode" else "New episode of $seriesName"

    /** The body: the title, plus whose it is when more than one profile on this device gets notifications. */
    fun addedTitleNotificationBody(title: String, profileName: String?) = if (profileName == null) title else "$title, for $profileName"

    // New-title notifications on Android: the channel, and the summary over several new episodes of one show
    val addedTitlesChannel = "New episodes and movies"
    val addedTitlesChannelDescription = "New episodes of the shows you follow and new movies, for each profile with notifications on"

    /** The summary over a show's episodes, which may come from several checks; [seriesName] is null when the server did not say which show. */
    fun addedEpisodesSummary(seriesName: String?) = if (seriesName.isNullOrBlank()) "New episodes" else "New episodes of $seriesName"

    /** The summary's body: the episodes this check found, plus whose they are as in [addedTitleNotificationBody]. */
    fun addedEpisodesSummaryBody(titles: List<String>, profileName: String?) = addedTitleNotificationBody(titles.joinToString(", "), profileName)

    // Downloads and offline mode
    val downloadsEmptyTitle = "Nothing downloaded yet"
    val downloadsEmptyHelp = "Download a movie or an episode to watch it without a connection."
    val downloaded = "Downloaded"
    val downloading = "Downloading"
    val converting = "Converting"

    fun downloadConverting(percent: Int) = "Converting for this device · $percent%"

    val downloadQueued = "Waiting to download"
    val downloadWaitingForWifi = "Waiting for Wi-Fi"
    val downloadWaitingForNetwork = "Waiting for a connection"
    val downloadWaitingForServer = "Waiting for the server"
    val downloadPaused = "Paused"
    val downloadFailed = "Download failed"
    val remove = "Remove"
    val removeDownload = "Remove download"
    val removeAllDownloads = "Remove all downloads"
    val downloadEpisode = "Download episode"
    val downloadSeason = "Download season"
    val downloadAllEpisodes = "Download all episodes"
    val downloadUnwatchedOnly = "Only episodes I haven't watched"

    /** A file size in the units people read: "850 MB", "1.4 GB". Decimal units, as the server and the system's storage screen use. */
    fun fileSize(bytes: Long): String {
        val kb = 1_000.0
        val mb = kb * 1_000
        val gb = mb * 1_000
        val value = bytes.coerceAtLeast(0).toDouble()
        return when {
            value >= gb -> "${tenths(value / gb)} GB"
            value >= mb -> "${(value / mb).roundToInt()} MB"
            value >= kb -> "${(value / kb).roundToInt()} KB"
            else -> "${value.toInt()} B"
        }
    }

    private fun tenths(value: Double): String {
        val rounded = (value * 10).roundToInt()
        return "${rounded / 10}.${rounded % 10}"
    }

    /** The line under a download that is coming in; [total] is null until the server has said how big the file is. */
    fun downloadProgress(done: Long, total: Long?) = if (total == null || total <= 0) fileSize(done) else "${fileSize(done)} of ${fileSize(total)}"

    fun downloadStorageUsed(bytes: Long) = "${fileSize(bytes)} used"

    /** Above how much the downloads take, in the storage card at the top of Downloads (Android). */
    val downloadsStorageHeading = "On this device"

    fun downloadConfirmTitle(episodes: Int) = if (episodes == 1) "Download 1 episode?" else "Download $episodes episodes?"

    fun downloadConfirmSize(bytes: Long) = "They take about ${fileSize(bytes)}."

    /** What a screen reader says for a download's state; the sighted see an icon or a ring. */
    fun downloadStateDescription(title: String, state: String) = "$title, $state"

    val offlineBannerTitle = "You're offline"
    val offlineBannerHelp = "Showing what you've downloaded."

    /** The toast when the app goes offline for want of a network. */
    val offlineToast = "You're offline. Showing what you've downloaded."

    /** The toast when Offline mode is switched on. */
    val offlineModeToast = "Offline mode is on. Showing what you've downloaded."
    val offlineByChoiceHelp = "Offline mode is on. Showing what you've downloaded."
    val goOnline = "Go online"
    val serverUnreachableBannerTitle = "Can't reach the server"
    val useOffline = "Use offline"
    val serverUnreachableBannerHelp = "You can still watch what you've downloaded."
    val offlineModeTitle = "Offline mode"
    val offlineModeHelp = "Use only your downloads, even when you're online."
    val offlineSearchHint = "Search your downloads"
    val offlineSearchPrompt = "Find something you've downloaded"
    val offlineSearchEmpty = "Nothing you've downloaded matches. Try another word."
    val downloadOverMobileTitle = "Download over mobile data"
    val downloadOverMobileHelp = "Off: downloads wait for Wi-Fi. Applies to this profile."
    val autoCleanWatchedTitle = "Remove downloads once watched"
    val autoCleanWatchedHelp = "Free the space when you finish a download. Applies to this profile."
    val storageLimitTitle = "Storage limit for downloads"
    val storageLimitHelp = "New downloads wait when the limit is reached. Applies to this profile."
    val storageLimitNone = "No limit"
    val purgeDownloadsTitle = "Delete downloaded files"
    val purgeDownloadsHelp = "Removes every download of this profile and any leftover files, also those shown in the Files app."
    val purgeDownloadsConfirmTitle = "Delete all downloaded files?"
    val purgeDownloadsConfirmMessage = "The downloads of this profile are deleted from this device. Other profiles keep theirs. You can download again at any time."
    val deleteFiles = "Delete"

    /** The notification that shows while downloads run in the background. */
    val downloadsChannelName = "Downloads"
    val downloadsNotificationIdle = "Downloading"

    fun downloadingNotificationTitle(title: String) = "Downloading $title"

    fun downloadsWaiting(count: Int) = if (count == 1) "1 more waiting" else "$count more waiting"

    // Downloads: the actions, the confirmations and the Downloads screen.
    fun downloadedWithSize(bytes: Long) = if (bytes > 0) "$downloaded · ${fileSize(bytes)}" else downloaded

    val pauseDownload = "Pause download"
    val resumeDownload = "Resume download"

    fun downloadSeasonNamed(season: String) = "Download $season"

    fun removeDownloadsOf(count: Int) = if (count == 1) "Remove 1 download" else "Remove $count downloads"

    fun downloadedCount(count: Int) = "$count downloaded"

    fun downloadActionDescription(title: String, state: String) = "Download $title, $state"

    val downloadConfirmAction = "Download"
    val downloadNothingNew = "Everything here is already downloaded."

    fun downloadConfirmSize(bytes: Long, episodes: Int) = if (bytes > 0) "${if (episodes == 1) "It takes" else "They take"} about ${fileSize(bytes)}." else null

    fun removeConfirmTitle(count: Int, title: String) = if (count == 1) "Remove $title?" else "Remove $count downloads?"

    fun removeConfirmBody(bytes: Long) = if (bytes > 0) "This frees ${fileSize(bytes)}. You can download it again later." else "You can download it again later."

    val removeConfirmAction = "Remove"

    fun showDownloadLine(episodes: Int, bytes: Long) = listOfNotNull(if (episodes == 1) "1 episode" else "$episodes episodes", if (bytes > 0) fileSize(bytes) else null).joinToString(" · ")

    fun seasonDownloadedCount(done: Int, total: Int) = "$done of $total downloaded"

    val downloadSeasonButton = "Download season"

    /** How long a download has left, from its speed: "Under 1m left", "7m left", "1h 20m left". */
    fun downloadTimeLeft(seconds: Long) = if (seconds < 60) "Under 1m left" else timeLeftShort(seconds * 1_000)

    val downloadLabelWaiting = "Waiting"
    val downloadLabelFailed = "Failed"

    fun downloadDescription(title: String) = "Download $title"

    fun storageLimitGb(gb: Int) = "$gb GB"

    // Seerr: connecting it in Settings. Errors are in SeerrMessages.
    val requestsSection = "Requests"
    val seerr = "Seerr"
    val seerrRowHelp = "Find and request titles that aren\u2019t in your library."
    val seerrNotSetUp = "Not set up"
    val seerrConnected = "Connected"
    val seerrOff = "Off"
    val seerrNotSignedIn = "Not signed in"
    val seerrLead = "Connect Seerr to find and request titles that aren\u2019t in your library yet."
    val seerrAddress = "Seerr address"
    val seerrAddressPlaceholder = "requests.example.org"
    val seerrAddressHelp = "Set up once for this server. Every profile signs in by itself, with no password."
    val seerrAddressSharedHelp = "Other profiles on this server use this address. Changing it changes it for them too."
    val seerrConnecting = "Connecting\u2026"
    val seerrSigningIn = "Signing in\u2026"
    val seerrServerSection = "Seerr"
    val seerrAddressRow = "Address"
    val seerrVersion = "Version"
    val seerrThisProfile = "This profile"

    fun seerrSignedInAs(name: String) = "Signed in as $name"

    val seerrSignedInAutomatically = "Signed in automatically"
    val seerrSignedInWithPassword = "Signed in with your password"
    val seerrSignInWithPassword = "Sign in with password"
    val seerrChangeAddress = "Change address"
    val seerrRequestsLeft = "Requests left"

    /** What is left of the quota, for the kinds that have one: "3 movies, 2 seasons". Seerr counts a show's requests in seasons. */
    fun seerrQuotaLeft(movies: Int?, seasons: Int?) = listOfNotNull(
        movies?.let { if (it == 1) "1 movie" else "$it movies" },
        seasons?.let { if (it == 1) "1 season" else "$it seasons" },
    ).joinToString(", ")

    /** How often the quota starts again, when Seerr says. */
    fun seerrQuotaPeriod(days: Int) = if (days == 1) {
        "Every day"
    } else if (days == 7) {
        "Every week"
    } else {
        "Every $days days"
    }

    val seerrDisconnect = "Disconnect Seerr"
    val seerrDisconnectHelp = "Signs this profile out of Seerr and hides it here. Other profiles on this server keep it. Your requests stay in Seerr."
    val seerrDisconnectConfirmTitle = "Disconnect Seerr?"
    val seerrDisconnectConfirmMessage = "This profile is signed out of Seerr and stops seeing it. Other profiles on this server keep it."
    val seerrDisconnectConfirm = "Disconnect"
    val seerrPasswordTitle = "Sign in to Seerr"
    val seerrPasswordReason = "Quick Connect is off on your Jellyfin server, so Seerr needs your Jellyfin password once."
    val seerrPasswordNotKept = "VibeyFin doesn\u2019t keep your password."
    val seerrUser = "User"

    // Seerr titles in search, and Seerr posters anywhere
    val seerrNotInLibrary = "Not in your library"
    val seerrNotInLibraryChip = "Not in library"

    fun libraryNoMatch(query: String) = "Nothing in your library matches \u201c$query\u201d."

    /** The line under a Seerr poster: "2025 · Movie". */
    fun seerrTitleMeta(year: Int?, type: SeerrMediaType) = listOfNotNull(year?.toString(), if (type == SeerrMediaType.Movie) "Movie" else "Show").joinToString(" \u00b7 ")

    /** The word on a Seerr poster's pill; null for a title nobody asked for, which shows the + disc instead. */
    fun seerrStateLabel(state: RequestState): String? = when (state) {
        RequestState.NotRequested -> null
        RequestState.Requested -> "Requested"
        RequestState.Approved -> "Approved"
        RequestState.PartlyAvailable -> "Partly available"
        RequestState.Available -> "Available"
        RequestState.Declined -> "Declined"
        RequestState.Failed -> "Failed"
    }

    // The Seerr page of a title, its poster menu and requesting. Errors are in SeerrMessages.
    val seerrRequest = "Request"
    val seerrRequestSeasons = "Request seasons"
    val seerrRequestAgain = "Request again"
    val seerrRequest4k = "Request in 4K"
    val seerrCancelRequest = "Cancel request"
    val seerrMoreLikeThis = "More like this"
    val seerrSeasons = "Seasons"
    val seerrAllMissingSeasons = "All missing seasons"
    val seerrQuality = "Quality"
    val seerrStandard = "Standard"
    val seerr4k = "4K"
    val seerrView = "View"
    val seerrRequestCancelled = "Request cancelled"

    /** A rating out of 10 in a facts line, the library's and Seerr's alike: "★ 8.7". */
    fun starRating(score: Double): String {
        val tenths = (score * 10 + 0.5).toInt()
        return "\u2605 ${tenths / 10}.${tenths % 10}"
    }

    fun seerrEpisodeCount(count: Int) = if (count == 1) "1 episode" else "$count episodes"

    /** A season's tile in the request sheet: its number, "SP" for Specials. */
    fun seerrSeasonTile(number: Int) = if (number == 0) "SP" else number.toString()

    /** The episode count under a season's tile: "10 ep". */
    fun seerrEpisodeCountShort(count: Int) = "$count ep"

    /** What a screen reader says for a season's tile: "Season 3, 10 episodes, Available". */
    fun seerrSeasonTileDescription(name: String, episodes: String, state: String?) = listOfNotNull(name, episodes, state).joinToString(", ")

    fun seerrRequestSeasonCount(count: Int) = if (count == 1) "Request 1 season" else "Request $count seasons"

    fun seerrPickerSubtitle(title: String, seasons: Int) = "$title \u00b7 ${seasonCount(seasons)}"

    /** The request sheet's subtitle for a movie: "Low Tide · 2025 · Movie". */
    fun seerrPickerMovieSubtitle(title: String, meta: String) = "$title \u00b7 $meta"

    /** A quality profile Seerr sent without a name, by its number: "Profile 4". */
    fun seerrQualityNumber(id: Int) = "Profile $id"

    /** What a screen reader says for a Seerr page's state tag and facts: "Requested, 2025, PG-13, 1 h 36 min". */
    fun seerrTagDescription(status: String?, facts: List<String>) = listOfNotNull(status, factsLine(facts).takeIf { facts.isNotEmpty() }).joinToString(", ")

    fun seerrRequested(title: String) = "Requested $title"

    /**
     * The line under a title's overview: where it stands, and who asked when someone did. [requestedBy] is the requester's name, or
     * null when it is the profile itself or not known; [byMe] says it is the profile.
     */
    fun seerrStatusLine(state: RequestState, byMe: Boolean, requestedBy: String?): String = when (state) {
        RequestState.NotRequested -> "Not in your library"

        RequestState.Requested -> when {
            byMe -> "Requested by you \u00b7 Waiting for approval"
            requestedBy != null -> "Requested by $requestedBy \u00b7 Waiting for approval"
            else -> "Requested \u00b7 Waiting for approval"
        }

        RequestState.Approved -> "Approved \u00b7 Added when the download finishes"

        RequestState.PartlyAvailable -> "Some seasons are in your library"

        RequestState.Available -> "In your library"

        RequestState.Declined -> "Declined \u00b7 You can ask again"

        RequestState.Failed -> "The download failed \u00b7 Seerr will try again"
    }

    /** The second status line, for a 4K request: "4K · Requested by you · Waiting for approval". */
    fun seerr4kLine(line: String) = "4K \u00b7 $line"

    /** Under a Seerr poster menu's title: "2025 · Movie · Not in your library", or the state's word. */
    fun seerrMenuSubtitle(meta: String, state: RequestState) = "$meta \u00b7 ${seerrStateLabel(state) ?: "Not in your library"}"

    /** Under the status row of a Seerr poster menu: "By you, 2 days ago · Waiting for approval". */
    fun seerrMenuStatusDetail(state: RequestState, byMe: Boolean, requestedBy: String?, daysAgo: Int?): String {
        val who = when {
            byMe -> "By you"
            requestedBy != null -> "By $requestedBy"
            else -> null
        }
        val whenAsked = daysAgo?.let {
            when (it) {
                0 -> "today"
                1 -> "yesterday"
                else -> "$it days ago"
            }
        }
        val asked = listOfNotNull(who, whenAsked).joinToString(", ").takeIf { it.isNotEmpty() }
        val next = when (state) {
            RequestState.Requested -> "Waiting for approval"
            RequestState.Approved -> "Added when the download finishes"
            RequestState.Failed -> "Seerr will try again"
            RequestState.Declined -> "You can ask again"
            RequestState.PartlyAvailable -> "Some seasons are in your library"
            RequestState.NotRequested, RequestState.Available -> null
        }
        return listOfNotNull(asked, next).joinToString(" \u00b7 ")
    }

    // Seerr on Home: My requests and the discover rows, and their pages
    val seerrMyRequests = "My requests"
    val seerrTrending = "Trending"
    val seerrPopularMovies = "Popular movies"
    val seerrPopularShows = "Popular shows"
    val seerrComingSoon = "Coming soon"
    val seerrNoRequests = "You haven\u2019t requested anything yet. Search for a title that isn\u2019t in your library to request it."
    val seerrFilterWaiting = "Waiting"
    val seerrFilterApproved = "Approved"
    val seerrFilterAvailable = "Available"
    val seerrFilterDeclined = "Declined"

    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** A release date ("2026-12-12") as Coming soon shows it: "Dec 12". Null when it can't be read. */
    fun seerrReleaseDay(date: String?): String? {
        val parts = date?.split("-")?.takeIf { it.size >= 3 } ?: return null
        val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = parts[2].take(2).toIntOrNull() ?: return null
        return "${monthNames[month - 1]} $day"
    }

    /** What a request is for: "Movie · 2022", "Show · Seasons 1–2", "Show · Season 3". */
    fun seerrRequestKind(type: SeerrMediaType, year: Int?, seasons: List<Int>): String = if (type == SeerrMediaType.Movie) {
        listOfNotNull("Movie", year?.toString()).joinToString(" \u00b7 ")
    } else {
        val sorted = seasons.sorted()
        val which = when {
            sorted.isEmpty() -> null
            sorted.size == 1 -> "Season ${sorted.first()}"
            sorted == (sorted.first()..sorted.last()).toList() -> "Seasons ${sorted.first()}\u2013${sorted.last()}"
            else -> "Seasons " + sorted.joinToString(", ")
        }
        listOfNotNull("Show", which).joinToString(" \u00b7 ")
    }

    /** When a request last changed, said as what happened: "Requested 2 days ago", "Approved yesterday", "Added to your library today". */
    fun seerrRequestWhen(state: RequestState, daysAgo: Int?): String {
        val what = when (state) {
            RequestState.Available, RequestState.PartlyAvailable -> "Added to your library"
            RequestState.Approved -> "Approved"
            RequestState.Declined -> "Declined"
            RequestState.Failed -> "Failed"
            RequestState.Requested, RequestState.NotRequested -> "Requested"
        }
        val whenText = when (daysAgo) {
            null -> null
            0 -> "today"
            1 -> "yesterday"
            in 2..6 -> "$daysAgo days ago"
            in 7..13 -> "last week"
            else -> "${daysAgo / 7} weeks ago"
        }
        return listOfNotNull(what, whenText).joinToString(" ")
    }

    /** What a screen reader says for a Seerr poster: "Harbour Bridge, 2025 · Movie, not in your library, can be requested". */
    fun seerrTitleDescription(title: String, meta: String, state: RequestState) = "$title, $meta, ${seerrStateLabel(state)?.lowercase() ?: "not in your library, can be requested"}"
}
