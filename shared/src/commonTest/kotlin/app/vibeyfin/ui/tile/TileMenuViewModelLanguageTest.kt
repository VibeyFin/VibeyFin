package app.vibeyfin.ui.tile

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.ui.common.LoadMessages
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

@OptIn(ExperimentalCoroutinesApi::class)
class TileMenuViewModelLanguageTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        TestLanguages.reset()
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun tile() = MediaItem(
        id = "m1", title = "Heat", kind = ItemKind.Movie, isFolder = false, year = 1995, runtimeMinutes = null,
        genres = emptyList(), overview = null, rating = null, ageRating = null, seriesName = null, seasonNumber = null, episodeNumber = null,
        posterAspectRatio = 2f / 3f, posterUrl = null, backdropUrl = null, isFavorite = false,
    )

    @Test
    fun theMyListFailureIsWordedAgainWhenTheLanguageChanges() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.json(itemsJson(itemJson("m1", "Heat"))))
        server.on("POST", "/UserFavoriteItems/m1", Reply.status(HttpStatusCode.ServiceUnavailable))
        TestLanguages.installGerman("load_add_to_my_list_failed" to "Auf Meine Liste konnte nicht gespeichert werden.")
        val viewModel = viewModels.track(TileMenuViewModel(app.library, app.watch, app.connection, app.downloadRepository, app.watchChanges))
        viewModel.onOpen(tile())
        viewModel.onToggleFavorite()

        assertEquals(LoadMessages.ADD_TO_MY_LIST_FAILED, viewModel.uiState.first { it.actionMessage != null }.actionMessage)

        Texts.select("de")
        assertEquals("Auf Meine Liste konnte nicht gespeichert werden.", viewModel.uiState.first { it.actionMessage == "Auf Meine Liste konnte nicht gespeichert werden." }.actionMessage)
    }
}
