package app.vibeyfin.ui.common

import app.vibeyfin.domain.DynamicRange
import app.vibeyfin.domain.VideoFormat
import app.vibeyfin.domain.model.CastMember
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaAspect
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.Person
import app.vibeyfin.domain.model.Profile
import app.vibeyfin.domain.model.RequestState
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.domain.model.SearchResult
import app.vibeyfin.domain.model.SeerrCastMember
import app.vibeyfin.domain.model.SeerrEpisode
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrSeason
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.domain.model.SeerrTitleDetails
import app.vibeyfin.domain.model.Server
import app.vibeyfin.ui.home.ContinueCard
import app.vibeyfin.ui.home.MediaRow
import app.vibeyfin.ui.servers.ServerRow
import app.vibeyfin.ui.subtitles.SubtitleResultRow

/**
 * Fake data for each kind of content, so a skeleton is the real view drawn with these and shaped by redaction. Texts are longer
 * than any line limit, so every line the real view has is filled; none of it ever shows as words.
 */
object Placeholders {
    const val DETAIL: String = "00"
    const val LONG_TITLE: String = "A placeholder title that is long enough to fill every line the real title may take up"

    private const val LONG_NAME: String = "Placeholder Person With A Long Name"
    private const val LONG_LINE: String = "A placeholder line that fills the width"
    private const val LONG_OVERVIEW: String =
        "A placeholder summary that runs long enough to fill every line the real one may take up. It goes on about who is in it, " +
            "what happens and where it is set, so the paragraph is as tall as the longest real one and the page never jumps when the " +
            "real text arrives. It keeps going for a while, with a second sentence and a third, so that even a wide screen fills its lines. A final sentence runs on a little further, to be safe."
    private const val SEASON_NUMBER: Int = 1
    private const val EPISODE_NUMBER: Int = 1
    private const val YEAR: Int = 2024
    private const val RUNTIME_MINUTES: Int = 120
    private const val RATING: Double = 7.5
    private const val PROGRESS: Float = 0.4f
    private const val CAST_COUNT: Int = 8
    private const val RUNTIME_MS: Long = 7_200_000L
    private const val RESUME_MS: Long = 2_880_000L
    private const val SERIES_ID: String = "placeholder-series"
    private const val TMDB_ID: Int = 1
    private const val AGE_RATING: String = "PG-13"

    private val genres: List<String> = listOf("Drama", "Thriller", "Adventure", "Science Fiction")

    val posterTile: MediaItem = mediaItem(id = "placeholder-0", kind = ItemKind.Movie, aspectRatio = MediaAspect.POSTER)

    val episode: MediaItem = mediaItem(id = "placeholder-episode", kind = ItemKind.Episode, aspectRatio = MediaAspect.WIDE)
        .copy(seasonNumber = SEASON_NUMBER, episodeNumber = EPISODE_NUMBER, seriesName = LONG_TITLE, seriesId = SERIES_ID)

    val item: MediaItem = mediaItem(id = "placeholder-item", kind = ItemKind.Movie, aspectRatio = MediaAspect.POSTER).copy(
        cast = (0 until CAST_COUNT).map { CastMember("placeholder-cast-$it", "$LONG_NAME $it", LONG_NAME, null) },
        videoFormat = VideoFormat("hevc", 3840, 2160, DynamicRange.Hdr10, 24f, 20_000),
        isFavorite = false,
        tmdbId = TMDB_ID,
    )

    val continueCard: ContinueCard = ContinueCard(
        id = "placeholder-continue",
        title = LONG_TITLE,
        subtitle = "Season 1 episode 1 · $LONG_LINE",
        imageUrl = null,
        imageBlurHash = null,
        progress = PROGRESS,
        accessibilityLabel = LONG_TITLE,
    )

    val library: MediaItem = mediaItem(id = "placeholder-library", kind = ItemKind.Library, aspectRatio = MediaAspect.WIDE)

    val seerrTitle: SeerrTitle = seerrTitle(0)

    val seerrEpisode: SeerrEpisode = SeerrEpisode(
        season = SEASON_NUMBER,
        number = EPISODE_NUMBER,
        title = LONG_TITLE,
        overview = LONG_OVERVIEW,
        airDate = "2024-01-01",
        stillUrl = null,
        rating = RATING,
    )

    val seerrDetails: SeerrTitleDetails = SeerrTitleDetails(
        title = seerrTitle,
        state4k = RequestState.NotRequested,
        genres = genres,
        runtimeMinutes = RUNTIME_MINUTES,
        certification = AGE_RATING,
        cast = (0 until CAST_COUNT).map { SeerrCastMember(it, "$LONG_NAME $it", LONG_NAME, null) },
        trailerUrl = null,
        seasons = (1..2).map { SeerrSeason(it, LONG_LINE, EPISODE_COUNT, YEAR, RequestState.NotRequested, RequestState.NotRequested) },
        requests = emptyList(),
    )

    val person: Person = Person(
        id = "placeholder-person",
        name = "$LONG_NAME And A Second Name",
        overview = LONG_OVERVIEW,
        bornYear = YEAR,
        birthplace = LONG_LINE,
        imageUrl = null,
        imageBlurHash = null,
    )

    val profile: Profile = Profile(userId = "placeholder-profile", name = "$LONG_NAME Profile", hasSession = true, needsPassword = false)

    val serverRow: ServerRow = ServerRow(
        server = Server(id = "placeholder-server", name = "$LONG_TITLE on the home network", version = "10.11.0", addresses = listOf("https://placeholder.example.org")),
        profileCount = 2,
    )

    val subtitleResult: SubtitleResultRow
        get() = SubtitleResultRow(id = key(0), name = LONG_TITLE, details = Strings.loadingPage, isMatch = false, added = false, spoken = "")

    fun key(index: Int): String = "placeholder-$index"

    fun posterTiles(count: Int): List<MediaItem> = (0 until count).map { posterTile.copy(id = "placeholder-$it") }

    fun mediaRow(count: Int): MediaRow = MediaRow(id = "placeholder-row", title = LONG_LINE, items = posterTiles(count), loading = false)

    fun seerrTitles(count: Int): List<SeerrTitle> = (0 until count).map(::seerrTitle)

    fun searchResult(kind: SearchKind): SearchResult = SearchResult(
        id = "placeholder-${kind.name}",
        title = LONG_TITLE,
        kind = kind,
        subtitle = LONG_LINE,
        imageUrl = null,
        aspectRatio = if (kind == SearchKind.Episode) MediaAspect.WIDE else MediaAspect.POSTER,
    )

    private fun seerrTitle(index: Int): SeerrTitle = SeerrTitle(
        tmdbId = index,
        type = SeerrMediaType.Movie,
        title = LONG_TITLE,
        year = YEAR,
        posterUrl = null,
        backdropUrl = null,
        overview = LONG_OVERVIEW,
        state = RequestState.NotRequested,
        jellyfinItemId = null,
    )

    private fun mediaItem(id: String, kind: ItemKind, aspectRatio: Float): MediaItem = MediaItem(
        id = id,
        title = LONG_TITLE,
        kind = kind,
        isFolder = false,
        year = YEAR,
        runtimeMinutes = RUNTIME_MINUTES,
        genres = genres,
        overview = LONG_OVERVIEW,
        rating = RATING,
        ageRating = AGE_RATING,
        seriesName = null,
        seasonNumber = null,
        episodeNumber = null,
        posterAspectRatio = aspectRatio,
        posterUrl = null,
        backdropUrl = null,
        runtimeMs = RUNTIME_MS,
        resumePositionMs = RESUME_MS,
    )

    private const val EPISODE_COUNT: Int = 10
}
