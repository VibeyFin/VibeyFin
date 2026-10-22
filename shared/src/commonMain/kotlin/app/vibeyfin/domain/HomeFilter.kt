package app.vibeyfin.domain

/** What a category page lists: a type, or one genre's movies and shows. */
sealed interface HomeFilter {
    /** Shows: series. */
    data object Shows : HomeFilter

    data object Movies : HomeFilter

    /** Movies and shows by when they were added to the server, newest first. */
    data object New : HomeFilter

    data class Genre(val id: String, val name: String) : HomeFilter
}

/** How a category page is ordered. Every page starts at [defaultSort]; the page's Sort button changes it. */
enum class PageSort {
    /** A to Z. */
    Name,

    /** What was added to the server most recently, newest first. */
    Added,

    /** Community rating, best first. */
    Rating,
}

/** New is what was added most recently; the other pages are by name. */
val HomeFilter.defaultSort: PageSort get() = if (this == HomeFilter.New) PageSort.Added else PageSort.Name

/** One entry of a picker sheet: the Categories sheet, and the genre and year pickers on a category page. [count] is how many titles it holds, when known. */
data class FilterOption(val id: String, val name: String, val count: Int? = null)
