package app.vibeyfin.ui.common

import app.vibeyfin.domain.PlayMethod
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SegmentType
import app.vibeyfin.domain.SubtitleMode
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.i18n.Formats
import app.vibeyfin.i18n.plural
import app.vibeyfin.i18n.text
import kotlin.math.roundToInt
import kotlin.time.Duration

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
    val tryAgain: String get() = text("try_again")
    val cancel: String get() = text("cancel")
    val back: String get() = text("back")
    val searchAgain: String get() = text("search_again")
    val searching: String get() = text("searching")
    val addServer: String get() = text("add_server")
    val signIn: String get() = text("sign_in")
    val signingIn: String get() = text("signing_in")
    val signOut: String get() = text("sign_out")
    val settings: String get() = text("settings")

    // Who's watching
    val whoIsWatching: String get() = text("who_is_watching")
    val otherUser: String get() = text("other_user")
    val cantReachServerHelp: String get() = text("cant_reach_server_help")
    val username: String get() = text("username")
    val password: String get() = text("password")
    val signInAsAnotherUser: String get() = text("sign_in_as_another_user")
    val passwordNotKept: String get() = text("password_not_kept")
    val required: String get() = text("required")
    val showPassword: String get() = text("show_password")
    val hidePassword: String get() = text("hide_password")
    val useQuickConnectInstead: String get() = text("use_quick_connect_instead")
    val signInWithPassword: String get() = text("sign_in_with_password")

    fun signInAs(name: String): String = text("sign_in_as", "name" to name)

    fun cantReachServer(name: String): String = text("cant_reach_server", "name" to name)

    fun triedAddresses(addresses: List<String>): String = text("tried_addresses", "addresses" to addresses.joinToStringAnd())

    fun sessionEndedFor(name: String): String = text("session_ended_for", "name" to name)

    fun serverMenu(name: String): String = text("server_menu", "name" to name)

    fun moreFor(name: String): String = text("more_for", "name" to name)

    fun profileTile(name: String, needsPassword: Boolean, needsPin: Boolean, opening: Boolean): String = when {
        opening -> text("profile_tile_opening", "name" to name)
        needsPassword -> text("profile_tile_needs_password", "name" to name)
        needsPin -> text("profile_tile_needs_pin", "name" to name)
        else -> name
    }

    // Quick Connect
    val quickConnect: String get() = text("quick_connect")
    val quickConnectTile: String get() = text("quick_connect_tile")
    val quickConnectLead: String get() = text("quick_connect_lead")
    val quickConnectSteps: List<String> get() = listOf(
        text("quick_connect_step_open_profile"),
        text("quick_connect_step_choose"),
        text("quick_connect_step_enter_code"),
    )
    val quickConnectGettingCode: String get() = text("quick_connect_getting_code")
    val quickConnectApproved: String get() = text("quick_connect_approved")
    val quickConnectSigningIn: String get() = text("quick_connect_signing_in")
    val quickConnectNewCode: String get() = text("quick_connect_new_code")
    val quickConnectApproveHelp: String get() = text("quick_connect_approve_help")

    fun quickConnectApproveLead(name: String): String = text("quick_connect_approve_lead", "name" to name)

    val quickConnectCodeLabel: String get() = text("quick_connect_code_label")
    val quickConnectCodePlaceholder: String get() = text("quick_connect_code_placeholder")
    val quickConnectApprove: String get() = text("quick_connect_approve")
    val quickConnectApproving: String get() = text("quick_connect_approving")

    fun quickConnectApprovedFor(name: String): String = text("quick_connect_approved_for", "name" to name)

    val copyCode: String get() = text("copy_code")
    val codeCopied: String get() = text("code_copied")

    fun quickConnectCode(code: String): String = if (code.length == 6) code.take(3) + " " + code.drop(3) else code

    fun quickConnectSpokenCode(code: String): String = text("quick_connect_spoken_code", "code" to code.toList().joinToString(" "))

    fun quickConnectWaiting(expiresIn: Duration): String {
        val seconds = expiresIn.inWholeSeconds.coerceAtLeast(0)
        return text("quick_connect_waiting", "minutes" to (seconds / 60), "seconds" to (seconds % 60).toString().padStart(2, '0'))
    }

    // Servers
    val servers: String get() = text("servers")
    val onYourNetwork: String get() = text("on_your_network")
    val addToServers: String get() = text("add_to_servers")
    val noServersFoundHelp: String get() = text("no_servers_found_help")
    val noNewServersFound: String get() = text("no_new_servers_found")
    val lookingForServers: String get() = text("looking_for_servers")
    val welcomeLead: String get() = text("welcome_lead")
    val welcomeAddressHelp: String get() = text("welcome_address_help")
    val addServerByAddress: String get() = text("add_server_by_address")
    val serverDetails: String get() = text("server_details")
    val removeServer: String get() = text("remove_server")
    val addresses: String get() = text("addresses")
    val addAddress: String get() = text("add_address")
    val removeAddress: String get() = text("remove_address")
    val lastWorked: String get() = text("last_worked")
    val addressesHelp: String get() = text("addresses_help")
    val addressesSwipeHelp: String get() = text("addresses_swipe_help")
    val serverSection: String get() = text("server_section")
    val version: String get() = text("version")
    val profilesOnDevice: String get() = text("profiles_on_device")

    fun jellyfinVersion(version: String): String = text("jellyfin_version", "version" to version)

    fun serverMeta(version: String?, addresses: Int, profiles: Int): String = listOfNotNull(
        version?.let(::jellyfinVersion),
        text("server_meta_addresses", "count" to addresses).takeIf { addresses > 1 },
        when (profiles) {
            0 -> null
            else -> plural("server_meta_profiles", profiles)
        },
    ).joinToString(" · ")

    fun removeServerTitle(name: String): String = text("remove_server_title", "name" to name)

    fun removeServerMessage(profiles: Int): String = when (profiles) {
        0 -> text("remove_server_message_none")
        else -> plural("remove_server_message", profiles)
    }

    fun removeServerHelp(profiles: Int): String = when (profiles) {
        0 -> text("remove_server_message_none")
        else -> plural("remove_server_help", profiles)
    }

    // Add server
    val addAServer: String get() = text("add_a_server")
    val serverAddress: String get() = text("server_address")
    val serverAddressExample: String get() = text("server_address_example")
    val serverAddressHelp: String get() = text("server_address_help")

    fun addServerNext(name: String): String = text("add_server_next", "name" to name)

    val connect: String get() = text("connect")
    val connecting: String get() = text("connecting")
    val found: String get() = text("found")
    val saveAddress: String get() = text("save_address")
    val done: String get() = text("done")

    fun alsoReachableAt(addresses: List<String>): String = text("also_reachable_at", "addresses" to addresses.joinToString(", "))

    fun alreadySaved(addressIsNew: Boolean): String = if (addressIsNew) {
        text("already_saved_new_address")
    } else {
        text("already_saved_existing_address")
    }

    // Home and browsing
    val home: String get() = text("home")

    /** The header of a library's row of what was added most recently. */
    fun recentlyAddedIn(library: String): String = text("recently_added_in", "library" to library)

    /** The header of the row that stands in for those when the libraries couldn't be listed. */
    val recentlyAdded: String get() = text("recently_added")

    /** The action in a row's header that opens the whole list, and what a screen reader says for it. */
    val seeAll: String get() = text("see_all")

    fun seeAllIn(row: String): String = text("see_all_in", "row" to row)

    /** The main navigation bar: its labels, and the name a screen reader gives the bar. */
    val tabHome: String get() = text("tab_home")
    val tabLibrary: String get() = text("tab_library")
    val tabDownloads: String get() = text("tab_downloads")
    val mainNavigation: String get() = text("main_navigation")

    /** The category buttons under Home's header, in the order they are shown. */
    val filterShows: String get() = text("filter_shows")
    val filterMovies: String get() = text("filter_movies")
    val filterNew: String get() = text("filter_new")
    val filterCategories: String get() = text("filter_categories")

    /** What a screen reader says for the group of category buttons. */
    val filterHome: String get() = text("filter_home")

    /** What a screen reader says for a button that opens a list of genres. */
    fun opensList(label: String): String = text("opens_list", "label" to label)

    /** The Sort and Genre buttons on a category page, and the rows of their sheets. */
    val sortName: String get() = text("sort_name")
    val sortAdded: String get() = text("sort_added")
    val sortRating: String get() = text("sort_rating")
    val sortTitle: String get() = text("sort_title")
    val genreTitle: String get() = text("genre_title")
    val allGenres: String get() = text("all_genres")

    /** The Year and People filters. */
    val yearTitle: String get() = text("year_title")

    val allYears: String get() = text("all_years")
    val decade2020s: String get() = text("decade_2020s")
    val decade2010s: String get() = text("decade_2010s")
    val decade2000s: String get() = text("decade_2000s")
    val decade1990s: String get() = text("decade_1990s")
    val decade1980s: String get() = text("decade_1980s")
    val decadeOlder: String get() = text("decade_older")
    val peopleButton: String get() = text("people_button")
    val peopleTitle: String get() = text("people_title")

    fun peopleChip(name: String): String = text("people_chip", "name" to name)

    val peopleSearchHint: String get() = text("people_search_hint")
    val peoplePrompt: String get() = text("people_prompt")
    val noPeopleFound: String get() = text("no_people_found")
    val clearFilters: String get() = text("clear_filters")

    fun sortChip(label: String): String = text("sort_chip", "label" to label)

    /** What a screen reader says for a sheet row that holds [count] titles. */
    fun titleCount(name: String, count: Int): String = plural("title_count", count, "name" to name)

    /** More orders for the Sort sheet, the Order button beside it, and the Filters button with every other filter. */
    val sortCriticRating: String get() = text("sort_critic_rating")
    val sortReleaseDate: String get() = text("sort_release_date")
    val sortRuntime: String get() = text("sort_runtime")
    val sortLastPlayed: String get() = text("sort_last_played")
    val sortPlayCount: String get() = text("sort_play_count")
    val sortRandom: String get() = text("sort_random")
    val orderAToZ: String get() = text("order_a_to_z")
    val orderZToA: String get() = text("order_z_to_a")
    val orderNewestFirst: String get() = text("order_newest_first")
    val orderOldestFirst: String get() = text("order_oldest_first")
    val orderHighestFirst: String get() = text("order_highest_first")
    val orderLowestFirst: String get() = text("order_lowest_first")
    val orderShortestFirst: String get() = text("order_shortest_first")
    val orderLongestFirst: String get() = text("order_longest_first")

    /** What a screen reader says for the order in use in the Sort sheet. */
    fun sortRowDescription(label: String, direction: String): String = text("sort_row_description", "label" to label, "direction" to direction)

    val filtersButton: String get() = text("filters_button")
    val filtersTitle: String get() = text("filters_title")

    fun filtersChip(count: Int): String = text("filters_chip", "count" to Formats.current.integer(count.toLong()))

    val filterAny: String get() = text("filter_any")
    val filterOn: String get() = text("filter_on")
    val filterOff: String get() = text("filter_off")

    fun selectedCount(count: Int): String = plural("selected_count", count, "count" to count)

    val watchedTitle: String get() = text("watched_title")
    val watchedUnwatched: String get() = text("watched_unwatched")
    val watchedWatched: String get() = text("watched_watched")
    val watchedInProgress: String get() = text("watched_in_progress")
    val onlyMyList: String get() = text("only_my_list")
    val genreMatchTitle: String get() = text("genre_match_title")
    val genreMatchAny: String get() = text("genre_match_any")
    val genreMatchAll: String get() = text("genre_match_all")
    val yearFromTitle: String get() = text("year_from_title")
    val yearToTitle: String get() = text("year_to_title")

    fun yearRange(from: Int, to: Int): String = text("year_range", "from" to from, "to" to to)

    val communityRatingTitle: String get() = text("community_rating_title")

    fun ratingAtLeast(rating: Int): String = text("rating_at_least", "rating" to Formats.current.integer(rating.toLong()))

    val criticRatingTitle: String get() = text("critic_rating_title")

    fun criticRatingAtLeast(percent: Int): String = text("critic_rating_at_least", "percent" to Formats.current.integer(percent.toLong()))

    val ageRatingTitle: String get() = text("age_rating_title")

    fun ageRatingUpTo(rating: String): String = text("age_rating_up_to", "rating" to rating)

    val studioTitle: String get() = text("studio_title")
    val tagTitle: String get() = text("tag_title")
    val libraryTitle: String get() = text("library_title")
    val allLibraries: String get() = text("all_libraries")
    val runtimeTitle: String get() = text("runtime_title")

    fun runtimeUnder(minutes: Int): String = text("runtime_under", "minutes" to Formats.current.integer(minutes.toLong()))

    fun runtimeOver(minutes: Int): String = text("runtime_over", "minutes" to Formats.current.integer(minutes.toLong()))

    val addedTitle: String get() = text("added_title")
    val addedWeek: String get() = text("added_week")
    val addedMonth: String get() = text("added_month")
    val addedYear: String get() = text("added_year")
    val resolutionTitle: String get() = text("resolution_title")
    val qualityHd: String get() = text("quality_hd")
    val qualityUhd: String get() = text("quality_uhd")
    val qualityHdr: String get() = text("quality_hdr")
    val qualityDolbyVision: String get() = text("quality_dolby_vision")

    /** In the Categories sheet when the server has none. */
    val noCategories: String get() = text("no_categories")

    val switchProfile: String get() = text("switch_profile")
    val switchServer: String get() = text("switch_server")
    val emptyFolder: String get() = text("empty_folder")
    val nothingMatchesFilters: String get() = text("nothing_matches_filters")
    val loadMoreFailed: String get() = text("load_more_failed")

    val continueWatching: String get() = text("continue_watching")

    /** The label of the Watched button under Play; its state is the tick. */
    val watchedAction: String get() = text("watched_action")
    val viewEpisode: String get() = text("view_episode")

    /** "S1 E4" on an episode's card; "E4" when the season is not known. */
    fun episodeNumber(number: Int): String = text("episode_number", "number" to number)

    /** The tag at the start of a Seerr page's facts: where the title stands in one or two words. */
    fun seerrTag(state: RequestState): String = seerrStateLabel(state) ?: text("seerr_tag_not_in_library")

    fun episodeTag(season: Int?, episode: Int?): String? = when {
        season != null && episode != null -> text("episode_tag_season_episode", "season" to season, "episode" to episode)
        episode != null -> text("episode_tag_episode", "episode" to episode)
        else -> null
    }

    /** "22 September 2015" from `2015-09-22`; null when it is not a date. */
    fun releaseDate(iso: String?): String? {
        val parts = iso?.split("-") ?: return null
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = parts.getOrNull(2)?.take(2)?.toIntOrNull() ?: return null
        return text("release_date", "day" to day, "month" to Formats.current.monthName(month, false), "year" to year)
    }

    fun ratingTag(rating: Double): String = "★ ${Formats.current.decimal(kotlin.math.round(rating * 10) / 10.0, 1, 1)}"

    val markSeasonWatched: String get() = text("mark_season_watched")
    val markSeasonUnwatched: String get() = text("mark_season_unwatched")
    val markSeriesWatched: String get() = text("mark_series_watched")
    val markSeriesUnwatched: String get() = text("mark_series_unwatched")
    val markWatched: String get() = text("mark_watched")
    val markUnwatched: String get() = text("mark_unwatched")
    val removeFromContinueWatching: String get() = text("remove_from_continue_watching")
    val removedFromContinueWatching: String get() = text("removed_from_continue_watching")
    val undo: String get() = text("undo")
    val details: String get() = text("details")

    /** In an item's facts, once the server says the user has watched it. */
    val watched: String get() = text("watched")

    /** What a screen reader offers for a long press on a poster, a card or a search result: it opens the poster menu. */
    val showOptions: String get() = text("show_options")

    /** What a screen reader says for the "…" menu on the item page. */
    val moreActions: String get() = text("more_actions")

    /** What a screen reader says while the Continue watching row is still loading. */
    val loadingContinueWatching: String get() = text("loading_continue_watching")

    /** What a screen reader says while a screen shows grey placeholders instead of its content. */
    val loadingPage: String get() = text("loading_page")

    /**
     * What a screen reader says for a Continue watching card: the title, what it is (the episode, or
     * a movie's year) and, for something in progress, how far in the user is.
     */
    fun continueCardLabel(title: String, detail: String?, percentWatched: Int?): String = listOfNotNull(title, detail, percentWatched?.let { text("continue_card_percent_watched", "percent" to it) }).joinToString(", ")

    /** What a screen reader says for a poster: the title and, when known, the year. */
    fun cardLabel(title: String, year: Int?): String = listOfNotNull(title, year?.toString()).joinToString(", ")

    // Playback
    val play: String get() = text("play")
    val startOver: String get() = text("start_over")

    /** The card in the last seconds of an episode that offers the next one. */
    val upNext: String get() = text("up_next")
    val playNow: String get() = text("play_now")

    /** The player's icon button that starts the next episode now; its hint is the episode's label and title. */
    val nextEpisode: String get() = text("next_episode")

    /** The player's button that starts the episode before this one; its hint is that episode's label and title. */
    val previousEpisode: String get() = text("previous_episode")

    /** Dismisses the card and plays the episode out to the end of its credits. */
    val watchCredits: String get() = text("watch_credits")

    fun upNextIn(seconds: Int): String = text("up_next_in", "seconds" to seconds)

    /** The Resume button when the length isn't known. */
    val resume: String get() = text("resume")

    /** How much is left to watch: "38m left", or "1h 52m left" from an hour. Never under a minute. */
    fun timeLeftShort(remainingMs: Long): String {
        val minutes = ((remainingMs + 30_000) / 60_000).coerceAtLeast(1)
        val hours = minutes / 60
        val rest = minutes % 60
        val left = when {
            hours == 0L -> text("time_left_short_minutes", "rest" to rest)
            rest == 0L -> text("time_left_short_hours", "hours" to hours)
            else -> text("time_left_short_hours_minutes", "hours" to hours, "rest" to rest)
        }
        return text("time_left_short", "left" to left)
    }

    /** The Resume button: "Resume · 38m left". */
    fun resumeTimeLeft(remainingMs: Long): String = text("resume_time_left", "timeLeft" to timeLeftShort(remainingMs))

    val pause: String get() = text("pause")
    val close: String get() = text("close")
    val airplay: String get() = text("airplay")
    val playbackPosition: String get() = text("playback_position")
    val audio: String get() = text("audio")
    val subtitles: String get() = text("subtitles")
    val subtitlesOff: String get() = text("subtitles_off")
    val audioAndSubtitles: String get() = text("audio_and_subtitles")

    /** The player button that turns the video into a small window over other apps. */
    val pictureInPicture: String get() = text("picture_in_picture")

    /** The player's status while the video plays on another screen over AirPlay: the receiver's [device] name, or a generic one when the system gave none. */
    fun playingOn(device: String?): String = device?.trim()?.takeIf { it.isNotEmpty() }?.let { text("playing_on", "device" to it) } ?: text("playing_on_airplay")

    /** The level a swipe on the player set, for the indicator's screen-reader text: "Volume 60%". */
    fun volumeLevel(percent: Int): String = text("volume_level", "percent" to percent)

    fun brightnessLevel(percent: Int): String = text("brightness_level", "percent" to percent)

    /** The name of the Android notification channel for the media controls shown while a video plays. */
    val playbackNotificationChannel: String get() = text("playback_notification_channel")

    /** One spoken phrase for a label and its value, "Bitrate, 4.5 Mbps", where the two are drawn apart. */
    fun labelled(label: String, value: String): String = text("labelled", "label" to label, "value" to value)

    // Playback stats
    fun statsMethod(method: PlayMethod): String = when (method) {
        PlayMethod.DirectPlay -> text("stats_method_direct_play")
        PlayMethod.DirectStream -> text("stats_method_direct_stream")
        PlayMethod.Transcode -> text("stats_method_transcode")
    }

    /** How a stream plays, after its codec in the item screen's technical info: "HEVC (Direct play)". */
    fun playoutTag(method: PlayMethod): String = text("playout_tag", "method" to statsMethod(method))

    /** The server's transcode reason in plain words; an unknown one is its name, split into words. */
    fun transcodeReason(code: String): String = when (code) {
        "ContainerNotSupported" -> text("transcode_reason_container_not_supported")
        "VideoCodecNotSupported" -> text("transcode_reason_video_codec_not_supported")
        "AudioCodecNotSupported" -> text("transcode_reason_audio_codec_not_supported")
        "SubtitleCodecNotSupported" -> text("transcode_reason_subtitle_codec_not_supported")
        "VideoProfileNotSupported", "VideoLevelNotSupported" -> text("transcode_reason_video_profile_not_supported")
        "VideoRangeTypeNotSupported" -> text("transcode_reason_video_range_type_not_supported")
        "VideoResolutionNotSupported" -> text("transcode_reason_video_resolution_not_supported")
        "VideoBitDepthNotSupported" -> text("transcode_reason_video_bit_depth_not_supported")
        "VideoFramerateNotSupported" -> text("transcode_reason_video_framerate_not_supported")
        "AudioChannelsNotSupported" -> text("transcode_reason_audio_channels_not_supported")
        "AudioIsExternal", "SecondaryAudioNotSupported" -> text("transcode_reason_audio_is_external")
        "ContainerBitrateExceedsLimit", "VideoBitrateNotSupported", "AudioBitrateNotSupported" -> text("transcode_reason_container_bitrate_exceeds_limit")
        else -> code.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar(Char::uppercase)
    }

    val statsLabelMethod: String get() = text("stats_label_method")
    val statsLabelReasons: String get() = text("stats_label_reasons")
    val statsLabelEngine: String get() = text("stats_label_engine")
    val statsEngineAvPlayer: String get() = text("stats_engine_av_player")

    /** Aether, with how it shows the picture (the engine's own name for the route). */
    fun statsEngineAether(route: String): String = text("stats_engine_aether", "route" to route)

    val statsLabelVideo: String get() = text("stats_label_video")
    val statsLabelAudio: String get() = text("stats_label_audio")
    val statsLabelResolution: String get() = text("stats_label_resolution")
    val statsLabelFrameRate: String get() = text("stats_label_frame_rate")
    val statsLabelBitrate: String get() = text("stats_label_bitrate")
    val statsLabelDroppedFrames: String get() = text("stats_label_dropped_frames")
    val statsLabelThroughput: String get() = text("stats_label_throughput")
    val statsLabelBuffer: String get() = text("stats_label_buffer")

    /** Shown for a stat nobody can tell. */
    val statsUnavailable: String get() = text("stats_unavailable")

    /**
     * The short codec name the stats overlay shows ("h264", "eac3 atmos"), the same on both platforms.
     * [code] is what the player reports: a MIME type on Android (`video/avc`), a FourCC on iOS (`avc1`).
     * An unknown one is shown as the player gave it, without its `video/` or `x-` prefix; null for none.
     */
    fun codecName(code: String?): String? {
        val key = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return CODEC_NAMES[key] ?: key.substringAfter('/').removePrefix("x-")
    }

    private val CODEC_NAMES: Map<String, String> = mapOf(
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

    fun statsResolution(width: Int, height: Int): String = text("stats_resolution", "width" to width, "height" to height)

    /** `24 fps`, `23.98 fps`: two decimals at most, none when the rate is whole. */
    fun statsFrameRate(framesPerSecond: Float): String {
        val hundredths = (framesPerSecond * 100).roundToInt()
        return text("stats_frame_rate", "rate" to Formats.current.decimal(hundredths / 100.0, 0, 2))
    }

    /** `850 kbps` below a megabit, `4.5 Mbps` from there. Used for bitrate and throughput. */
    fun statsBitrate(kbps: Int): String = if (kbps < 1000) text("stats_bitrate_kbps", "kbps" to kbps) else text("stats_bitrate_mbps", "megabits" to Formats.current.decimal((kbps / 100) / 10.0, 1, 1))

    /** `12.3 s` of video buffered ahead. */
    fun statsBuffer(milliseconds: Long): String {
        val tenths = (milliseconds.coerceAtLeast(0) / 100)
        return text("stats_buffer", "seconds" to Formats.current.decimal(tenths / 10.0, 1, 1))
    }

    /** What sits between the parts of one meta line, such as the series name and the episode. */
    val META_SEPARATOR: String get() = text("meta_separator")

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
        SegmentType.Intro -> text("skip_intro")
        SegmentType.Outro -> text("skip_credits")
        SegmentType.Recap -> text("skip_recap")
        SegmentType.Preview -> text("skip_preview")
        SegmentType.Commercial -> text("skip_ad")
        SegmentType.Unknown -> ""
    }

    // Format badges
    val badgeDolbyVision: String get() = text("badge_dolby_vision")
    val badgeHdr10: String get() = text("badge_hdr10")
    val badgeHlg: String get() = text("badge_hlg")
    val badge4k: String get() = text("badge_4k")
    val badgeHd: String get() = text("badge_hd")

    val speed: String get() = text("speed")

    /** `1x`, `0.75x`, `1.5x`. */
    fun speedValue(speed: Float): String {
        val hundredths = (speed * 100).roundToInt()
        return text("speed_value", "number" to Formats.current.decimal(hundredths / 100.0, 0, 2))
    }

    /** The page of a movie, an episode or a show: its sections, and what the Play button says for a show. */
    val cast: String get() = text("cast")
    val inCollections: String get() = text("in_collections")

    /** The actions under Play on an item's page. My List is the server's favourites. */
    val myList: String get() = text("my_list")

    fun myListAction(onList: Boolean): String = if (onList) text("my_list_action_remove") else text("my_list_action_add")

    val trailer: String get() = text("trailer")

    /** The poster menu's greyed row while the server is still asked whether the title has a trailer. */
    val checkingTrailer: String get() = text("checking_trailer")
    val download: String get() = text("download")

    /** What a season's button says: always "Season 3"; the server's own names ("Series 3", "Season 3") are not used. 0 is Specials. */
    val specials: String get() = text("specials")

    fun season(number: Int): String = text("season", "number" to number)

    val moreLikeThis: String get() = text("more_like_this")
    val technicalInfo: String get() = text("technical_info")
    val more: String get() = text("more")
    val less: String get() = text("less")

    /** Search. */
    val tabSearch: String get() = text("tab_search")
    val searchHint: String get() = text("search_hint")
    val searchPrompt: String get() = text("search_prompt")
    val searchPromptHelp: String get() = text("search_prompt_help")
    val clearSearch: String get() = text("clear_search")

    fun featured(title: String): String = text("featured", "title" to title)

    /** One page of the hero's carousel, [index] counted from 1. */
    fun featuredPage(title: String, index: Int, count: Int): String = text("featured_page", "index" to index, "count" to count, "title" to title)

    val recentSearches: String get() = text("recent_searches")
    val clearHistory: String get() = text("clear_history")

    fun removeFromHistory(term: String): String = text("remove_from_history", "term" to term)

    fun noSearchResults(query: String): String = text("no_search_results", "query" to query)

    val noSearchResultsHelp: String get() = text("no_search_results_help")
    val searchAll: String get() = text("search_all")
    val searchMovies: String get() = text("search_movies")
    val searchShows: String get() = text("search_shows")
    val searchEpisodes: String get() = text("search_episodes")
    val searchPeople: String get() = text("search_people")
    val searchCollections: String get() = text("search_collections")

    fun searchResultDescription(title: String, subtitle: String?): String = if (subtitle.isNullOrBlank()) title else text("search_result_description", "title" to title, "subtitle" to subtitle)

    /** A cast member's page. */
    fun born(year: Int): String = text("born", "year" to year)

    val noAppearances: String get() = text("no_appearances")
    val noBiography: String get() = text("no_biography")

    fun personDescription(name: String, role: String?): String = if (role.isNullOrBlank()) text("person_description_without_role", "name" to name) else text("person_description_with_role", "name" to name, "role" to role)

    val extras: String get() = text("extras")

    /** The sub-headings of a collection's members. */
    val collectionMovies: String get() = text("collection_movies")
    val collectionShows: String get() = text("collection_shows")
    val collectionMore: String get() = text("collection_more")

    fun collectionMovieCount(count: Int): String = plural("collection_movie_count", count)

    fun collectionShowCount(count: Int): String = plural("collection_show_count", count)

    fun collectionTitles(count: Int): String = plural("collection_titles", count)

    fun seasonCount(count: Int): String = plural("season_count", count)

    fun playEpisode(episode: String): String = text("play_episode", "episode" to episode)

    fun resumeEpisodeFrom(episode: String, positionMs: Long): String = text("resume_episode_from", "episode" to episode, "position" to clock(positionMs))

    /** The link from an episode's or a season's page to its show: what a screen reader says for it. */
    fun goToSeries(name: String): String = text("go_to_series", "name" to name)

    /** An episode's length in its row. */
    fun minutesShort(minutes: Int): String = text("minutes_short", "minutes" to minutes)

    /** The label of the video line in the collapsed technical info, and the long-press entry on an episode. */
    val technicalVideo: String get() = text("technical_video")

    /** The label of the line in the technical info that says how much storage the file takes. */
    val technicalSize: String get() = text("technical_size")

    val technicalAudio: String get() = text("technical_audio")
    val technicalSubtitles: String get() = text("technical_subtitles")
    val trackForced: String get() = text("track_forced")
    val trackSdh: String get() = text("track_sdh")
    val trackExternal: String get() = text("track_external")

    /** What a screen reader says for a season button, and for the row of them. */
    fun seasonDescription(name: String, selected: Boolean): String = if (selected) text("season_description_selected", "name" to name) else name

    fun seasonsDescription(count: Int): String = text("seasons_description", "count" to count)

    /** In the season's list when it holds nothing, and when the show has no seasons. */
    val noEpisodes: String get() = text("no_episodes")
    val noSeasons: String get() = text("no_seasons")

    /** A track the file gives no name or language for. */
    fun track(index: Int): String = text("track", "number" to index + 1)

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
    fun timeLeft(positionMs: Long, durationMs: Long): String = "−" + clock((durationMs - positionMs).coerceAtLeast(0))

    /** What a screen reader says for the scrubber. */
    fun positionDescription(positionMs: Long, durationMs: Long): String = text("position_description", "position" to clock(positionMs), "duration" to clock(durationMs))

    // Settings
    val languageTitle: String get() = text("language_title")
    val languageDeviceDefault: String get() = text("language_device_default")
    val languageHelp: String get() = text("language_help")

    val alwaysAskWhoIsWatchingTitle: String get() = text("always_ask_who_is_watching_title")
    val playbackStatsTitle: String get() = text("playback_stats_title")
    val playbackSection: String get() = text("playback_section")
    val skipBack: String get() = text("skip_back")
    val skipForward: String get() = text("skip_forward")

    fun skipBackLabel(seconds: Int): String = text("skip_back_label", "seconds" to seconds)

    fun skipForwardLabel(seconds: Int): String = text("skip_forward_label", "seconds" to seconds)

    /** The seconds skipped so far by a run of double taps, signed by direction: "+30", "−30". */
    fun seekTotal(forward: Boolean, seconds: Int): String = if (forward) text("seek_forward_total", "seconds" to seconds) else text("seek_back_total", "seconds" to seconds)

    val autoSkipSegments: String get() = text("auto_skip_segments")
    val bufferAhead: String get() = text("buffer_ahead")
    val bufferAheadAuto: String get() = text("buffer_ahead_auto")

    val bufferAheadHelp: String get() = text("buffer_ahead_help")
    val heroMovie: String get() = text("hero_movie")
    val heroShow: String get() = text("hero_show")
    val homeHeroItemsTitle: String get() = text("home_hero_items_title")
    val homeHeroItemsOff: String get() = text("home_hero_items_off")

    fun homeHeroItemsCount(n: Int): String = plural("home_hero_items_count", n)

    val homeGenreRowsTitle: String get() = text("home_genre_rows_title")
    val homeGenreRowsAll: String get() = text("home_genre_rows_all")
    val homeGenreRowsNone: String get() = text("home_genre_rows_none")

    fun homeGenreRowsTop(n: Int): String = text("home_genre_rows_top", "count" to n)

    val showWatchedInRowsTitle: String get() = text("show_watched_in_rows_title")
    val showWatchedInRowsHelp: String get() = text("show_watched_in_rows_help")

    val stillWatchingOff: String get() = text("still_watching_off")

    /** The question that pauses the player after several episodes in a row. */
    val stillWatchingPrompt: String get() = text("still_watching_prompt")
    val stillWatchingContinue: String get() = text("still_watching_continue")
    val stillWatchingStop: String get() = text("still_watching_stop")

    fun stillWatchingAfter(n: Int): String = text("still_watching_after", "count" to n)

    // Settings pages, groups and rows (the data-driven Settings screen)
    val settingsRootTitle: String get() = text("settings_root_title")
    val settingsProfileCardHelp: String get() = text("settings_profile_card_help")

    val settingsGroupHome: String get() = text("settings_group_home")
    val settingsGroupKids: String get() = text("settings_group_kids")
    val settingsGroupDevice: String get() = text("settings_group_device")
    val settingsGroupAdvanced: String get() = text("settings_group_advanced")
    val settingsGroupDeveloper: String get() = text("settings_group_developer")
    val settingsKidsFooter: String get() = text("settings_kids_footer")
    val settingsDeviceHelp: String get() = text("settings_device_help")

    val settingsAbout: String get() = text("settings_about")

    // Profile page
    val profilePageTitle: String get() = text("profile_page_title")
    val profileAccountGroup: String get() = text("profile_account_group")
    val switchProfileHelp: String get() = text("switch_profile_help")
    val profileServerGroup: String get() = text("profile_server_group")
    val profileServerName: String get() = text("profile_server_name")
    val profileServerAddress: String get() = text("profile_server_address")
    val profileServerAlsoAt: String get() = text("profile_server_also_at")
    val profileServerFooter: String get() = text("profile_server_footer")
    val signOutFooter: String get() = text("sign_out_footer")
    val signOutConfirmTitle: String get() = text("sign_out_confirm_title")
    val signOutConfirmMessage: String get() = text("sign_out_confirm_message")

    // Quality page
    val qualityTitle: String get() = text("quality_title")
    val qualityGroup: String get() = text("quality_group")
    val qualityMaximum: String get() = text("quality_maximum")
    val qualityFooter: String get() = text("quality_footer")

    /** The label of one stream-quality cap, given in bits per second; 0 is Maximum. Anything off the list reads as Maximum. */
    fun qualityLabel(bitsPerSecond: Int): String = when (bitsPerSecond) {
        40_000_000 -> text("quality_label_4k")
        10_000_000 -> text("quality_label_1080p")
        4_000_000 -> text("quality_label_720p")
        1_500_000 -> text("quality_label_480p")
        else -> qualityMaximum
    }

    // Skipping and autoplay page
    val skippingTitle: String get() = text("skipping_title")
    val skippingGroup: String get() = text("skipping_group")
    val skippingFooter: String get() = text("skipping_footer")
    val autoSkipHelp: String get() = text("auto_skip_help")
    val autoplayGroup: String get() = text("autoplay_group")
    val stillWatchingAfterTitle: String get() = text("still_watching_after_title")
    val stillWatchingAfterHelp: String get() = text("still_watching_after_help")
    val pictureInPictureHelp: String get() = text("picture_in_picture_help")
    val swipeGesturesGroup: String get() = text("swipe_gestures_group")
    val swipeGestures: String get() = text("swipe_gestures")
    val swipeGesturesHelp: String get() = text("swipe_gestures_help")
    val bufferingGroup: String get() = text("buffering_group")
    val screenGroup: String get() = text("screen_group")
    val dimWhenPaused: String get() = text("dim_when_paused")
    val dimWhenPausedHelp: String get() = text("dim_when_paused_help")
    val dimWhenPausedNever: String get() = text("dim_when_paused_never")
    val playerScreenDimmed: String get() = text("player_screen_dimmed")

    fun skipSecondsShort(seconds: Int): String = text("skip_seconds_short", "seconds" to seconds)

    /** The root's one-line summary of the skipping page: "10 s · 10 s · Still watching after 3". */
    fun skippingSummary(back: Int, forward: Int, stillWatching: Int): String = "${skipSecondsShort(back)} · ${skipSecondsShort(forward)} · " +
        if (stillWatching == 0) text("skipping_summary_still_watching_off") else text("skipping_summary_still_watching_after", "count" to stillWatching)

    // Audio and subtitles page: the preferred languages, kept on the Jellyfin account
    val audioLanguageTitle: String get() = text("audio_language_title")
    val audioLanguageFooter: String get() = text("audio_language_footer")
    val subtitleLanguageTitle: String get() = text("subtitle_language_title")
    val subtitleModeTitle: String get() = text("subtitle_mode_title")
    val rememberTracksTitle: String get() = text("remember_tracks_title")
    val rememberTracksHelp: String get() = text("remember_tracks_help")
    val languagesFooter: String get() = text("languages_footer")
    val languagesLoading: String get() = text("languages_loading")
    val anyLanguage: String get() = text("any_language")
    val anyLanguageAudioHelp: String get() = text("any_language_audio_help")
    val anyLanguageSubtitleHelp: String get() = text("any_language_subtitle_help")
    val languageListGroup: String get() = text("language_list_group")
    val languageListFooter: String get() = text("language_list_footer")
    val searchLanguages: String get() = text("search_languages")
    val searchForSubtitles: String get() = text("search_for_subtitles")
    val subtitleSearchLanguage: String get() = text("subtitle_search_language")
    val subtitleMatch: String get() = text("subtitle_match")
    val noSubtitlesHint: String get() = text("no_subtitles_hint")

    fun noSubtitlesFound(language: String): String = text("no_subtitles_found", "language" to language)

    fun subtitlesAdded(language: String): String = text("subtitles_added", "language" to language)

    val deleteSubtitles: String get() = text("delete_subtitles")
    val deleteSubtitlesHelp: String get() = text("delete_subtitles_help")
    val deleteSubtitleText: String get() = text("delete_subtitle_text")
    val deleteSubtitleConfirm: String get() = text("delete_subtitle_confirm")

    fun deleteSubtitleTitle(name: String): String = text("delete_subtitle_title", "name" to name)

    fun deleteSubtitleDescription(name: String, fileName: String?): String = listOfNotNull(text("delete_subtitle_description", "name" to name), fileName).joinToString(", ")

    fun subtitleDeleted(name: String): String = text("subtitle_deleted", "name" to name)

    /** Provider, format and rating; what the server left out is left out here too. */
    fun subtitleResultDetails(provider: String?, format: String?, rating: Double?, possiblyAdded: Boolean = false): String {
        val stars = rating?.takeIf { it > 0 }?.let { "★ " + Formats.current.decimal((it * 10).roundToInt() / 10.0, 1, 1) }
        return listOfNotNull(provider, format?.uppercase(), stars, if (possiblyAdded) text("subtitle_result_details_possibly_added") else null).joinToString(" · ")
    }

    fun subtitleResultDescription(name: String, details: String, isMatch: Boolean, added: Boolean): String = listOfNotNull(name, details, if (isMatch) text("subtitle_result_description_matches") else null, if (added) text("subtitle_result_description_already_added") else null).joinToString(", ")

    val noLanguageMatches: String get() = text("no_language_matches")
    val languagesDefaultsSummary: String get() = text("languages_defaults_summary")

    /** The label of one subtitle mode. */
    fun subtitleModeLabel(mode: SubtitleMode): String = when (mode) {
        SubtitleMode.Default -> text("subtitle_mode_label_default")
        SubtitleMode.Always -> text("subtitle_mode_label_always")
        SubtitleMode.OnlyForced -> text("subtitle_mode_label_only_forced")
        SubtitleMode.Smart -> text("subtitle_mode_label_smart")
        SubtitleMode.None -> text("subtitle_mode_label_none")
    }

    /** What the mode in use does, under the Subtitles group. */
    fun subtitleModeFooter(mode: SubtitleMode): String = when (mode) {
        SubtitleMode.Default -> text("subtitle_mode_footer_default")
        SubtitleMode.Always -> text("subtitle_mode_footer_always")
        SubtitleMode.OnlyForced -> text("subtitle_mode_footer_only_forced")
        SubtitleMode.Smart -> text("subtitle_mode_footer_smart")
        SubtitleMode.None -> text("subtitle_mode_footer_none")
    }

    fun languagesSummary(audio: String?, subtitles: String?, mode: SubtitleMode): String {
        if (audio == null && subtitles == null && mode == SubtitleMode.Default) return languagesDefaultsSummary
        val subs = when (mode) {
            SubtitleMode.Default -> subtitles?.let { text("languages_summary_named_subs_default", "name" to it) } ?: text("languages_summary_any_subs_default")
            SubtitleMode.Always -> subtitles?.let { text("languages_summary_named_subs_always", "name" to it) } ?: text("languages_summary_any_subs_always")
            SubtitleMode.OnlyForced -> subtitles?.let { text("languages_summary_named_subs_only_forced", "name" to it) } ?: text("languages_summary_any_subs_only_forced")
            SubtitleMode.Smart -> subtitles?.let { text("languages_summary_named_subs_smart", "name" to it) } ?: text("languages_summary_any_subs_smart")
            SubtitleMode.None -> text("languages_summary_subs_none")
        }
        return "${audio ?: anyLanguage} · $subs"
    }

    // Subtitle style, on the Audio and subtitles page
    val captionsFollowSystemTitle: String get() = text("captions_follow_system_title")
    val captionsFollowSystemHelp: String get() = text("captions_follow_system_help")
    val captionsFollowSystemFooter: String get() = text("captions_follow_system_footer")
    val subtitleStyleGroup: String get() = text("subtitle_style_group")
    val subtitleStyleIosNote: String get() = text("subtitle_style_ios_note")
    val subtitleSizeTitle: String get() = text("subtitle_size_title")
    val subtitleSizeSmall: String get() = text("subtitle_size_small")
    val subtitleSizeMedium: String get() = text("subtitle_size_medium")
    val subtitleSizeLarge: String get() = text("subtitle_size_large")
    val subtitleSizeExtraLarge: String get() = text("subtitle_size_extra_large")
    val subtitleColorTitle: String get() = text("subtitle_color_title")
    val subtitleColorWhite: String get() = text("subtitle_color_white")
    val subtitleColorYellow: String get() = text("subtitle_color_yellow")
    val subtitleColorLightBlue: String get() = text("subtitle_color_light_blue")
    val subtitleBackgroundTitle: String get() = text("subtitle_background_title")
    val subtitleBackgroundNone: String get() = text("subtitle_background_none")
    val subtitleBackgroundShadow: String get() = text("subtitle_background_shadow")
    val subtitleBackgroundBox: String get() = text("subtitle_background_box")
    val subtitleReset: String get() = text("subtitle_reset")
    val subtitlePreviewSample: String get() = text("subtitle_preview_sample")

    // Kids and PIN page
    val kidsPinTitle: String get() = text("kids_pin_title")
    val kidsProfileTitle: String get() = text("kids_profile_title")
    val kidsProfileHelp: String get() = text("kids_profile_help")
    val pinGroup: String get() = text("pin_group")
    val pinTitle: String get() = text("pin_title")
    val pinHelp: String get() = text("pin_help")
    val pinFooter: String get() = text("pin_footer")
    val pinSet: String get() = text("pin_set")
    val pinChange: String get() = text("pin_change")
    val pinRemove: String get() = text("pin_remove")
    val pinAskGroup: String get() = text("pin_ask_group")
    val pinAskOpenProfile: String get() = text("pin_ask_open_profile")
    val pinAskOpenSettings: String get() = text("pin_ask_open_settings")
    val pinAskSwitchProfile: String get() = text("pin_ask_switch_profile")
    val serverPolicyGroup: String get() = text("server_policy_group")
    val serverPolicyRating: String get() = text("server_policy_rating")
    val serverPolicyBlockedTags: String get() = text("server_policy_blocked_tags")
    val serverPolicyHours: String get() = text("server_policy_hours")
    val serverPolicyAnyTime: String get() = text("server_policy_any_time")
    val serverPolicyFooter: String get() = text("server_policy_footer")
    val serverPolicyLoading: String get() = text("server_policy_loading")
    val serverPolicyFailed: String get() = text("server_policy_failed")
    val serverPolicyNone: String get() = text("server_policy_none")
    val serverPolicyNoRating: String get() = text("server_policy_no_rating")
    val serverPolicyNoTags: String get() = text("server_policy_no_tags")
    val pinSetDescription: String get() = text("pin_set_description")

    /** A rating the server has no name for: "Level 10". Level 0 is the strictest cap, not "all". */
    fun serverPolicyLevel(level: Int): String = text("server_policy_level", "level" to level)

    fun serverPolicyWindow(days: String, startMinutes: Int, endMinutes: Int): String {
        val start: String = hhmm(startMinutes)
        val end: String = hhmm(endMinutes)
        return if (startMinutes > endMinutes) {
            text("server_policy_window_next_day", "days" to days, "start" to start, "end" to end)
        } else {
            text("server_policy_window", "days" to days, "start" to start, "end" to end)
        }
    }

    private fun hhmm(minutes: Int): String = Formats.current.time(minutes).replace('\u202f', ' ').replace('\u00a0', ' ')

    val serverPolicyEveryday: String get() = text("server_policy_everyday")
    val serverPolicyWeekdays: String get() = text("server_policy_weekdays")
    val serverPolicyWeekends: String get() = text("server_policy_weekends")
    val serverPolicySundays: String get() = text("server_policy_sundays")
    val serverPolicyMondays: String get() = text("server_policy_mondays")
    val serverPolicyTuesdays: String get() = text("server_policy_tuesdays")
    val serverPolicyWednesdays: String get() = text("server_policy_wednesdays")
    val serverPolicyThursdays: String get() = text("server_policy_thursdays")
    val serverPolicyFridays: String get() = text("server_policy_fridays")
    val serverPolicySaturdays: String get() = text("server_policy_saturdays")

    // PIN prompt
    val pinPromptEnter: String get() = text("pin_prompt_enter")
    val pinPromptNew: String get() = text("pin_prompt_new")
    val pinPromptConfirm: String get() = text("pin_prompt_confirm")
    val pinPromptWrong: String get() = text("pin_prompt_wrong")
    val pinPromptMismatch: String get() = text("pin_prompt_mismatch")
    val pinPromptOpenProfile: String get() = text("pin_prompt_open_profile")
    val pinPromptOpenSettings: String get() = text("pin_prompt_open_settings")
    val pinPromptSwitchProfile: String get() = text("pin_prompt_switch_profile")
    val pinPromptChangePin: String get() = text("pin_prompt_change_pin")
    val pinPromptRemovePin: String get() = text("pin_prompt_remove_pin")
    val pinPromptKidsOff: String get() = text("pin_prompt_kids_off")

    fun pinPromptAttemptsLeft(attempts: Int): String = plural("pin_prompt_attempts_left", attempts)

    fun pinPromptLocked(seconds: Int): String = plural("pin_prompt_locked", seconds)

    val pinForgot: String get() = text("pin_forgot")

    fun pinForgotMessage(name: String): String = text("pin_forgot_message", "name" to name)

    val pinForgotSubmit: String get() = text("pin_forgot_submit")

    /** The keypad's delete key, and the four dots read out by a screen reader. */
    val pinKeypadDelete: String get() = text("pin_keypad_delete")

    fun pinDigitsEntered(count: Int): String = plural("pin_digits_entered", count)

    // Notifications page
    val notificationsTitle: String get() = text("notifications_title")
    val notificationsOn: String get() = text("notifications_on")
    val notificationsToggle: String get() = text("notifications_toggle")
    val notificationsWhichGroup: String get() = text("notifications_which_group")
    val notificationsFollowed: String get() = text("notifications_followed")
    val notificationsEverything: String get() = text("notifications_everything")
    val notificationsBlocked: String get() = text("notifications_blocked")

    fun notificationsScopeLabel(scope: Int): String = if (scope == ProfileSettings.NOTIFY_EVERYTHING) notificationsEverything else notificationsFollowed

    val downloadNotificationsToggle: String get() = text("download_notifications_toggle")
    val downloadNotificationsFooter: String get() = text("download_notifications_footer")
    val notificationsFooter: String get() = text("notifications_footer")

    // New-title notifications: one per movie, one per episode. No name starts with "new": Kotlin/Native renames those for Swift.
    val addedMovieNotificationTitle: String get() = text("added_movie_notification_title")

    /** The title of an episode's notification; [seriesName] is null when the server did not say which show. */
    fun addedEpisodeNotificationTitle(seriesName: String?): String = if (seriesName.isNullOrBlank()) text("added_episode_notification_title") else text("added_episode_notification_title_of_series", "series" to seriesName)

    /** The body: the title, plus whose it is when more than one profile on this device gets notifications. */
    fun addedTitleNotificationBody(title: String, profileName: String?): String = if (profileName == null) title else text("added_title_notification_body_for_profile", "title" to title, "profile" to profileName)

    // New-title notifications on Android: the channel, and the summary over several new episodes of one show
    val addedTitlesChannel: String get() = text("added_titles_channel")
    val addedTitlesChannelDescription: String get() = text("added_titles_channel_description")

    /** The summary over a show's episodes, which may come from several checks; [seriesName] is null when the server did not say which show. */
    fun addedEpisodesSummary(seriesName: String?): String = if (seriesName.isNullOrBlank()) text("added_episodes_summary") else text("added_episodes_summary_of_series", "series" to seriesName)

    /** The summary's body: the episodes this check found, plus whose they are as in [addedTitleNotificationBody]. */
    fun addedEpisodesSummaryBody(titles: List<String>, profileName: String?): String = addedTitleNotificationBody(titles.joinToString(", "), profileName)

    // Downloads and offline mode
    val downloadsEmptyTitle: String get() = text("downloads_empty_title")
    val downloadsEmptyHelp: String get() = text("downloads_empty_help")
    val downloaded: String get() = text("downloaded")
    val downloading: String get() = text("downloading")

    val downloadQueued: String get() = text("download_queued")
    val downloadWaitingForWifi: String get() = text("download_waiting_for_wifi")
    val downloadWaitingForNetwork: String get() = text("download_waiting_for_network")
    val downloadWaitingForServer: String get() = text("download_waiting_for_server")
    val downloadPaused: String get() = text("download_paused")
    val downloadFailed: String get() = text("download_failed")
    val remove: String get() = text("remove")
    val removeDownload: String get() = text("remove_download")
    val removeAllDownloads: String get() = text("remove_all_downloads")
    val downloadEpisode: String get() = text("download_episode")
    val downloadSeason: String get() = text("download_season")
    val downloadAllEpisodes: String get() = text("download_all_episodes")
    val downloadUnwatchedOnly: String get() = text("download_unwatched_only")

    /** A file size in the units people read: "850 MB", "1.4 GB". Decimal units, as the server and the system's storage screen use. */
    fun fileSize(bytes: Long): String {
        val kb = 1_000.0
        val mb = kb * 1_000
        val gb = mb * 1_000
        val value = bytes.coerceAtLeast(0).toDouble()
        return when {
            value >= gb -> text("file_size_gb", "size" to tenths(value / gb))
            value >= mb -> text("file_size_mb", "size" to (value / mb).roundToInt())
            value >= kb -> text("file_size_kb", "size" to (value / kb).roundToInt())
            else -> text("file_size_b", "size" to value.toInt())
        }
    }

    private fun tenths(value: Double): String {
        val rounded = (value * 10).roundToInt()
        return Formats.current.decimal(rounded / 10.0, 1, 1)
    }

    /** The line under a download that is coming in; [total] is null until the server has said how big the file is. */
    fun downloadProgress(done: Long, total: Long?): String = if (total == null || total <= 0) fileSize(done) else text("download_progress_of", "done" to fileSize(done), "total" to fileSize(total))

    fun downloadStorageUsed(bytes: Long): String = text("download_storage_used", "size" to fileSize(bytes))

    /** Above how much the downloads take, in the storage card at the top of Downloads (Android). */
    val downloadsStorageHeading: String get() = text("downloads_storage_heading")

    fun downloadConfirmTitle(episodes: Int): String = plural("download_confirm_title", episodes)

    fun downloadConfirmSize(bytes: Long): String = text("download_confirm_size", "size" to fileSize(bytes))

    /** What a screen reader says for a download's state; the sighted see an icon or a ring. */
    fun downloadStateDescription(title: String, state: String): String = text("download_state_description", "title" to title, "state" to state)

    val offlineBannerHelp: String get() = text("offline_banner_help")

    /** The toast when the app goes offline for want of a network. */
    val offlineToast: String get() = text("offline_toast")

    /** The toast when Offline mode is switched on. */
    val offlineModeToast: String get() = text("offline_mode_toast")
    val goOnline: String get() = text("go_online")
    val serverUnreachableBannerTitle: String get() = text("server_unreachable_banner_title")
    val useOffline: String get() = text("use_offline")
    val serverUnreachableBannerHelp: String get() = text("server_unreachable_banner_help")
    val offlineModeTitle: String get() = text("offline_mode_title")
    val offlineModeHelp: String get() = text("offline_mode_help")
    val offlineSearchHint: String get() = text("offline_search_hint")
    val offlineSearchPrompt: String get() = text("offline_search_prompt")
    val offlineSearchEmpty: String get() = text("offline_search_empty")
    val downloadOverMobileTitle: String get() = text("download_over_mobile_title")
    val downloadOverMobileHelp: String get() = text("download_over_mobile_help")
    val storageLimitTitle: String get() = text("storage_limit_title")
    val storageLimitHelp: String get() = text("storage_limit_help")
    val storageLimitNone: String get() = text("storage_limit_none")
    val purgeDownloadsTitle: String get() = text("purge_downloads_title")
    val purgeDownloadsHelp: String get() = text("purge_downloads_help")
    val purgeDownloadsConfirmTitle: String get() = text("purge_downloads_confirm_title")
    val purgeDownloadsConfirmMessage: String get() = text("purge_downloads_confirm_message")
    val deleteFiles: String get() = text("delete_files")

    /** The notification that shows while downloads run in the background. */
    val downloadsChannelName: String get() = text("downloads_channel_name")
    val downloadsNotificationIdle: String get() = text("downloads_notification_idle")

    fun downloadingNotificationTitle(title: String): String = text("downloading_notification_title", "title" to title)

    /** The notifications for downloads that finished or failed while the app was away. */
    fun downloadedTitle(title: String): String = text("downloaded_title", "title" to title)

    fun downloadedEpisodes(count: Int, show: String): String = text("downloaded_episodes", "count" to count, "show" to show)

    fun downloadedVideos(count: Int): String = text("downloaded_videos", "count" to count)

    val downloadsReadyBody: String get() = text("downloads_ready_body")

    fun downloadFailedTitle(title: String): String = text("download_failed_title", "title" to title)

    /** The second line of the system progress: how much of the video in hand is here, "1.23 GB of 4.50 GB"; just the first part while the size is unknown. */
    fun downloadSizeProgress(done: Long, total: Long?): String = if (total != null && total > 0) text("download_progress_of", "done" to preciseFileSize(done), "total" to preciseFileSize(total)) else preciseFileSize(done)

    /** A file size with two decimals, in MB below a gigabyte: "512.34 MB", "1.23 GB". Decimal units, like [fileSize]. */
    fun preciseFileSize(bytes: Long): String {
        val value = bytes.coerceAtLeast(0)
        return if (value >= GIGABYTE) text("file_size_gb", "size" to hundredths(value, GIGABYTE)) else text("file_size_mb", "size" to hundredths(value, MEGABYTE))
    }

    private fun hundredths(value: Long, unit: Long): String {
        val rounded = (value * 100 + unit / 2) / unit
        return Formats.current.decimal(rounded / 100.0, 2, 2)
    }

    private const val MEGABYTE: Long = 1_000_000
    private const val GIGABYTE: Long = 1_000_000_000

    fun downloadsWaiting(count: Int): String = plural("downloads_waiting", count)

    // Downloads: the actions, the confirmations and the Downloads screen.

    val pauseDownload: String get() = text("pause_download")
    val resumeDownload: String get() = text("resume_download")
    val retryDownload: String get() = text("retry_download")
    val resumeAllDownloads: String get() = text("resume_all_downloads")
    val retryFailedDownloads: String get() = text("retry_failed_downloads")

    fun downloadSeasonNamed(season: String): String = text("download_season_named", "season" to season)

    fun removeDownloadsOf(count: Int): String = plural("remove_downloads_of", count)

    fun downloadedCount(count: Int): String = text("downloaded_count", "count" to count)

    fun downloadActionDescription(title: String, state: String): String = text("download_action_description", "title" to title, "state" to state)

    val downloadConfirmAction: String get() = text("download_confirm_action")
    val downloadNothingNew: String get() = text("download_nothing_new")

    fun downloadConfirmSize(bytes: Long, episodes: Int): String? = if (bytes > 0) plural("download_confirm_size_about", episodes, "size" to fileSize(bytes)) else null

    fun removeConfirmTitle(count: Int, title: String): String = plural("remove_confirm_title", count, "title" to title)

    fun removeConfirmBody(bytes: Long): String = if (bytes > 0) text("remove_confirm_body_with_size", "size" to fileSize(bytes)) else text("remove_confirm_body")

    val removeConfirmAction: String get() = text("remove_confirm_action")

    fun showDownloadLine(episodes: Int, bytes: Long): String = listOfNotNull(plural("show_download_line_episodes", episodes), if (bytes > 0) fileSize(bytes) else null).joinToString(" · ")

    fun seasonDownloadedCount(done: Int, total: Int): String = text("season_downloaded_count", "done" to done, "total" to total)

    val downloadSeasonButton: String get() = text("download_season")

    /** How long a download has left, from its speed: "Under 1m left", "7m left", "1h 20m left". */
    fun downloadTimeLeft(seconds: Long): String = if (seconds < 60) text("download_time_left_under_minute") else timeLeftShort(seconds * 1_000)

    val downloadLabelWaiting: String get() = text("download_label_waiting")
    val downloadLabelFailed: String get() = text("download_label_failed")

    fun downloadDescription(title: String): String = text("download_description", "title" to title)

    fun storageLimitGb(gb: Int): String = text("storage_limit_gb", "gigabytes" to gb)

    // Seerr: connecting it in Settings. Errors are in SeerrMessages.
    val requestsSection: String get() = text("requests_section")
    val seerr: String get() = text("seerr")
    val seerrRowHelp: String get() = text("seerr_row_help")
    val seerrNotSetUp: String get() = text("seerr_not_set_up")
    val seerrConnected: String get() = text("seerr_connected")
    val seerrOff: String get() = text("seerr_off")
    val seerrNotSignedIn: String get() = text("seerr_not_signed_in")
    val seerrLead: String get() = text("seerr_lead")
    val seerrAddress: String get() = text("seerr_address")
    val seerrAddressPlaceholder: String get() = text("seerr_address_placeholder")
    val seerrAddressHelp: String get() = text("seerr_address_help")
    val seerrAddressSharedHelp: String get() = text("seerr_address_shared_help")
    val seerrConnecting: String get() = text("seerr_connecting")
    val seerrSigningIn: String get() = text("seerr_signing_in")
    val seerrServerSection: String get() = text("seerr_server_section")
    val seerrAddressRow: String get() = text("seerr_address_row")
    val seerrVersion: String get() = text("seerr_version")
    val seerrThisProfile: String get() = text("seerr_this_profile")

    fun seerrSignedInAs(name: String): String = text("seerr_signed_in_as", "name" to name)

    val seerrSignedInAutomatically: String get() = text("seerr_signed_in_automatically")
    val seerrSignedInWithPassword: String get() = text("seerr_signed_in_with_password")
    val seerrSignInWithPassword: String get() = text("seerr_sign_in_with_password")
    val seerrChangeAddress: String get() = text("seerr_change_address")
    val seerrRequestsLeft: String get() = text("seerr_requests_left")

    /**
     * What is left of the quota, for the kinds that have one, and how often it starts again when Seerr says: "3 movies and 2 seasons
     * this week". Seerr counts a show's requests in seasons.
     */
    fun seerrQuotaLeft(movies: Int?, seasons: Int?, days: Int?): String = when {
        movies != null && seasons != null -> quotaLeftBoth(collectionMovieCount(movies), seasonCount(seasons), days)
        movies != null -> quotaLeftMovies(collectionMovieCount(movies), days)
        seasons != null -> quotaLeftSeasons(seasonCount(seasons), days)
        else -> quotaLeftMovies("", days)
    }

    private fun quotaLeftMovies(movies: String, days: Int?): String = when (days) {
        null -> text("seerr_quota_left_movies", "movies" to movies)
        1 -> text("seerr_quota_left_movies_a_day", "movies" to movies)
        7 -> text("seerr_quota_left_movies_this_week", "movies" to movies)
        else -> plural("seerr_quota_left_movies_every_days", days, "movies" to movies)
    }

    private fun quotaLeftSeasons(seasons: String, days: Int?): String = when (days) {
        null -> text("seerr_quota_left_seasons", "seasons" to seasons)
        1 -> text("seerr_quota_left_seasons_a_day", "seasons" to seasons)
        7 -> text("seerr_quota_left_seasons_this_week", "seasons" to seasons)
        else -> plural("seerr_quota_left_seasons_every_days", days, "seasons" to seasons)
    }

    private fun quotaLeftBoth(movies: String, seasons: String, days: Int?): String = when (days) {
        null -> text("seerr_quota_left_movies_and_seasons", "movies" to movies, "seasons" to seasons)
        1 -> text("seerr_quota_left_movies_and_seasons_a_day", "movies" to movies, "seasons" to seasons)
        7 -> text("seerr_quota_left_movies_and_seasons_this_week", "movies" to movies, "seasons" to seasons)
        else -> plural("seerr_quota_left_movies_and_seasons_every_days", days, "movies" to movies, "seasons" to seasons)
    }

    val seerrDisconnect: String get() = text("seerr_disconnect")
    val seerrDisconnectHelp: String get() = text("seerr_disconnect_help")
    val seerrDisconnectConfirmTitle: String get() = text("seerr_disconnect_confirm_title")
    val seerrDisconnectConfirmMessage: String get() = text("seerr_disconnect_confirm_message")
    val seerrDisconnectConfirm: String get() = text("seerr_disconnect_confirm")
    val seerrPasswordTitle: String get() = text("seerr_password_title")
    val seerrPasswordReason: String get() = text("seerr_password_reason")
    val seerrUser: String get() = text("seerr_user")

    // Seerr titles in search, and Seerr posters anywhere
    val seerrNotInLibrary: String get() = text("seerr_not_in_library")
    val seerrNotInLibraryChip: String get() = text("seerr_not_in_library_chip")

    fun libraryNoMatch(query: String): String = text("library_no_match", "query" to query)

    /** The line under a Seerr poster: "2025 · Movie". */
    fun seerrTitleMeta(year: Int?, type: SeerrMediaType): String = listOfNotNull(year?.toString(), if (type == SeerrMediaType.Movie) text("seerr_title_meta_movie") else text("seerr_title_meta_show")).joinToString(" · ")

    /** The word on a Seerr poster's pill; null for a title nobody asked for, which shows the + disc instead. */
    fun seerrStateLabel(state: RequestState): String? = when (state) {
        RequestState.NotRequested -> null
        RequestState.Requested -> text("seerr_state_label_requested")
        RequestState.Approved -> text("seerr_state_label_approved")
        RequestState.PartlyAvailable -> text("seerr_state_label_partly_available")
        RequestState.Available -> text("seerr_state_label_available")
        RequestState.Declined -> text("seerr_state_label_declined")
        RequestState.Failed -> text("seerr_state_label_failed")
    }

    // The Seerr page of a title, its poster menu and requesting. Errors are in SeerrMessages.
    val seerrRequest: String get() = text("seerr_request")
    val seerrRequestSeasons: String get() = text("seerr_request_seasons")
    val seerrRequestAgain: String get() = text("seerr_request_again")
    val seerrRequest4k: String get() = text("seerr_request_4k")

    /** Under Request in 4K in the poster menu. */
    val seerrRequest4kQuota: String get() = text("seerr_request_4k_quota")
    val seerrCancelRequest: String get() = text("seerr_cancel_request")
    val seerrMoreLikeThis: String get() = text("seerr_more_like_this")
    val seerrAllMissingSeasons: String get() = text("seerr_all_missing_seasons")
    val seerrQuality: String get() = text("seerr_quality")
    val seerrStandard: String get() = text("seerr_standard")
    val seerr4k: String get() = text("seerr_4k")
    val seerrView: String get() = text("seerr_view")
    val seerrRequestCancelled: String get() = text("seerr_request_cancelled")

    /** A rating out of 10 in a facts line, the library's and Seerr's alike: "★ 8.7". */
    fun starRating(score: Double): String {
        val tenths = (score * 10 + 0.5).toInt()
        return "★ ${Formats.current.decimal(tenths / 10.0, 1, 1)}"
    }

    fun seerrEpisodeCount(count: Int): String = plural("seerr_episode_count", count)

    /** A season's tile in the request sheet: its number, "SP" for Specials. */
    fun seerrSeasonTile(number: Int): String = if (number == 0) text("seerr_season_tile_specials") else number.toString()

    /** The episode count under a season's tile: "10 ep". */
    fun seerrEpisodeCountShort(count: Int): String = text("seerr_episode_count_short", "count" to count)

    /** What a screen reader says for a season's tile: "Season 3, 10 episodes, Available". */
    fun seerrSeasonTileDescription(name: String, episodes: String, state: String?): String = listOfNotNull(name, episodes, state).joinToString(", ")

    fun seerrRequestSeasonCount(count: Int): String = plural("seerr_request_season_count", count)

    fun seerrPickerSubtitle(title: String, seasons: Int): String = text("seerr_picker_subtitle", "title" to title, "seasons" to seasonCount(seasons))

    /** The request sheet's subtitle for a movie: "Low Tide · 2025 · Movie". */
    fun seerrPickerMovieSubtitle(title: String, meta: String): String = text("seerr_picker_movie_subtitle", "title" to title, "meta" to meta)

    /** A quality profile Seerr sent without a name, by its number: "Profile 4". */
    fun seerrQualityNumber(id: Int): String = text("seerr_quality_number", "id" to id)

    /** What a screen reader says for a Seerr page's state tag and facts: "Requested, 2025, PG-13, 1 h 36 min". */
    fun seerrTagDescription(status: String?, facts: List<String>): String = listOfNotNull(status, factsLine(facts).takeIf { facts.isNotEmpty() }).joinToString(", ")

    fun seerrRequested(title: String): String = text("seerr_requested", "title" to title)

    /**
     * The line under a title's overview: where it stands, and who asked when someone did. [requestedBy] is the requester's name, or
     * null when it is the profile itself or not known; [byMe] says it is the profile.
     */
    fun seerrStatusLine(state: RequestState, byMe: Boolean, requestedBy: String?): String = when (state) {
        RequestState.NotRequested -> text("seerr_status_line_not_requested")

        RequestState.Requested -> when {
            byMe -> text("seerr_status_line_requested_by_me")
            requestedBy != null -> text("seerr_status_line_requested_by", "name" to requestedBy)
            else -> text("seerr_status_line_requested")
        }

        RequestState.Approved -> text("seerr_status_line_approved")

        RequestState.PartlyAvailable -> text("seerr_status_line_partly_available")

        RequestState.Available -> text("seerr_status_line_available")

        RequestState.Declined -> text("seerr_status_line_declined")

        RequestState.Failed -> text("seerr_status_line_failed")
    }

    /** The second status line, for a 4K request: "4K · Requested by you · Waiting for approval". */
    fun seerr4kLine(line: String): String = text("seerr_4k_line", "line" to line)

    /** Under a Seerr poster menu's title: "2025 · Movie · Not in your library", or the state's word. */
    fun seerrMenuSubtitle(meta: String, state: RequestState): String = text("seerr_menu_subtitle", "meta" to meta, "state" to (seerrStateLabel(state) ?: text("seerr_menu_subtitle_not_in_library")))

    /** Under the status row of a Seerr poster menu: "By you, 2 days ago · Waiting for approval". */
    fun seerrMenuStatusDetail(state: RequestState, byMe: Boolean, requestedBy: String?, daysAgo: Int?): String {
        val asked: String? = when {
            byMe -> menuAskedByYou(daysAgo)
            requestedBy != null -> menuAskedBy(requestedBy, daysAgo)
            else -> menuAskedWhen(daysAgo)
        }
        val next = when (state) {
            RequestState.Requested -> text("seerr_menu_status_detail_requested")
            RequestState.Approved -> text("seerr_menu_status_detail_approved")
            RequestState.Failed -> text("seerr_menu_status_detail_failed")
            RequestState.Declined -> text("seerr_menu_status_detail_declined")
            RequestState.PartlyAvailable -> text("seerr_menu_status_detail_partly_available")
            RequestState.NotRequested, RequestState.Available -> null
        }
        return listOfNotNull(asked, next).joinToString(" · ")
    }

    private fun menuAskedByYou(daysAgo: Int?): String = when (daysAgo) {
        null -> text("seerr_menu_status_detail_by_you")
        0 -> text("seerr_menu_status_detail_by_you_today")
        1 -> text("seerr_menu_status_detail_by_you_yesterday")
        else -> plural("seerr_menu_status_detail_by_you_days_ago", daysAgo)
    }

    private fun menuAskedBy(name: String, daysAgo: Int?): String = when (daysAgo) {
        null -> text("seerr_menu_status_detail_by", "name" to name)
        0 -> text("seerr_menu_status_detail_by_name_today", "name" to name)
        1 -> text("seerr_menu_status_detail_by_name_yesterday", "name" to name)
        else -> plural("seerr_menu_status_detail_by_name_days_ago", daysAgo, "name" to name)
    }

    private fun menuAskedWhen(daysAgo: Int?): String? = when (daysAgo) {
        null -> null
        0 -> text("seerr_menu_status_detail_today")
        1 -> text("seerr_menu_status_detail_yesterday")
        else -> plural("seerr_menu_status_detail_days_ago", daysAgo)
    }

    // Seerr on Home: My requests and the discover rows, and their pages
    val seerrMyRequests: String get() = text("seerr_my_requests")
    val seerrTrending: String get() = text("seerr_trending")
    val seerrPopularMovies: String get() = text("seerr_popular_movies")
    val seerrPopularShows: String get() = text("seerr_popular_shows")
    val seerrComingSoon: String get() = text("seerr_coming_soon")
    val seerrNoRequests: String get() = text("seerr_no_requests")
    val seerrFilterWaiting: String get() = text("seerr_filter_waiting")
    val seerrFilterApproved: String get() = text("seerr_filter_approved")
    val seerrFilterAvailable: String get() = text("seerr_filter_available")
    val seerrFilterDeclined: String get() = text("seerr_filter_declined")

    /** A release date ("2026-12-12") as Coming soon shows it: "Dec 12". Null when it can't be read. */
    fun seerrReleaseDay(date: String?): String? {
        val parts = date?.split("-")?.takeIf { it.size >= 3 } ?: return null
        val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = parts[2].take(2).toIntOrNull() ?: return null
        return text("seerr_release_day", "month" to Formats.current.monthName(month, true), "day" to day)
    }

    /** The line under a title in a poster's menu: "Movie · 2022", "Show · 2019", "S1 E4 · The Show". */
    fun tileMenuMeta(kind: ItemKind, year: Int?, seriesName: String?, season: Int?, episode: Int?): String? = when (kind) {
        ItemKind.Movie -> listOfNotNull(heroMovie, year?.toString())
        ItemKind.Series -> listOfNotNull(heroShow, year?.toString())
        ItemKind.Episode -> listOfNotNull(episodeTag(season, episode), seriesName)
        else -> listOfNotNull(year?.toString())
    }.joinToString(" · ").ifEmpty { null }

    /** What a request is for: "Movie · 2022", "Show · Seasons 1–2", "Show · Season 3". */
    fun seerrRequestKind(type: SeerrMediaType, year: Int?, seasons: List<Int>): String = if (type == SeerrMediaType.Movie) {
        listOfNotNull(text("seerr_request_kind_movie"), year?.toString()).joinToString(" · ")
    } else {
        val sorted = seasons.sorted()
        val which = when {
            sorted.isEmpty() -> null
            sorted.size == 1 -> text("seerr_request_kind_season", "number" to sorted.first())
            sorted == (sorted.first()..sorted.last()).toList() -> text("seerr_request_kind_seasons_range", "first" to sorted.first(), "last" to sorted.last())
            else -> text("seerr_request_kind_seasons", "numbers" to sorted.joinToString(", "))
        }
        listOfNotNull(text("seerr_request_kind_show"), which).joinToString(" · ")
    }

    /** When a request last changed, said as what happened: "Requested 2 days ago", "Approved yesterday", "Added to your library today". */
    fun seerrRequestWhen(state: RequestState, daysAgo: Int?): String {
        val what = when (state) {
            RequestState.Available, RequestState.PartlyAvailable -> text("seerr_request_when_added_to_library")
            RequestState.Approved -> text("seerr_request_when_approved")
            RequestState.Declined -> text("seerr_request_when_declined")
            RequestState.Failed -> text("seerr_request_when_failed")
            RequestState.Requested, RequestState.NotRequested -> text("seerr_request_when_requested")
        }
        return when (daysAgo) {
            null -> what
            0 -> text("seerr_request_status_today", "what" to what)
            1 -> text("seerr_request_status_yesterday", "what" to what)
            in 2..6 -> plural("seerr_request_status_days_ago", daysAgo, "what" to what)
            in 7..13 -> text("seerr_request_status_last_week", "what" to what)
            else -> plural("seerr_request_status_weeks_ago", daysAgo / 7, "what" to what)
        }
    }

    /** What a screen reader says for a Seerr poster: "Harbour Bridge, 2025 · Movie, not in your library, can be requested". */
    fun seerrTitleDescription(title: String, meta: String, state: RequestState): String = when (state) {
        RequestState.NotRequested -> text("seerr_title_description_not_requested", "title" to title, "meta" to meta)
        RequestState.Requested -> text("seerr_title_description_requested", "title" to title, "meta" to meta)
        RequestState.Approved -> text("seerr_title_description_approved", "title" to title, "meta" to meta)
        RequestState.PartlyAvailable -> text("seerr_title_description_partly_available", "title" to title, "meta" to meta)
        RequestState.Available -> text("seerr_title_description_available", "title" to title, "meta" to meta)
        RequestState.Declined -> text("seerr_title_description_declined", "title" to title, "meta" to meta)
        RequestState.Failed -> text("seerr_title_description_failed", "title" to title, "meta" to meta)
    }
}

private fun List<String>.joinToStringAnd(): String = if (size < 2) joinToString() else text("list_and", "leading" to dropLast(1).joinToString(", "), "last" to last())
