package app.vibeyfin.ui.seerr

import app.vibeyfin.i18n.Texts
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.TestLanguages
import app.vibeyfin.testing.ViewModelTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrSettingsViewModelLanguageTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        TestLanguages.reset()
        viewModels.stopAll()
        applicationScope.cancel()
        Dispatchers.resetMain()
        app.close()
    }

    private suspend fun await(viewModel: SeerrSettingsViewModel, predicate: (SeerrSettingsUiState) -> Boolean): SeerrSettingsUiState = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { viewModel.uiState.first(predicate) }
    }

    @Test
    fun theAddressErrorIsWrittenInTheLanguageSelectedAfterItWasShown() = runTest {
        TestLanguages.installGerman("seerr_message_invalid_address" to "Seerr-Adresse eingeben.")
        val viewModel = viewModels.track(SeerrSettingsViewModel(app.seerr, app.seerrRepository, app.connection, applicationScope))

        viewModel.onConnect()
        assertEquals(SeerrMessages.INVALID_ADDRESS, viewModel.uiState.value.addressError)

        Texts.select("de")
        assertEquals("Seerr-Adresse eingeben.", await(viewModel) { it.addressError == "Seerr-Adresse eingeben." }.addressError)
    }
}
