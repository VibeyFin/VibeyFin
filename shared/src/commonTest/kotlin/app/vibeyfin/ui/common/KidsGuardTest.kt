package app.vibeyfin.ui.common

import app.vibeyfin.testing.TestApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KidsGuardTest {
    private val app = TestApp()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        Dispatchers.resetMain()
        app.close()
    }

    @Test
    fun `a profile that is not a kids one is not blocked`() = runTest {
        app.signedIn()

        assertFalse(KidsGuard(app.profileSettings).isKidsProfile())
    }

    @Test
    fun `a kids profile is blocked`() = runTest {
        app.signedIn()
        app.profileSettings.setKidsProfile(true)

        assertTrue(KidsGuard(app.profileSettings).isKidsProfile())
    }
}
