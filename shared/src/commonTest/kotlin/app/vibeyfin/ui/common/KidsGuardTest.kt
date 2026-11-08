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
import kotlin.test.assertEquals
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
    fun `a profile that is not a kids one runs the action`() = runTest {
        app.signedIn()
        val guard = KidsGuard(app.profileSettings)
        var ran = 0

        guard.unlessKids { ran++ }

        assertFalse(guard.isKidsProfile())
        assertEquals(1, ran)
    }

    @Test
    fun `a kids profile is blocked and the action does not run`() = runTest {
        app.signedIn()
        app.profileSettings.setKidsProfile(true)
        val guard = KidsGuard(app.profileSettings)
        var ran = 0

        guard.unlessKids { ran++ }

        assertTrue(guard.isKidsProfile())
        assertEquals(0, ran)
    }
}
