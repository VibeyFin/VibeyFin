package app.vibeyfin.app.navigation

import app.vibeyfin.domain.HomeFilter
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. One `@Serializable` object or class per screen. */
@Serializable
data object Servers

/** [address] is filled in and checked straight away, e.g. for a server found by the search. */
@Serializable
data class AddServer(val address: String? = null)

@Serializable
data class WhoIsWatching(
    val serverId: String,
    /** Opens on this profile's PIN (the user id on [serverId]), set at launch. */
    val pinUserId: String? = null,
)

@Serializable
data object Home

/** The three tabs' graphs; each holds its root and what opens from it. */
@Serializable
data object HomeTab

@Serializable
data object SearchTab

@Serializable
data object LibraryTab

@Serializable
data object DownloadsTab

/** The Search tab's root. */
@Serializable
data object Search

/** The Library tab's root: every movie and show, to browse by type, genre, year, person and order. */
@Serializable
data object Library

/** The Downloads tab's root. A placeholder until offline lands. */
@Serializable
data object Downloads

@Serializable
data object Startup

@Serializable
data object Settings

/** A page of the Settings tree below the root; [id] is a `SettingsPageId` name, so the route stays serialisable. */
@Serializable
data class SettingsPageRoute(val id: String)

/** A cast member's page: their biography and what they appear in. */
@Serializable
data class Person(val personId: String)

/** What is inside a library, folder, series or season. [title] is shown at the top while it loads. */
@Serializable
data class Children(val parentId: String, val title: String, val collection: Boolean = false, val imageUrl: String? = null)

@Serializable
data class Item(val itemId: String)

/** Plays one movie or episode, full screen. It resumes where the server says the user stopped, unless [startOver]. */
@Serializable
data class Player(val itemId: String, val startOver: Boolean = false)

/**
 * The page a category button or a dropdown entry opens: [kind] is `shows`, `movies`, `new`, or `genre`;
 * [id] and [name] are the genre's own.
 */
@Serializable
data class FilteredList(val kind: String, val id: String? = null, val name: String? = null)

internal fun HomeFilter.toRoute(): FilteredList = when (this) {
    HomeFilter.Shows -> FilteredList("shows")
    HomeFilter.Movies -> FilteredList("movies")
    HomeFilter.New -> FilteredList("new")
    HomeFilter.MyList -> FilteredList("mylist")
    HomeFilter.Library -> FilteredList("library")
    is HomeFilter.Genre -> FilteredList("genre", id, name)
}

internal fun FilteredList.toFilter(): HomeFilter = when (kind) {
    "shows" -> HomeFilter.Shows
    "movies" -> HomeFilter.Movies
    "new" -> HomeFilter.New
    "mylist" -> HomeFilter.MyList
    "library" -> HomeFilter.Library
    else -> HomeFilter.Genre(id.orEmpty(), name.orEmpty())
}

/** The page of a title that isn't in the library, from Seerr: TMDB's [tmdbId], and [mediaType] `movie` or `tv`. */
@Serializable
data class SeerrPage(val tmdbId: Int, val mediaType: String)

/** A Seerr discover list's See all page; [kind] is a `SeerrListKind` name. */
@Serializable
data class SeerrList(val kind: String)

/** The profile's Seerr requests, opened from Home's My requests row. */
@Serializable
data object MyRequests

/** The same page opened from Settings › Seerr, which sits outside the tabs. */
@Serializable
data object SettingsMyRequests
