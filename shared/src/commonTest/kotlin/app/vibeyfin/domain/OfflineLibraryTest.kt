package app.vibeyfin.domain

import app.vibeyfin.data.db.DOWNLOAD_RUNNING
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.downloaded
import app.vibeyfin.testing.downloadedShow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What is on the device, read without the server. */
class OfflineLibraryTest {
    private val app = TestApp()
    private val alex = ProfileRef("srv-1", "u1")
    private val library = app.offlineLibrary

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private suspend fun account() {
        app.servers.save("srv-1", "Home", "10.10.7", "http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
    }

    @Test
    fun aFinishedDownloadPlaysFromItsFileWithItsTracks() = runTest {
        account()
        app.downloaded("m1", "Heat", subtitle = true)

        val playable = assertNotNull(library.playable(alex, "m1"))

        assertEquals("file:///fake/srv-1/u1/m1/video.mkv", playable.plan.url)
        assertTrue(playable.plan.headers.isEmpty(), "nothing to sign: the file is on the device")
        assertEquals(PlayMethod.DirectPlay, playable.plan.method)
        assertEquals("Heat", playable.item.title)
        assertEquals(listOf("English", "German"), playable.plan.audioTracks.map { it.name })
        assertEquals(1, playable.plan.audioStreamIndex)
        assertEquals("file:///fake/srv-1/u1/m1/subs/3.srt", playable.plan.subtitleFiles.single().url)
        assertTrue(playable.plan.playSessionId.startsWith(LOCAL_SESSION_PREFIX))
    }

    private val avPlayer = PlaybackCapabilities(containers = listOf("mp4"), videoCodecs = listOf("h264"), audioCodecs = listOf("aac"))
    private val aether = PlaybackCapabilities(containers = listOf("mp4", "mkv"), videoCodecs = listOf("h264"), audioCodecs = listOf("aac"))

    @Test
    fun aFileOnlyTheSecondPlayerOpensPlaysInIt() = runTest {
        account()
        app.capabilities.engines = listOf(EngineCapabilities(PlaybackEngineChoice.Native, avPlayer), EngineCapabilities(PlaybackEngineChoice.Aether, aether))
        app.downloaded("m1")

        assertEquals(PlaybackEngineChoice.Aether, assertNotNull(library.playable(alex, "m1")).plan.engine)
    }

    @Test
    fun aFileTheFirstPlayerOpensStaysInIt() = runTest {
        account()
        app.capabilities.engines = listOf(EngineCapabilities(PlaybackEngineChoice.Native, aether), EngineCapabilities(PlaybackEngineChoice.Aether, aether))
        app.downloaded("m1")

        assertEquals(PlaybackEngineChoice.Native, assertNotNull(library.playable(alex, "m1")).plan.engine)
    }

    @Test
    fun aFileNoPlayerListsGoesToTheLastOne() = runTest {
        account()
        app.capabilities.engines = listOf(EngineCapabilities(PlaybackEngineChoice.Native, avPlayer), EngineCapabilities(PlaybackEngineChoice.Aether, avPlayer))
        app.downloaded("m1")

        assertEquals(PlaybackEngineChoice.Aether, assertNotNull(library.playable(alex, "m1")).plan.engine)
    }

    @Test
    fun aDownloadStartsWithTheTracksTheLanguagePreferencesPick() = runTest {
        account()
        app.database.accountDao().setLanguagePrefs("srv-1", "u1", """{"audioLanguage":"deu","subtitleMode":"None"}""")
        app.downloaded("m1", subtitle = true, described = true)

        val plan = assertNotNull(library.playable(alex, "m1")).plan

        assertEquals(2, plan.audioStreamIndex, "German, by its other code")
        assertNull(plan.subtitleStreamIndex)
        assertEquals(listOf(false, true), plan.audioTracks.map { it.selected })
        assertEquals(listOf(false), plan.subtitleTracks.map { it.selected })
    }

    @Test
    fun aDownloadSavedBeforeTracksHadLanguagesKeepsTheServersPick() = runTest {
        account()
        app.database.accountDao().setLanguagePrefs("srv-1", "u1", """{"audioLanguage":"ger","subtitleMode":"None"}""")
        app.downloaded("m1", subtitle = true)

        val plan = assertNotNull(library.playable(alex, "m1")).plan

        assertEquals(1, plan.audioStreamIndex)
        assertEquals(3, plan.subtitleStreamIndex)
    }

    @Test
    fun withoutSavedPreferencesADownloadKeepsTheServersPick() = runTest {
        account()
        app.downloaded("m1", subtitle = true, described = true)

        val plan = assertNotNull(library.playable(alex, "m1")).plan

        assertEquals(1, plan.audioStreamIndex)
        assertEquals(3, plan.subtitleStreamIndex)
    }

    @Test
    fun aSubtitleThatIsNotAFileIsNotOffered() = runTest {
        account()
        app.downloaded("m1")

        assertTrue(assertNotNull(library.playable(alex, "m1")).plan.subtitleTracks.isEmpty())
    }

    @Test
    fun anUnfinishedOrMissingDownloadDoesNotPlay() = runTest {
        account()
        app.downloaded("running", state = DOWNLOAD_RUNNING)
        app.downloaded("lost", withFile = false)

        assertNull(library.playable(alex, "running"))
        assertNull(library.playable(alex, "lost"), "its file is gone")
        assertNull(library.playable(alex, "never"))
    }

    @Test
    fun whereTheUserStoppedComesFromTheDevice() = runTest {
        account()
        app.downloaded("m1", positionMs = 42_000)
        app.downloaded("m2", played = true, positionMs = 90_000)

        assertEquals(42_000L, library.playable(alex, "m1")!!.item.resumePositionMs)
        val finished = library.playable(alex, "m2")!!.item
        assertTrue(finished.played)
        assertEquals(0L, finished.resumePositionMs, "a finished item has no place to resume")
    }

    @Test
    fun theNextEpisodeIsTheNextOneOnTheDevice() = runTest {
        account()
        app.downloaded("e1", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1)
        app.downloaded("e3", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 3)
        app.downloaded("s2e1", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 2, episode = 1)
        app.downloaded("other", type = "Episode", seriesId = "s2", seriesName = "Other", season = 1, episode = 2)
        val first = library.playable(alex, "e1")!!.item

        assertEquals("e3", library.nextAfter(alex, first)?.id, "episode 2 is not downloaded")
        assertEquals("s2e1", library.nextAfter(alex, library.playable(alex, "e3")!!.item)?.id)
        assertNull(library.nextAfter(alex, library.playable(alex, "s2e1")!!.item), "nothing after the last")
    }

    @Test
    fun continueWatchingHasWhatWasStartedAndThenTheNextEpisodeOfAShow() = runTest {
        account()
        app.downloaded("half", positionMs = 30_000, userDataAt = 5_000)
        app.downloaded("e1", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1, played = true, userDataAt = 9_000)
        app.downloaded("e2", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 2)
        app.downloaded("e3", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 3)
        app.downloaded("fresh")

        val ids = library.continueWatching(alex).first().map { it.id }

        assertEquals(listOf("half", "e2"), ids, "in progress first, then the next one of Saga; unstarted movies are not here")
    }

    @Test
    fun aShowWithAnEpisodeInProgressIsNotAlsoOfferedItsNext() = runTest {
        account()
        app.downloaded("e1", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1, played = true)
        app.downloaded("e2", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 2, positionMs = 10_000)
        app.downloaded("e3", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 3)

        assertEquals(listOf("e2"), library.continueWatching(alex).first().map { it.id })
    }

    @Test
    fun moviesAndShowsAreListedByName() = runTest {
        account()
        app.downloaded("b", "Beta")
        app.downloaded("a", "Alpha")
        app.downloaded("e1", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1)
        app.downloadedShow("s1", "Saga")

        assertEquals(listOf("Alpha", "Beta"), library.movies(alex).first().map { it.title })
        val shows = library.shows(alex).first()
        assertEquals(listOf("Saga"), shows.map { it.title })
        assertEquals("file:///fake/srv-1/u1/s1/images/poster.img", shows.single().posterUrl)
    }

    @Test
    fun aShowWithNothingDownloadedLeftIsNotListed() = runTest {
        account()
        app.downloadedShow("s1", "Saga")

        assertTrue(library.shows(alex).first().isEmpty())
    }

    @Test
    fun searchFindsByTitleShowAndCastOnly() = runTest {
        account()
        app.downloaded("m1", "Heat", cast = listOf("Al Pacino"))
        app.downloaded("e1", "Pilot", type = "Episode", seriesId = "s1", seriesName = "Saga", season = 1, episode = 1)
        app.downloaded("hidden", "Heat 2", state = DOWNLOAD_RUNNING)

        assertEquals(listOf("m1"), library.search(alex, "HEA").map { it.id })
        assertEquals(listOf("m1"), library.search(alex, "pacino").map { it.id })
        val byShow = library.search(alex, "saga").single()
        assertEquals(SearchKind.Episode, byShow.kind)
        assertEquals("Saga · S1 E1", byShow.subtitle)
        assertTrue(library.search(alex, "nothing at all").isEmpty())
    }

    @Test
    fun aSearchWordWithWildcardsIsTakenLiterally() = runTest {
        account()
        app.downloaded("m1", "100% Wolf")
        app.downloaded("m2", "Heat")

        assertEquals(listOf("m1"), library.search(alex, "100%").map { it.id })
        assertTrue(library.search(alex, "%").size == 1, "a bare % matches only a title with a % in it")
    }
}
