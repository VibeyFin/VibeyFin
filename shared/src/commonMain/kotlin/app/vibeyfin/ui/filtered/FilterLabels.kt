package app.vibeyfin.ui.filtered

import app.vibeyfin.domain.AddedWithin
import app.vibeyfin.domain.Decade
import app.vibeyfin.domain.FilterSet
import app.vibeyfin.domain.GenreMatch
import app.vibeyfin.domain.PageSort
import app.vibeyfin.domain.RuntimeLimit
import app.vibeyfin.domain.SortOrder
import app.vibeyfin.domain.VideoQuality
import app.vibeyfin.domain.WatchedFilter
import app.vibeyfin.domain.YearRange
import app.vibeyfin.ui.common.Strings

/** The runtimes a page can be narrowed to, shortest first. */
internal val RUNTIME_LIMITS: List<RuntimeLimit> = listOf(
    RuntimeLimit(RuntimeLimit.Bound.ShorterThan, 30),
    RuntimeLimit(RuntimeLimit.Bound.ShorterThan, 60),
    RuntimeLimit(RuntimeLimit.Bound.ShorterThan, 90),
    RuntimeLimit(RuntimeLimit.Bound.LongerThan, 90),
    RuntimeLimit(RuntimeLimit.Bound.LongerThan, 120),
    RuntimeLimit(RuntimeLimit.Bound.LongerThan, 150),
)

/** The lowest community ratings a page can ask for, out of ten. */
internal val COMMUNITY_RATINGS: List<Int> = listOf(9, 8, 7, 6, 5)

/** The lowest critic ratings a page can ask for, in per cent. */
internal val CRITIC_RATINGS: List<Int> = listOf(90, 75, 60)

internal fun sortLabel(sort: PageSort): String = when (sort) {
    PageSort.Name -> Strings.sortName
    PageSort.Added -> Strings.sortAdded
    PageSort.Rating -> Strings.sortRating
    PageSort.CriticRating -> Strings.sortCriticRating
    PageSort.ReleaseDate -> Strings.sortReleaseDate
    PageSort.Runtime -> Strings.sortRuntime
    PageSort.LastPlayed -> Strings.sortLastPlayed
    PageSort.PlayCount -> Strings.sortPlayCount
    PageSort.Random -> Strings.sortRandom
}

internal fun orderLabel(order: SortOrder): String = if (order == SortOrder.Ascending) Strings.sortAscending else Strings.sortDescending

internal fun decadeLabel(decade: Decade): String = when (decade) {
    Decade.Twenties -> Strings.decade2020s
    Decade.Tens -> Strings.decade2010s
    Decade.Noughties -> Strings.decade2000s
    Decade.Nineties -> Strings.decade1990s
    Decade.Eighties -> Strings.decade1980s
    Decade.Older -> Strings.decadeOlder
}

/** A decade by its name, one year as itself, anything else as "1995 to 2004". */
internal fun yearLabel(range: YearRange): String = Decade.entries.firstOrNull { it.years == range }?.let(::decadeLabel)
    ?: if (range.from == range.to) range.from.toString() else Strings.yearRange(range.from, range.to)

internal fun watchedLabel(watched: WatchedFilter): String = when (watched) {
    WatchedFilter.Unwatched -> Strings.watchedUnwatched
    WatchedFilter.Watched -> Strings.watchedWatched
    WatchedFilter.InProgress -> Strings.watchedInProgress
}

internal fun runtimeLabel(limit: RuntimeLimit): String = when (limit.bound) {
    RuntimeLimit.Bound.ShorterThan -> Strings.runtimeUnder(limit.minutes)
    RuntimeLimit.Bound.LongerThan -> Strings.runtimeOver(limit.minutes)
}

internal fun runtimeId(limit: RuntimeLimit): String = "${limit.bound.name}:${limit.minutes}"

internal fun addedLabel(added: AddedWithin): String = when (added) {
    AddedWithin.Week -> Strings.addedWeek
    AddedWithin.Month -> Strings.addedMonth
    AddedWithin.Year -> Strings.addedYear
}

internal fun qualityLabel(quality: VideoQuality): String = when (quality) {
    VideoQuality.Hd -> Strings.qualityHd
    VideoQuality.Uhd -> Strings.qualityUhd
    VideoQuality.Hdr -> Strings.qualityHdr
    VideoQuality.DolbyVision -> Strings.qualityDolbyVision
}

internal fun genreMatchLabel(match: GenreMatch): String = if (match == GenreMatch.All) Strings.genreMatchAll else Strings.genreMatchAny

/** What a filter that takes several values says once some are chosen: the one name, or how many. */
internal fun manyLabel(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> names.single()
    else -> Strings.selectedCount(names.size)
}

/** How many of the filters behind the Filters button narrow the page. */
internal fun FilterSet.filtersSheetCount(): Int = listOf(
    genres.isNotEmpty(),
    years != null,
    watched != null,
    favouritesOnly,
    minCommunityRating != null,
    minCriticRating != null,
    maxAgeRating != null,
    studios.isNotEmpty(),
    tags.isNotEmpty(),
    library != null,
    runtime != null,
    addedWithin != null,
    quality != null,
    audioLanguages.isNotEmpty(),
    subtitleLanguages.isNotEmpty(),
).count { it }
