package app.vibeyfin.ui.subtitles

import app.vibeyfin.testing.ConnectionRefused
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleSearchViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun viewModel() = viewModels.track(SubtitleSearchViewModel(app.subtitles, app.languages, app.connection))

    private suspend fun SubtitleSearchViewModel.settled() = uiState.first { !it.loading }

    private suspend fun signedIn(subtitleLanguage: String? = null): FakeServer {
        val server = app.signedIn()
        val config = subtitleLanguage?.let { """{"SubtitleLanguagePreference":"$it"}""" } ?: "{}"
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Configuration":$config}"""))
        server.on(
            "GET",
            "/Localization/Cultures",
            Reply.json(
                """[{"DisplayName":"English","TwoLetterISOLanguageName":"en","ThreeLetterISOLanguageName":"eng"},
                    {"DisplayName":"German","TwoLetterISOLanguageName":"de","ThreeLetterISOLanguageName":"deu","ThreeLetterISOLanguageNames":["ger","deu"]},
                    {"DisplayName":"Italian","TwoLetterISOLanguageName":"it","ThreeLetterISOLanguageName":"ita"}]""",
            ),
        )
        return server
    }

    private fun result(id: String, rating: Double? = null, match: Boolean = false, provider: String = "OpenSubtitles") = buildString {
        append("""{"Id":"$id","Name":"Name $id","ProviderName":"$provider","Format":"srt","IsHashMatch":$match""")
        rating?.let { append(""","CommunityRating":$it""") }
        append("}")
    }

    private fun FakeServer.finds(language: String, vararg results: String) = on("GET", "/Items/m1/RemoteSearch/Subtitles/$language", Reply.json(results.joinToString(",", "[", "]")))

    private fun itemWith(vararg subtitles: String) = Reply.json(
        """{"Items":[{"Id":"m1","Name":"Film","Type":"Movie","MediaStreams":[${subtitles.joinToString(",")}]}],"TotalRecordCount":1}""",
    )

    private fun track(index: Int, title: String) = """{"Index":$index,"Type":"Subtitle","DisplayTitle":"$title","Language":"eng"}"""

    @Test
    fun searchesAtOnceInTheProfilesLanguageWithMatchesFirstThenByRating() = runTest {
        val server = signedIn(subtitleLanguage = "ita")
        server.finds("ita", result("low", rating = 5.0), result("none"), result("high", rating = 8.4), result("match", rating = 1.0, match = true))
        val viewModel = viewModel()

        viewModel.onOpen("m1")
        val state = viewModel.settled()

        assertEquals("Italian", state.languageName)
        assertEquals(listOf("match", "high", "low", "none"), state.results.map { it.id })
        assertTrue(state.results.first().isMatch)
        assertEquals("OpenSubtitles · SRT · ★ 8.4", state.results[1].details)
        assertEquals("OpenSubtitles · SRT", state.results[3].details)
    }

    @Test
    fun withNoPreferenceSearchesInTheDevicesLanguage() = runTest {
        val server = signedIn()
        server.finds("deu", result("a"))
        val viewModel = viewModel()

        viewModel.onOpen("m1")
        val state = viewModel.settled()

        assertEquals("German", state.languageName)
        assertEquals(listOf("a"), state.results.map { it.id })
    }

    @Test
    fun changingTheLanguageSearchesAgainAndLeavesTheProfilesSettingAlone() = runTest {
        val server = signedIn(subtitleLanguage = "eng")
        server.finds("eng", result("en"))
        server.finds("ita", result("it"))
        val viewModel = viewModel()
        viewModel.onOpen("m1")
        viewModel.uiState.first { it.results.isNotEmpty() }

        viewModel.onSelectLanguage("ita")
        val state = viewModel.uiState.first { it.results.singleOrNull()?.id == "it" }

        assertEquals("Italian", state.languageName)
        assertTrue(app.jellyfin.callLog().none { it.startsWith("POST") && it.contains("Configuration") })

        viewModel.onOpen("m1")
        assertEquals("en", viewModel.uiState.first { it.results.isNotEmpty() && it.languageName == "English" }.results.single().id)
    }

    @Test
    fun theLanguageListIsTheServersFilteredByWhatIsTypedAndHasNoAnyLanguage() = runTest {
        val server = signedIn(subtitleLanguage = "eng")
        server.finds("eng")
        val viewModel = viewModel()
        viewModel.onOpen("m1")
        val all = viewModel.settled().languages
        assertEquals(listOf("English", "German", "Italian"), all.map { it.name })
        assertEquals("eng", all.single { it.selected }.code)

        viewModel.onSearchLanguages("ger")
        assertEquals(listOf("German"), viewModel.uiState.first { it.languages.size == 1 }.languages.map { it.name })
    }

    @Test
    fun nothingFoundSaysSoWithThePluginHint() = runTest {
        val server = signedIn(subtitleLanguage = "ita")
        server.finds("ita")
        val viewModel = viewModel()

        viewModel.onOpen("m1")
        val problem = assertNotNull(viewModel.settled().problem)

        assertEquals(Strings.noSubtitlesFound("Italian"), problem.title)
        assertEquals(Strings.noSubtitlesHint, problem.text)
        assertFalse(problem.canRetry)
    }

    @Test
    fun aRefusedSearchSaysWhoToAskAndHidesTheEntryFromThenOn() = runTest {
        val server = signedIn(subtitleLanguage = "ita")
        server.on("GET", "/Items/m1/RemoteSearch/Subtitles/ita", Reply.status(HttpStatusCode.Forbidden))
        val viewModel = viewModel()

        viewModel.onOpen("m1")
        val problem = assertNotNull(viewModel.settled().problem)

        assertEquals(LoadMessages.SUBTITLES_NOT_ALLOWED_TITLE, problem.title)
        assertFalse(problem.canRetry)
        assertFalse(app.subtitles.available.first())
    }

    @Test
    fun aSearchThatGetsNoAnswerOffersTryAgain() = runTest {
        val server = signedIn(subtitleLanguage = "ita")
        var answer = false
        server.on("GET", "/Items/m1/RemoteSearch/Subtitles/ita") { if (answer) Reply.json("[${result("a")}]") else throw ConnectionRefused() }
        val viewModel = viewModel()

        viewModel.onOpen("m1")
        val problem = assertNotNull(viewModel.settled().problem)
        assertEquals(LoadMessages.SUBTITLES_UNREACHABLE_TITLE, problem.title)
        assertTrue(problem.canRetry)

        answer = true
        viewModel.onRetry()
        assertEquals("a", viewModel.uiState.first { it.results.isNotEmpty() }.results.single().id)
    }

    @Test
    fun aRejectedTokenEndsTheSessionWithNoMessage() = runTest {
        val server = signedIn(subtitleLanguage = "ita")
        server.on("GET", "/Items/m1/RemoteSearch/Subtitles/ita", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()

        viewModel.onOpen("m1")
        val state = viewModel.uiState.first { it.exit != null }

        assertNull(state.problem)
    }

    @Test
    fun choosingAResultWaitsForTheNewTrackAndReportsItsIndex() = runTest {
        val server = signedIn(subtitleLanguage = "eng")
        server.finds("eng", result("a b"))
        var reads = 0
        server.on("GET", "/Items") {
            reads++
            if (reads < 3) itemWith(track(2, "Italian")) else itemWith(track(2, "Italian"), track(3, "English - External"))
        }
        server.on("POST", "/Items/m1/RemoteSearch/Subtitles/a%20b", Reply.noContent())
        val viewModel = viewModel()
        viewModel.onOpen("m1")
        viewModel.settled()

        viewModel.onChoose("a b")
        val added = assertNotNull(viewModel.uiState.first { it.added != null }.added)

        assertEquals(SubtitleAdded("m1", 3, "English"), added)
        assertNull(viewModel.uiState.value.downloadingId)
        viewModel.onAddedHandled()
        assertNull(viewModel.uiState.first { it.added == null }.added)
    }

    @Test
    fun aTrackThatNeverArrivesSaysTheLimitMayBeReachedAndKeepsTheResults() = runTest {
        val server = signedIn(subtitleLanguage = "eng")
        server.finds("eng", result("a"))
        server.on("GET", "/Items", itemWith(track(2, "Italian")))
        server.on("POST", "/Items/m1/RemoteSearch/Subtitles/a", Reply.noContent())
        val viewModel = viewModel()
        viewModel.onOpen("m1")
        viewModel.settled()

        viewModel.onChoose("a")
        val state = viewModel.uiState.first { it.message != null }

        assertEquals(LoadMessages.SUBTITLES_DID_NOT_ARRIVE, state.message?.text)
        assertEquals(false, state.message?.canRetry)
        assertEquals(1, state.results.size)
        assertNull(state.added)
        assertNull(state.downloadingId)
    }

    @Test
    fun aDownloadThatFailsOffersTryAgainWhichRepeatsIt() = runTest {
        val server = signedIn(subtitleLanguage = "eng")
        server.finds("eng", result("a"))
        var posts = 0
        server.on("GET", "/Items") { if (posts < 2) itemWith() else itemWith(track(4, "English")) }
        server.on("POST", "/Items/m1/RemoteSearch/Subtitles/a") {
            posts++
            if (posts == 1) Reply.status(HttpStatusCode.InternalServerError) else Reply.noContent()
        }
        val viewModel = viewModel()
        viewModel.onOpen("m1")
        viewModel.settled()

        viewModel.onChoose("a")
        val failed = viewModel.uiState.first { it.message != null }
        assertEquals(LoadMessages.SUBTITLES_ADD_FAILED, failed.message?.text)
        assertEquals(true, failed.message?.canRetry)

        viewModel.onRetry()
        assertEquals(4, viewModel.uiState.first { it.added != null }.added?.streamIndex)
    }
}
