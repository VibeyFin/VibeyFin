package app.vibeyfin.ui.home

import app.vibeyfin.domain.FilterOption
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.ui.common.Strings

/** The four buttons under Home's header, in the order they are shown. */
enum class CategoryKind {
    Shows,
    Movies,
    New,

    /** Opens a dropdown of genres; the pick opens that genre's page. */
    Categories,
}

/** One category button, ready to draw. A button that [opensDropdown] looks different from the others: it has a chevron. */
data class CategoryButton(
    val kind: CategoryKind,
    val label: String,
    val opensDropdown: Boolean,
)

/** The Categories dropdown: loading, its entries, or why they didn't load. The screen filters [options] by what is typed. */
data class FilterDropdown(
    val kind: CategoryKind,
    val title: String,
    val options: List<FilterOption> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
) {
    /** The server answered and has nothing to pick from. */
    val isEmpty: Boolean get() = !loading && message == null && options.isEmpty()

    val emptyText: String get() = Strings.noCategories
}

internal fun categoryButtons(): List<CategoryButton> = listOf(
    CategoryButton(CategoryKind.Shows, Strings.filterShows, opensDropdown = false),
    CategoryButton(CategoryKind.Movies, Strings.filterMovies, opensDropdown = false),
    CategoryButton(CategoryKind.New, Strings.filterNew, opensDropdown = false),
    CategoryButton(CategoryKind.Categories, Strings.filterCategories, opensDropdown = true),
)

/** The name at the top of a category page: the button's own, or the genre's name. */
val HomeFilter.title: String
    get() = when (this) {
        HomeFilter.Shows -> Strings.filterShows
        HomeFilter.Movies -> Strings.filterMovies
        HomeFilter.New -> Strings.filterNew
        is HomeFilter.Genre -> name
    }
