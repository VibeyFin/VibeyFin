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
data class WhoIsWatching(val serverId: String)

@Serializable
data object Home

/** The three tabs' graphs; each holds its root and what opens from it. */
@Serializable
data object HomeTab

@Serializable
data object LibraryTab

@Serializable
data object DownloadsTab

/** The Library tab's root. A placeholder until the library browser lands. */
@Serializable
data object Library

/** The Downloads tab's root. A placeholder until offline lands. */
@Serializable
data object Downloads

@Serializable
data object Startup

@Serializable
data object Settings

/** A cast member's page: their biography and what they appear in. */
@Serializable
data class Person(val personId: String)

/** What is inside a library, folder, series or season. [title] is shown at the top while it loads. */
@Serializable
data class Children(val parentId: String, val title: String)

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
    is HomeFilter.Genre -> FilteredList("genre", id, name)
}

internal fun FilteredList.toFilter(): HomeFilter = when (kind) {
    "shows" -> HomeFilter.Shows
    "movies" -> HomeFilter.Movies
    "new" -> HomeFilter.New
    "mylist" -> HomeFilter.MyList
    else -> HomeFilter.Genre(id.orEmpty(), name.orEmpty())
}
