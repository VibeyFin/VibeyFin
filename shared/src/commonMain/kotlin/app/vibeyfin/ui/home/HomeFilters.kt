package app.vibeyfin.ui.home

import app.vibeyfin.domain.FilterOption
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings

/** The five buttons under Home's header, in the order they are shown. */
enum class CategoryKind {
    Shows,
    Movies,
    New,

    /** Opens a sheet of studios. */
    Studios,

    /** Opens a sheet of genres. */
    Categories,
}

/** One category button, ready to draw. [label] already holds the picked name ("Studios: A24"). */
data class CategoryButton(
    val kind: CategoryKind,
    val label: String,
    val selected: Boolean,
    /** Tapping it opens a sheet instead of filtering at once. */
    val opensSheet: Boolean,
)

/** The Studios or Categories sheet: loading, its entries, or why they didn't load. */
data class FilterSheet(
    val kind: CategoryKind,
    val title: String,
    val options: List<FilterOption> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    /** The id already picked, marked in the list. */
    val pickedId: String? = null,
) {
    /** The server answered and has nothing to pick from. */
    val isEmpty: Boolean get() = !loading && message == null && options.isEmpty()

    val emptyText: String get() = if (kind == CategoryKind.Studios) Strings.noStudios else Strings.noCategories
}

/** The "Recently added" row under a filter. */
data class RecentRow(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
) {
    val title: String get() = Strings.recentlyAdded
}

internal fun categoryButtons(filter: HomeFilter?): List<CategoryButton> = listOf(
    CategoryButton(CategoryKind.Shows, Strings.filterShows, filter == HomeFilter.Shows, opensSheet = false),
    CategoryButton(CategoryKind.Movies, Strings.filterMovies, filter == HomeFilter.Movies, opensSheet = false),
    CategoryButton(CategoryKind.New, Strings.filterNew, filter == HomeFilter.New, opensSheet = false),
    CategoryButton(
        CategoryKind.Studios,
        (filter as? HomeFilter.Studio)?.let { Strings.filterPicked(Strings.filterStudios, it.name) } ?: Strings.filterStudios,
        filter is HomeFilter.Studio,
        opensSheet = true,
    ),
    CategoryButton(
        CategoryKind.Categories,
        (filter as? HomeFilter.Genre)?.let { Strings.filterPicked(Strings.filterCategories, it.name) } ?: Strings.filterCategories,
        filter is HomeFilter.Genre,
        opensSheet = true,
    ),
)
