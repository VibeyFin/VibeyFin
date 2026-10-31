package app.vibeyfin.ui.seerr

import app.vibeyfin.domain.SeerrState
import app.vibeyfin.testing.FakeSeerr
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrSettingsViewModelTest {
    private val app = TestApp()
    private val viewModels = ViewModelTracker()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val address = "http://seerr:5055"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        applicationScope.cancel()
        Dispatchers.resetMain()
        app.close()
    }

    // Alex signed in, a Seerr at [address] linked to the server but not yet connected, and the page open on it.
    private suspend fun page(): Pair<SeerrSettingsViewModel, FakeSeerr> {
        val jellyfin = app.signedIn()
        val seerr = FakeSeerr(app.jellyfin.server(address), jellyfin)
        val viewModel = viewModels.track(SeerrSettingsViewModel(app.seerr, app.seerrRepository, app.connection, applicationScope))
        await(viewModel) { it.layout == SeerrSettingsLayout.Setup }
        return viewModel to seerr
    }

    private suspend fun await(viewModel: SeerrSettingsViewModel, predicate: (SeerrSettingsUiState) -> Boolean): SeerrSettingsUiState = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { viewModel.uiState.first(predicate) }
    }

    @Test
    fun withoutSeerrThePageAsksForTheAddressAndConnectingSignsInByItself() = runTest {
        val (viewModel, seerr) = page()
        seerr.onSignedIn("GET", "/api/v1/user/7/quota") {
            Reply.json("""{"movie":{"days":7,"limit":10,"used":7,"remaining":3},"tv":{"days":7,"limit":5,"used":3,"remaining":2}}""")
        }
        val empty = viewModel.uiState.value
        assertEquals("", empty.address)
        assertFalse(empty.addressShared)

        viewModel.onAddressChange(address)
        viewModel.onConnect()

        val connected = await(viewModel) { it.layout == SeerrSettingsLayout.Connected && it.version != null }
        assertEquals("Alex", connected.userName)
        assertEquals(address, connected.savedAddress)
        assertEquals("3.5.1", connected.version)
        assertFalse(connected.signedInWithPassword)
        assertEquals("3 movies, 2 seasons", connected.quotaLeft)
        assertEquals("Every week", connected.quotaPeriod)
        assertFalse(connected.connecting)
    }

    @Test
    fun connectUsesWhatWasJustTypedBeforeTheStateHasCaughtUp() = runTest {
        val jellyfin = app.signedIn()
        FakeSeerr(app.jellyfin.server(address), jellyfin)
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.seerr.state.first { it == SeerrState.NotSet } } }
        // As on a device: the ViewModel's state is worked out after the call that changed it returns, not during it.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModels.track(SeerrSettingsViewModel(app.seerr, app.seerrRepository, app.connection, applicationScope))

        viewModel.onAddressChange(address)
        viewModel.onConnect()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.addressError)
        assertTrue(app.seerr.state.value is SeerrState.Ready)
    }

    @Test
    fun noQuotaShowsNoRequestsLeft() = runTest {
        val (viewModel, seerr) = page()
        seerr.onSignedIn("GET", "/api/v1/user/7/quota") { Reply.json("""{"movie":{"used":0,"restricted":false},"tv":{"used":0,"restricted":false}}""") }

        viewModel.onAddressChange(address)
        viewModel.onConnect()

        val connected = await(viewModel) { it.layout == SeerrSettingsLayout.Connected && it.version != null }
        assertNull(connected.quotaLeft)
        assertNull(connected.quotaPeriod)
    }

    @Test
    fun anAddressThatDoesNotWorkSaysWhyUnderTheFieldAndTypingClearsIt() = runTest {
        val (viewModel, _) = page()

        viewModel.onConnect()
        assertEquals(SeerrMessages.INVALID_ADDRESS, viewModel.uiState.value.addressError)

        viewModel.onAddressChange("http://nothing-here:5055")
        viewModel.onConnect()
        val failed = await(viewModel) { it.addressError != null }
        assertEquals(SeerrMessages.UNREACHABLE_ADDRESS, failed.addressError)
        // What was typed stays.
        assertEquals("http://nothing-here:5055", failed.address)
        assertEquals(SeerrSettingsLayout.Setup, failed.layout)

        viewModel.onAddressChange("http://nothing-here:5056")
        assertNull(viewModel.uiState.value.addressError)
    }

    @Test
    fun withQuickConnectOffThePasswordSheetSignsInOnceAndSaysWhenThePasswordIsWrong() = runTest {
        val (viewModel, seerr) = page()
        seerr.quickConnectOnJellyfin = false
        viewModel.onAddressChange(address)
        viewModel.onConnect()

        val notSignedIn = await(viewModel) { it.layout == SeerrSettingsLayout.NotSignedIn }
        assertEquals(SeerrMessages.QUICK_CONNECT_OFF, notSignedIn.problem)
        assertEquals(SeerrFix.SignInWithPassword, notSignedIn.fix)

        viewModel.onSignInWithPassword()
        assertEquals("Alex", viewModel.uiState.value.passwordSheet?.userName)

        viewModel.onSubmitPassword("wrong")
        assertEquals(SeerrMessages.WRONG_PASSWORD, await(viewModel) { it.passwordSheet?.error != null }.passwordSheet?.error)

        viewModel.onSubmitPassword("secret")
        val connected = await(viewModel) { it.layout == SeerrSettingsLayout.Connected }
        assertNull(connected.passwordSheet)
        assertTrue(connected.signedInWithPassword)
    }

    @Test
    fun disconnectAsksFirstThenOffersTheServersAddressAgain() = runTest {
        val (viewModel, _) = page()
        viewModel.onAddressChange(address)
        viewModel.onConnect()
        await(viewModel) { it.layout == SeerrSettingsLayout.Connected }

        viewModel.onDisconnect()
        assertTrue(viewModel.uiState.value.confirmDisconnect)
        viewModel.onDismissDisconnect()
        assertFalse(viewModel.uiState.value.confirmDisconnect)

        viewModel.onDisconnect()
        viewModel.onConfirmDisconnect()

        val off = await(viewModel) { it.layout == SeerrSettingsLayout.Setup }
        assertEquals(address, off.address)
        assertTrue(off.addressShared)
        assertTrue(app.seerr.state.value is SeerrState.Off)

        // Connect turns it back on.
        viewModel.onConnect()
        await(viewModel) { it.layout == SeerrSettingsLayout.Connected }
    }

    @Test
    fun aSeerrThatCannotBeReachedOffersTryAgainAndChangeAddress() = runTest {
        val (viewModel, seerr) = page()
        viewModel.onAddressChange(address)
        viewModel.onConnect()
        await(viewModel) { it.layout == SeerrSettingsLayout.Connected }
        // Seerr goes down, and the profile comes back after a switch.
        seerr.server.refuseConnections()
        app.connection.switchProfile()
        app.connection.activate("srv-1", "u1")

        val down = await(viewModel) { it.layout == SeerrSettingsLayout.NotSignedIn }
        assertEquals(SeerrMessages.UNREACHABLE_NOW, down.problem)
        assertEquals(SeerrFix.TryAgain, down.fix)

        seerr.server.refuseConnections(false)
        viewModel.onRetry()
        await(viewModel) { it.layout == SeerrSettingsLayout.Connected }
    }

    @Test
    fun changeAddressBringsTheFieldBackFilledIn() = runTest {
        val (viewModel, seerr) = page()
        seerr.quickConnectOnJellyfin = false
        viewModel.onAddressChange(address)
        viewModel.onConnect()
        await(viewModel) { it.layout == SeerrSettingsLayout.NotSignedIn }

        viewModel.onChangeAddress()

        val editing = viewModel.uiState.value
        assertEquals(SeerrSettingsLayout.Setup, editing.layout)
        assertEquals(address, editing.address)
        assertTrue(editing.addressShared)
    }

    @Test
    fun turningKidsModeOnWhileThePageIsOpenLeavesIt() = runTest {
        val (viewModel, _) = page()
        viewModel.onAddressChange(address)
        viewModel.onConnect()
        await(viewModel) { it.layout == SeerrSettingsLayout.Connected }

        app.profileSettings.setKidsProfile(true)

        assertTrue(await(viewModel) { it.leave }.leave)
    }
}
