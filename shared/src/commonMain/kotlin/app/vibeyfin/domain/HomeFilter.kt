package app.vibeyfin.domain

/** What a category page lists: a type, or one genre's movies and shows. */
sealed interface HomeFilter {
    /** Shows: series. */
    data object Shows : HomeFilter

    data object Movies : HomeFilter

    /** Movies and shows by when they were added to the server, newest first. */
    data object New : HomeFilter

    /** Every movie and show in the library, to browse: the Library tab. */
    data object Library : HomeFilter

    /** The titles the user put on My List (the server's favourites), movies and shows. */
    data object MyList : HomeFilter

    data class Genre(val id: String, val name: String) : HomeFilter
}

/** Narrows a page that lists movies and shows together (My List, New, Library) to one kind; the Library also offers its collections. */
enum class TypeFilter { Movies, Shows, Collections }

/** The pages that list movies and shows together, and so offer the Movies and Shows buttons. */
val HomeFilter.offersTypeFilter: Boolean get() = this == HomeFilter.MyList || this == HomeFilter.New || this == HomeFilter.Library

/** Only the Library page offers collections. */
val HomeFilter.offersCollections: Boolean get() = this == HomeFilter.Library

/** Only the Library page narrows by release year and by person. */
val HomeFilter.offersYearAndPeople: Boolean get() = this == HomeFilter.Library

/** A release-year range to browse by, newest first: the decades, then everything before the oldest of them. */
enum class Decade(val years: IntRange) {
    Twenties(2020..2029),
    Tens(2010..2019),
    Noughties(2000..2009),
    Nineties(1990..1999),
    Eighties(1980..1989),
    Older(1900..1979),
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

/** New and My List are by when they were added; the other pages are by name. */
val HomeFilter.defaultSort: PageSort get() = if (this == HomeFilter.New || this == HomeFilter.MyList) PageSort.Added else PageSort.Name

/** One entry of a picker sheet: the Categories sheet, and the genre and year pickers on a category page. [count] is how many titles it holds, when known. */
data class FilterOption(val id: String, val name: String, val count: Int? = null)
