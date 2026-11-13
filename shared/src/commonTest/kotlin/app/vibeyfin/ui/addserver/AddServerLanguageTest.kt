package app.vibeyfin.ui.addserver

import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.domain.ConnectionIssue
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.FakeClock
import app.vibeyfin.testing.FakeTokenVault
import app.vibeyfin.testing.MockJellyfinNetwork
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.inMemoryDatabase
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
class AddServerLanguageTest {
    private lateinit var database: VibeyFinDatabase
    private lateinit var servers: ServerRepository
    private val viewModels = ViewModelTracker()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = inMemoryDatabase()
        servers = ServerRepository(database.serverDao(), database.accountDao(), FakeTokenVault(), FakeClock())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        database.close()
        TestLanguages.reset()
    }

    @Test
    fun theFailureMessageFollowsTheLanguage() = runTest {
        TestLanguages.installGerman("connection_issue_unreachable" to "Der Server ist nicht erreichbar.")
        val viewModel = viewModels.track(AddServerViewModel(null, MockJellyfinNetwork().resolver(), servers))
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onAddressChange("nas")
        viewModel.onConnect()
        val before = viewModel.uiState.first { it.step is AddServerStep.Failed }
        assertEquals(ConnectionIssue.Unreachable.message(), (before.step as AddServerStep.Failed).message)

        Texts.select("de")

        val after = viewModel.uiState.first { it != before }
        assertEquals("Der Server ist nicht erreichbar.", (after.step as AddServerStep.Failed).message)
    }
}
