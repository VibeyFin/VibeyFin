package app.vibeyfin.domain

/** What a category page lists: a type, or one genre's movies and shows. */
sealed interface HomeFilter {
    /** Shows: series. */
    data object Shows : HomeFilter

    data object Movies : HomeFilter

    /** Movies and shows by when they were added to the server, newest first. */
    data object New : HomeFilter

    /** The titles the user put on My List (the server's favourites), movies and shows. */
    data object MyList : HomeFilter

    data class Genre(val id: String, val name: String) : HomeFilter
}

/** Narrows a page that lists movies and shows together (My List, New) to one of them. */
enum class TypeFilter { Movies, Shows }

/** The pages that list movies and shows together, and so offer the Movies and Shows buttons. */
val HomeFilter.offersTypeFilter: Boolean get() = this == HomeFilter.MyList || this == HomeFilter.New

/** How a category page is ordered. Every page starts at [defaultSort]; the page's Sort button changes it. */
enum class PageSort {
    /** A to Z. */
    Name,

    /** What was added to the server most recently, newest first. */
    Added,

    /** Community rating, best first. */
    Rating,
}

/** New and My List are by when they were added; the other pages are by name. */
val HomeFilter.defaultSort: PageSort get() = if (this == HomeFilter.New || this == HomeFilter.MyList) PageSort.Added else PageSort.Name

/** One entry of a picker sheet: the Categories sheet, and the genre and year pickers on a category page. [count] is how many titles it holds, when known. */
data class FilterOption(val id: String, val name: String, val count: Int? = null)
