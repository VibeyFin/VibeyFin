package app.vibeyfin.domain

import app.vibeyfin.data.api.BaseItemDto
import kotlin.time.Instant

private const val TICKS_PER_MINUTE = 600_000_000L
private const val SECONDS_PER_DAY = 86_400L

/** The `/Items` item types [filter]'s page lists with this set. */
internal fun FilterSet.itemTypes(filter: HomeFilter): List<String> = when {
    filter == HomeFilter.Shows || type == TypeFilter.Shows -> listOf("Series")
    filter == HomeFilter.Movies || type == TypeFilter.Movies -> listOf("Movie")
    type == TypeFilter.Collections -> listOf("BoxSet")
    else -> listOf("Movie", "Series")
}

/**
 * The `/Items` parameters for [filter]'s page with this set: every filter the server applies itself. What it can't
 * apply ([FilterSet.needsDevicePass]) is checked by [matchesOnDevice] on what comes back.
 */
internal fun FilterSet.serverQuery(filter: HomeFilter): Map<String, String> = buildMap {
    put("recursive", "true")
    put("includeItemTypes", itemTypes(filter).joinToString(","))
    put("sortBy", sortKeys(sortOn(filter)))
    put("sortOrder", orderOn(filter).name)
    itemFilters(filter)?.let { put("filters", it) }
    genreIds(filter)?.let { put("genreIds", it) }
    years?.let { put("years", it.years.joinToString(",")) }
    minCommunityRating?.let { put("minCommunityRating", it.toString()) }
    // Jellyfin 12.2 lists this but ignores it; it is checked on the device as well.
    minCriticRating?.let { put("minCriticRating", it.toString()) }
    maxAgeRating?.let { put("maxOfficialRating", it) }
    studios.ids()?.let { put("studioIds", it) }
    // Names are pipe-separated on the server, ids and languages comma-separated.
    tags.takeIf { it.isNotEmpty() }?.let { put("tags", it.joinToString("|")) }
    library?.let { put("parentId", it.id) }
    audioLanguages.ids()?.let { put("audioLanguages", it) }
    subtitleLanguages.ids()?.let { put("subtitleLanguages", it) }
    person?.let { put("personIds", it.id) }
    when (quality) {
        VideoQuality.Hd -> put("isHd", "true")
        VideoQuality.Uhd -> put("is4K", "true")
        VideoQuality.Hdr, VideoQuality.DolbyVision, null -> Unit
    }
}

/** The fields [matchesOnDevice] reads that a list leaves out unless asked. */
internal fun FilterSet.deviceFields(): List<String> = listOfNotNull(
    "Genres".takeIf { genreMatch == GenreMatch.All && genres.size > 1 },
    "DateCreated".takeIf { addedWithin != null },
    "MediaStreams".takeIf { quality == VideoQuality.Hdr || quality == VideoQuality.DolbyVision },
)

/** Whether [item] passes the filters the server can't apply: runtime, date added, critic rating, HDR, Dolby Vision and all genres. */
internal fun FilterSet.matchesOnDevice(item: BaseItemDto, now: Instant): Boolean = matchesRuntime(item) &&
    matchesAdded(item, now) &&
    (minCriticRating == null || (item.criticRating ?: return false) >= minCriticRating) &&
    matchesQuality(item) &&
    matchesEveryGenre(item)

private fun FilterSet.matchesRuntime(item: BaseItemDto): Boolean {
    val limit = runtime ?: return true
    val minutes = (item.runTimeTicks ?: return false) / TICKS_PER_MINUTE

    return when (limit.bound) {
        RuntimeLimit.Bound.ShorterThan -> minutes < limit.minutes
        RuntimeLimit.Bound.LongerThan -> minutes > limit.minutes
    }
}

private fun FilterSet.matchesAdded(item: BaseItemDto, now: Instant): Boolean {
    val within = addedWithin ?: return true
    val added = item.dateCreated?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false

    return (now - added).inWholeSeconds <= within.days * SECONDS_PER_DAY
}

private fun FilterSet.matchesQuality(item: BaseItemDto): Boolean {
    val range = item.mediaStreams.firstOrNull { it.type == "Video" }?.videoRangeType.orEmpty()

    return when (quality) {
        VideoQuality.Hdr -> range.isNotEmpty() && range != "SDR" && range != "Unknown"
        VideoQuality.DolbyVision -> range.startsWith("DOVI")
        VideoQuality.Hd, VideoQuality.Uhd, null -> true
    }
}

private fun FilterSet.matchesEveryGenre(item: BaseItemDto): Boolean {
    if (genreMatch != GenreMatch.All || genres.size < 2) return true
    val held = item.genres.map { it.lowercase() }.toSet()

    return genres.all { it.name.lowercase() in held }
}

private fun FilterSet.itemFilters(filter: HomeFilter): String? = listOfNotNull(
    "IsFavorite".takeIf { favouritesOnly || filter == HomeFilter.MyList },
    when (watched) {
        WatchedFilter.Unwatched -> "IsUnplayed"
        WatchedFilter.Watched -> "IsPlayed"
        WatchedFilter.InProgress -> "IsResumable"
        null -> null
    },
).joinToString(",").ifEmpty { null }

// A genre's own page is that genre. Several genres the server matches as any of them; for all of them it is asked for
// the first and the device checks the rest.
private fun FilterSet.genreIds(filter: HomeFilter): String? = when {
    filter is HomeFilter.Genre -> filter.id
    genres.isEmpty() -> null
    genreMatch == GenreMatch.All -> genres.first().id
    else -> genres.ids()
}

private fun List<NamedValue>.ids(): String? = takeIf { it.isNotEmpty() }?.joinToString(",") { it.id }

private fun sortKeys(sort: PageSort): String = when (sort) {
    PageSort.Name -> "SortName"
    PageSort.Added -> "DateCreated"
    PageSort.Rating -> "CommunityRating,SortName"
    PageSort.CriticRating -> "CriticRating,SortName"
    PageSort.ReleaseDate -> "PremiereDate,ProductionYear,SortName"
    PageSort.Runtime -> "Runtime,SortName"
    PageSort.LastPlayed -> "DatePlayed,SortName"
    PageSort.PlayCount -> "PlayCount,SortName"
    PageSort.Random -> "Random"
}
