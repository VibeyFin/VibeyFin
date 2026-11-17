package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TICKS_PER_MINUTE
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import io.ktor.http.Parameters
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The `/Items` query each filter of a category page builds, and the filters the device checks itself. */
class FilterQueryTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private fun <T> ApiResult<T>.success(): T = assertIs<ApiResult.Success<T>>(this).value

    private suspend fun queryFor(filters: FilterSet, filter: HomeFilter = HomeFilter.Library): Parameters {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a"))))
        app.library.filteredPage(filter, filters).success()
        return app.jellyfin.requestsTo("GET", "/Items").last().query
    }

    @Test
    fun eachWatchedStateIsTheServersOwnFilter() = runTest {
        assertEquals("IsUnplayed", queryFor(FilterSet(watched = WatchedFilter.Unwatched))["filters"])
        assertEquals("IsPlayed", queryFor(FilterSet(watched = WatchedFilter.Watched))["filters"])
        assertEquals("IsResumable", queryFor(FilterSet(watched = WatchedFilter.InProgress))["filters"])
    }

    @Test
    fun favouritesCombineWithTheWatchedState() = runTest {
        assertEquals("IsFavorite,IsUnplayed", queryFor(FilterSet(favouritesOnly = true, watched = WatchedFilter.Unwatched))["filters"])
    }

    @Test
    fun severalGenresMatchAnyOfThemByTheirIdsCommaSeparated() = runTest {
        val query = queryFor(FilterSet(genres = listOf(NamedValue("g1", "Drama"), NamedValue("g2", "Thriller"))))

        assertEquals("g1,g2", query["genreIds"])
        assertNull(query["fields"])
    }

    @Test
    fun aYearRangeAsksForEachYearInIt() = runTest {
        assertEquals("1995,1996,1997", queryFor(FilterSet(years = YearRange(1995, 1997)))["years"])
        assertEquals("2003", queryFor(FilterSet(years = YearRange(2003, 2003)))["years"])
    }

    @Test
    fun ratingsAndAgeRatingAreTheServersMinimumsAndMaximum() = runTest {
        val query = queryFor(FilterSet(minCommunityRating = 7, minCriticRating = 75, maxAgeRating = "PG-13"))

        assertEquals("7", query["minCommunityRating"])
        assertEquals("75", query["minCriticRating"])
        assertEquals("PG-13", query["maxOfficialRating"])
    }

    @Test
    fun studiosAreIdsAndTagsAreNamesSeparatedByAPipe() = runTest {
        val query = queryFor(FilterSet(studios = listOf(NamedValue("s1", "A24"), NamedValue("s2", "Neon")), tags = listOf("dance", "no-kids")))

        assertEquals("s1,s2", query["studioIds"])
        assertEquals("dance|no-kids", query["tags"])
    }

    @Test
    fun aLibraryIsTheParentAndAPersonTheirId() = runTest {
        val query = queryFor(FilterSet(library = NamedValue("lib-movies", "Movies"), person = NamedValue("p1", "Alex")))

        assertEquals("lib-movies", query["parentId"])
        assertEquals("p1", query["personIds"])
        assertEquals("true", query["recursive"])
    }

    @Test
    fun languagesAreTheirCodesCommaSeparated() = runTest {
        val query = queryFor(FilterSet(audioLanguages = listOf(NamedValue("ger", "German")), subtitleLanguages = listOf(NamedValue("eng", "English"), NamedValue("fra", "French"))))

        assertEquals("ger", query["audioLanguages"])
        assertEquals("eng,fra", query["subtitleLanguages"])
    }

    @Test
    fun hdAnd4kAreTheServersOwnFlags() = runTest {
        assertEquals("true", queryFor(FilterSet(quality = VideoQuality.Hd))["isHd"])
        assertEquals("true", queryFor(FilterSet(quality = VideoQuality.Uhd))["is4K"])
    }

    @Test
    fun eachSortHasItsKeysAndRunsTheWayItWasTurned() = runTest {
        val releaseDate = queryFor(FilterSet(sort = PageSort.ReleaseDate))
        val shortest = queryFor(FilterSet(sort = PageSort.Runtime))
        val longest = queryFor(FilterSet(sort = PageSort.Runtime, order = SortOrder.Descending))

        assertEquals("PremiereDate,ProductionYear,SortName" to "Descending", releaseDate["sortBy"] to releaseDate["sortOrder"])
        assertEquals("Runtime,SortName" to "Ascending", shortest["sortBy"] to shortest["sortOrder"])
        assertEquals("Descending", longest["sortOrder"])
        assertEquals("CriticRating,SortName", queryFor(FilterSet(sort = PageSort.CriticRating))["sortBy"])
        assertEquals("DatePlayed,SortName", queryFor(FilterSet(sort = PageSort.LastPlayed))["sortBy"])
        assertEquals("PlayCount,SortName", queryFor(FilterSet(sort = PageSort.PlayCount))["sortBy"])
        assertEquals("Random", queryFor(FilterSet(sort = PageSort.Random))["sortBy"])
    }

    @Test
    fun filtersCombineInOneQuery() = runTest {
        val query = queryFor(
            FilterSet(
                type = TypeFilter.Movies,
                genres = listOf(NamedValue("g1", "Drama")),
                watched = WatchedFilter.Unwatched,
                years = YearRange(1990, 1999),
                minCommunityRating = 7,
            ),
        )

        assertEquals("Movie", query["includeItemTypes"])
        assertEquals("g1", query["genreIds"])
        assertEquals("IsUnplayed", query["filters"])
        assertEquals("1990,1991,1992,1993,1994,1995,1996,1997,1998,1999", query["years"])
        assertEquals("7", query["minCommunityRating"])
    }

    // What the server can't filter by is checked on the device.

    @Test
    fun aRuntimeLimitKeepsOnlyTheTitlesOnTheRightSideOfIt() = runTest {
        app.signedIn().on(
            "GET",
            "/Items",
            Reply.json(itemsJson(itemJson("short", runtimeTicks = 25 * TICKS_PER_MINUTE), itemJson("long", runtimeTicks = 130 * TICKS_PER_MINUTE), itemJson("unknown"))),
        )

        val under = app.library.filteredPage(HomeFilter.Library, FilterSet(runtime = RuntimeLimit(RuntimeLimit.Bound.ShorterThan, 30))).success()
        val over = app.library.filteredPage(HomeFilter.Library, FilterSet(runtime = RuntimeLimit(RuntimeLimit.Bound.LongerThan, 120))).success()

        assertEquals(listOf("short"), under.items.map { it.id })
        assertEquals(listOf("long"), over.items.map { it.id })
    }

    @Test
    fun aMinimumCriticRatingIsCheckedOnTheDeviceAsTheServerIgnoresIt() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("praised", criticRating = 92.0), itemJson("panned", criticRating = 40.0), itemJson("unrated"))))

        val page = app.library.filteredPage(HomeFilter.Library, FilterSet(minCriticRating = 90)).success()

        assertEquals(listOf("praised"), page.items.map { it.id })
    }

    @Test
    fun dateAddedIsCountedBackFromTheClock() = runTest {
        val now = app.clock.now()
        app.signedIn().on(
            "GET",
            "/Items",
            Reply.json(itemsJson(itemJson("fresh", dateCreated = (now - 2.days).toString()), itemJson("old", dateCreated = (now - 40.days).toString()))),
        )

        val week = app.library.filteredPage(HomeFilter.Library, FilterSet(addedWithin = AddedWithin.Week)).success()

        assertEquals(listOf("fresh"), week.items.map { it.id })
        assertEquals("DateCreated", app.jellyfin.requestsTo("GET", "/Items").last().query["fields"])
    }

    @Test
    fun aJellyfinDateWithSevenDecimalsIsRead() = runTest {
        val now = Instant.parse("2026-11-16T12:00:00Z")
        app.clock.advance(now - app.clock.now())
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("fresh", dateCreated = "2026-11-15T08:30:12.1234567Z"))))

        val week = app.library.filteredPage(HomeFilter.Library, FilterSet(addedWithin = AddedWithin.Week)).success()

        assertEquals(listOf("fresh"), week.items.map { it.id })
    }

    @Test
    fun hdrAndDolbyVisionAreReadFromTheVideoTrack() = runTest {
        app.signedIn().on(
            "GET",
            "/Items",
            Reply.json(itemsJson(itemJson("sdr", videoRange = "SDR"), itemJson("hdr10", videoRange = "HDR10"), itemJson("dv", videoRange = "DOVIWithHDR10"), itemJson("show", type = "Series"))),
        )

        val hdr = app.library.filteredPage(HomeFilter.Library, FilterSet(quality = VideoQuality.Hdr)).success()
        val dolby = app.library.filteredPage(HomeFilter.Library, FilterSet(quality = VideoQuality.DolbyVision)).success()

        assertEquals(listOf("hdr10", "dv"), hdr.items.map { it.id })
        assertEquals(listOf("dv"), dolby.items.map { it.id })
        assertEquals("MediaStreams", app.jellyfin.requestsTo("GET", "/Items").last().query["fields"])
    }

    @Test
    fun allGenresAsksForTheFirstAndChecksTheRestOnTheDevice() = runTest {
        app.signedIn().on(
            "GET",
            "/Items",
            Reply.json(itemsJson(itemJson("both", genres = listOf("Drama", "Thriller")), itemJson("drama", genres = listOf("Drama")))),
        )

        val page = app.library.filteredPage(
            HomeFilter.Library,
            FilterSet(genres = listOf(NamedValue("g1", "Drama"), NamedValue("g2", "Thriller")), genreMatch = GenreMatch.All),
            fields = "Overview",
        ).success()

        assertEquals(listOf("both"), page.items.map { it.id })
        val query = app.jellyfin.requestsTo("GET", "/Items").last().query
        assertEquals("g1", query["genreIds"])
        assertEquals("Overview,Genres", query["fields"])
    }

    @Test
    fun theDevicePassReadsOnUntilThePageIsFullAndSaysWhereToGoOn() = runTest {
        val all = (1..250).map { n -> itemJson("t$n", runtimeTicks = (if (n % 50 == 0) 20 else 100) * TICKS_PER_MINUTE) }
        app.signedIn().on("GET", "/Items") { request ->
            val start = request.query["startIndex"]!!.toInt()
            val size = request.query["limit"]!!.toInt()
            Reply.json(itemsJson(*all.drop(start).take(size).toTypedArray(), total = all.size, startIndex = start))
        }
        val short = FilterSet(runtime = RuntimeLimit(RuntimeLimit.Bound.ShorterThan, 30))

        val first = app.library.filteredPage(HomeFilter.Library, short, limit = 2).success()
        val rest = app.library.filteredPage(HomeFilter.Library, short, startIndex = first.nextIndex, limit = 2).success()

        assertEquals(listOf("t50", "t100"), first.items.map { it.id })
        assertEquals(100, first.nextIndex)
        assertTrue(first.hasMore)
        assertNull(first.total)
        assertEquals(listOf("t150", "t200"), rest.items.map { it.id })
    }

    @Test
    fun aDevicePassThatReadsTheWholeListKnowsItsTotal() = runTest {
        app.signedIn().on("GET", "/Items", Reply.json(itemsJson(itemJson("a", runtimeTicks = 10 * TICKS_PER_MINUTE), itemJson("b", runtimeTicks = 99 * TICKS_PER_MINUTE))))

        val page = app.library.filteredPage(HomeFilter.Library, FilterSet(runtime = RuntimeLimit(RuntimeLimit.Bound.ShorterThan, 30))).success()

        assertEquals(1, page.total)
        assertFalse(page.hasMore)
    }

    // What the pickers list.

    @Test
    fun yearsAndAgeRatingsAreMergedFromEachVideoLibrary() = runTest {
        val server = app.signedIn()
        server.on(
            "GET",
            "/UserViews",
            Reply.json(
                itemsJson(
                    itemJson("lib-a", "Movies", type = "CollectionFolder", isFolder = true, collectionType = "movies"),
                    itemJson("lib-b", "Shows", type = "CollectionFolder", isFolder = true, collectionType = "tvshows"),
                    itemJson("lib-c", "Collections", type = "CollectionFolder", isFolder = true, collectionType = "boxsets"),
                ),
            ),
        )
        server.on("GET", "/Items/Filters") { request ->
            if (request.query["parentId"] == "lib-a") {
                Reply.json("""{"OfficialRatings":["R","PG-13"],"Years":[1999,2026]}""")
            } else {
                Reply.json("""{"OfficialRatings":["TV-Y","Unknown-Rating"],"Years":[2010,2026]}""")
            }
        }
        server.on("GET", "/Items/Filters2", Reply.json("""{"Tags":["b-tag","A-tag"],"AudioLanguages":[{"Name":"English (eng)","Value":"eng"}],"SubtitleLanguages":[{"Name":"Undetermined (und)","Value":"und"}]}"""))
        server.on("GET", "/Localization/ParentalRatings", Reply.json("""[{"Name":"TV-Y","Value":0},{"Name":"PG-13","Value":13},{"Name":"R","Value":17}]"""))

        val values = app.library.filterValues(HomeFilter.Library, FilterSet()).success()

        assertEquals(listOf(2026, 2010, 1999), values.years)
        // Youngest first; a rating the server can't place is left out, as it couldn't be a limit.
        assertEquals(listOf("TV-Y", "PG-13", "R"), values.ageRatings)
        assertEquals(listOf("A-tag", "b-tag"), values.tags)
        assertEquals(listOf("English (eng)"), values.audioLanguages.map { it.name })
        assertTrue(values.subtitleLanguages.isEmpty())
        assertEquals(setOf("lib-a", "lib-b"), app.jellyfin.requestsTo("GET", "/Items/Filters").map { it.query["parentId"] }.toSet())
    }

    @Test
    fun studiosAreCountedForThePagesKindOfTitle() = runTest {
        app.signedIn().on(
            "GET",
            "/Studios",
            Reply.json(itemsJson(itemJson("s1", "A24", type = "Studio", movieCount = 4, seriesCount = 1), itemJson("s2", "HBO", type = "Studio", movieCount = 0, seriesCount = 3))),
        )

        val studios = app.library.studios(HomeFilter.Movies, FilterSet()).success()

        assertEquals(listOf("A24" to 4), studios.map { it.name to it.count })
        assertEquals("Movie", app.jellyfin.requestsTo("GET", "/Studios").last().query["includeItemTypes"])
        assertEquals("ItemCounts", app.jellyfin.requestsTo("GET", "/Studios").last().query["fields"])
    }
}
