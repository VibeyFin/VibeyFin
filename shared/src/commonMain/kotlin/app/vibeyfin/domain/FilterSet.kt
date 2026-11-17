package app.vibeyfin.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Something picked by its server id and shown by its name: a genre, studio, library, language or person. */
@Serializable
data class NamedValue(val id: String, val name: String)

@Serializable
enum class WatchedFilter { Unwatched, Watched, InProgress }

/** Several genres: a title has any one of them, or every one. */
@Serializable
enum class GenreMatch { Any, All }

/** Release years from [from] to [to], both included; one year when they are the same. */
@Serializable
data class YearRange(val from: Int, val to: Int) {
    val years: List<Int> get() = (minOf(from, to)..maxOf(from, to)).toList()
}

/** Shorter or longer than [minutes]. */
@Serializable
data class RuntimeLimit(val bound: Bound, val minutes: Int) {
    @Serializable
    enum class Bound { ShorterThan, LongerThan }
}

/** Added to the server within this many days. */
@Serializable
enum class AddedWithin(val days: Int) {
    Week(7),
    Month(30),
    Year(365),
}

@Serializable
enum class VideoQuality {
    Hd,
    Uhd,
    Hdr,
    DolbyVision,
}

/**
 * Everything a category page is ordered and narrowed by, in one value a custom row can save ([encode]) and load again
 * ([decode]). Null and empty mean "not narrowed"; [sort] and [order] null mean the page's own. [version] is written so a
 * later app can tell an old set from a new one; filters it doesn't know are ignored when it is read.
 */
@Serializable
data class FilterSet(
    val version: Int = VERSION,
    val sort: PageSort? = null,
    val order: SortOrder? = null,
    val type: TypeFilter? = null,
    val watched: WatchedFilter? = null,
    val favouritesOnly: Boolean = false,
    val genres: List<NamedValue> = emptyList(),
    val genreMatch: GenreMatch = GenreMatch.Any,
    val years: YearRange? = null,
    val minCommunityRating: Int? = null,
    val minCriticRating: Int? = null,
    val maxAgeRating: String? = null,
    val studios: List<NamedValue> = emptyList(),
    val tags: List<String> = emptyList(),
    val library: NamedValue? = null,
    val runtime: RuntimeLimit? = null,
    val addedWithin: AddedWithin? = null,
    val quality: VideoQuality? = null,
    val audioLanguages: List<NamedValue> = emptyList(),
    val subtitleLanguages: List<NamedValue> = emptyList(),
    val person: NamedValue? = null,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    /** Some filters the server can't apply, so the titles it sends are checked on the device. */
    val needsDevicePass: Boolean
        get() = runtime != null || addedWithin != null || minCriticRating != null || quality == VideoQuality.Hdr ||
            quality == VideoQuality.DolbyVision || (genreMatch == GenreMatch.All && genres.size > 1)

    companion object {
        const val VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
            explicitNulls = false
        }

        /** The set [text] holds, or null when it isn't one. */
        fun decode(text: String): FilterSet? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** The order [filter]'s page is in with this set. */
fun FilterSet.sortOn(filter: HomeFilter): PageSort = sort ?: filter.defaultSort

fun FilterSet.orderOn(filter: HomeFilter): SortOrder = order ?: sortOn(filter).naturalOrder

/** What the titles of a page can be narrowed by: [years] newest first, [ageRatings] youngest first. */
data class FilterValues(
    val years: List<Int> = emptyList(),
    val ageRatings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val audioLanguages: List<FilterOption> = emptyList(),
    val subtitleLanguages: List<FilterOption> = emptyList(),
)
