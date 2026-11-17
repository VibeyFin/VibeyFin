package app.vibeyfin.ui.children

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.itemJson
import app.vibeyfin.testing.itemsJson
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChildrenLanguageTest {
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

    private fun viewModel(parentId: String = "folder-1") = viewModels.track(ChildrenViewModel(parentId, app.library, app.connection, app.downloadRepository))

    private fun FakeServer.listOf(all: List<String>, pageSize: Int) = on("GET", "/Items") { request ->
        val start = request.query["startIndex"]!!.toInt()
        val page = all.drop(start).take(pageSize)
        Reply.json(itemsJson(*page.map { itemJson(it) }.toTypedArray(), total = all.size, startIndex = start))
    }

    @Test
    fun theEmptyFolderTextIsWrittenInTheLanguageSelectedAfterItWasShown() = runTest {
        TestLanguages.installGerman("empty_folder" to "Hier ist noch nichts.")
        app.signedIn().listOf(emptyList(), pageSize = 60)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertEquals(Strings.emptyFolder, viewModel.uiState.first { !it.loading }.emptyText)

        Texts.select("de")
        assertEquals("Hier ist noch nichts.", viewModel.uiState.first { it.emptyText == "Hier ist noch nichts." }.emptyText)
    }
}
