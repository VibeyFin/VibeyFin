package app.vibeyfin.ui.playback

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.ui.common.LoadMessages
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLanguageTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    private val reporterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        reporterScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
        app.close()
        TestLanguages.reset()
    }

    @Test
    fun aFailureMessageFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("load_for_error_server" to "Der Server meldet einen Fehler (HTTP {{status}}).")
        app.signedIn().on("GET", "/Items", Reply.status(HttpStatusCode.ServiceUnavailable))
        val viewModel = viewModels.track(
            PlaybackViewModel(
                "m1", app.library, app.playback, app.reporter(reporterScope), app.connection, app.watch, app.profileSettings, app.binge,
                app.offlineLibrary, app.offline, app.localReporter(reporterScope), app.subtitles,
            ),
        )
        val before = viewModel.uiState.first { it.message != null }
        assertEquals(LoadMessages.forError(ApiError.Server(503)), before.message)

        Texts.select("de")

        val after = viewModel.uiState.first { it != before }
        assertEquals("Der Server meldet einen Fehler (HTTP 503).", after.message)
    }
}
