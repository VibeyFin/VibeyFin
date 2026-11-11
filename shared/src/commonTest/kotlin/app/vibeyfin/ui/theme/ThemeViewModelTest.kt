package app.vibeyfin.ui.theme

import app.vibeyfin.domain.ThemeIds
import app.vibeyfin.testing.TestApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeViewModelTest {
    private val app: TestApp = TestApp()

    @AfterTest
    fun tearDown() = runTest {
        Dispatchers.resetMain()
        app.close()
    }

    @Test
    fun `the theme is the default while nobody is signed in and for a profile that never chose one`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel: ThemeViewModel = ThemeViewModel(app.profileSettings)

        assertEquals(ThemeIds.DEFAULT, viewModel.themeId.value)

        app.signedIn()

        assertEquals(ThemeIds.DEFAULT, viewModel.themeId.first())
    }
}
