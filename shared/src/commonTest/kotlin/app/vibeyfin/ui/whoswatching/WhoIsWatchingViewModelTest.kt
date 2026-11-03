package app.vibeyfin.ui.whoswatching

import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import app.vibeyfin.testing.ViewModelTracker
import app.vibeyfin.testing.authJson
import app.vibeyfin.testing.publicUsersJson
import app.vibeyfin.testing.quickConnectJson
import app.vibeyfin.testing.virtualClock
import app.vibeyfin.ui.common.PinReason
import app.vibeyfin.ui.common.Strings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class WhoIsWatchingViewModelTest {
    private val app = TestApp()
    private val address = "http://nas:8096"
    private lateinit var server: FakeServer

    @BeforeTest
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = app.serverAt(address)
        server.on("GET", "/Users/Public", Reply.json(publicUsersJson(Triple("u1", "Alex", true), Triple("u2", "Kim", false), Triple("u3", "Sam", true))))
        server.on("GET", "/QuickConnect/Enabled", Reply.json("false"))
        // "right" is the password for everyone; Kim has none.
        server.on("POST", "/Users/AuthenticateByName") { request ->
            val name = Regex(""""Username":"([^"]*)"""").find(request.body)!!.groupValues[1]
            val password = Regex(""""Pw":"([^"]*)"""").find(request.body)!!.groupValues[1]
            val id = mapOf("alex" to "u1", "kim" to "u2", "sam" to "u3").getValue(name.lowercase())
            if ((name.lowercase() == "kim" && password == "") || password == "right") {
                Reply.json(authJson(userId = id, name = name.replaceFirstChar { it.uppercase() }, token = "token-$id"))
            } else {
                Reply.status(HttpStatusCode.Unauthorized)
            }
        }
    }

    private val viewModels = ViewModelTracker()

    @AfterTest
    fun tearDown() = runTest {
        viewModels.stopAll()
        Dispatchers.resetMain()
        app.close()
    }

    private fun TestScope.viewModel(pinFor: ProfileRef? = null): WhoIsWatchingViewModel {
        val clock = virtualClock()
        val auth = AuthRepository(app.connector, app.accounts, app.connection, clock)
        return viewModels.track(WhoIsWatchingViewModel("srv-1", app.servers, app.accounts, auth, app.connection, app.pins, clock, pinFor))
    }

    private suspend fun WhoIsWatchingViewModel.loaded() = uiState.first { !it.loading }

    @Test
    fun showsTheServersUsersAsTiles() = runTest {
        val state = viewModel().loaded()

        assertEquals("Home", state.serverName)
        assertEquals(listOf("Alex", "Kim", "Sam"), state.profiles.map { it.name })
        assertEquals(listOf(true, false, true), state.profiles.map { it.needsPassword })
        assertTrue(state.serverReachable)
    }

    @Test
    fun savedProfilesComeFirst() = runTest {
        app.accounts.saveSession("srv-1", "u3", "Sam", null, "token-u3")

        assertEquals(listOf("Sam", "Alex", "Kim"), viewModel().loaded().profiles.map { it.name })
    }

    @Test
    fun anUnreachableServerStillShowsSavedProfiles() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        server.refuseConnections()

        val state = viewModel().loaded()

        assertFalse(state.serverReachable)
        assertEquals(listOf("Alex"), state.profiles.map { it.name })
    }

    @Test
    fun tappingAProfileWithASavedSessionSignsInWithoutAPassword() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        val viewModel = viewModel()
        val alex = viewModel.loaded().profiles.first { it.name == "Alex" }

        viewModel.onProfileTapped(alex)

        val state = viewModel.uiState.first { it.signedIn }
        assertNull(state.sheet)
        assertEquals("u1", state.openedUserId)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").size)
    }

    @Test
    fun onceSignedInAnotherTileDoesNotOpen() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        val viewModel = viewModel()
        val profiles = viewModel.loaded().profiles
        viewModel.onProfileTapped(profiles.first { it.name == "Alex" })
        viewModel.uiState.first { it.signedIn }

        viewModel.onProfileTapped(profiles.first { it.name == "Kim" })

        assertEquals("u1", viewModel.uiState.value.openedUserId)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").size)
    }

    @Test
    fun aSavedSessionTheServerRejectsFallsBackToAskingForThePassword() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "stale-token")
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()

        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Alex" })

        // The tile locks a moment after the sheet opens, once the saved list reports the cleared token.
        val state = viewModel.uiState.first { it.sheet != null && it.profiles.first { p -> p.name == "Alex" }.locked }
        assertEquals("Alex", assertIs<SignInSheet.Password>(state.sheet).profile.name)
        assertEquals(SignInMessages.SESSION_ENDED, state.sheetNotice)
        assertNull(state.error)
        assertFalse(state.signedIn)
    }

    @Test
    fun aUserWithoutAPasswordSignsInWithAnEmptyOne() = runTest {
        val viewModel = viewModel()

        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Kim" })

        assertEquals("u2", viewModel.uiState.first { it.signedIn }.openedUserId)
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").single().body.contains(""""Pw":"""""))
        assertEquals("token-u2", app.accounts.sessionToken("srv-1", "u2"))
    }

    @Test
    fun aLockedProfileOpensThePasswordSheet() = runTest {
        val viewModel = viewModel()

        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })

        val sheet = assertIs<SignInSheet.Password>(viewModel.uiState.first { it.sheet != null }.sheet)
        assertEquals("Sam", sheet.profile.name)
        assertEquals(0, app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").size)
    }

    @Test
    fun aWrongPasswordShowsAnErrorAndKeepsTheSheetOpen() = runTest {
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })

        viewModel.onSubmitPassword("wrong")

        val state = viewModel.uiState.first { !it.signingIn && it.error != null }
        assertEquals(SignInMessages.WRONG_PASSWORD, state.error)
        assertIs<SignInSheet.Password>(state.sheet)
        assertFalse(state.signedIn)
        assertNull(app.accounts.account("srv-1", "u3"))
    }

    @Test
    fun theRightPasswordAfterAWrongOneSignsInAndClosesTheSheet() = runTest {
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })
        viewModel.onSubmitPassword("wrong")
        viewModel.uiState.first { !it.signingIn && it.error != null }

        viewModel.onSubmitPassword("right")

        val state = viewModel.uiState.first { it.signedIn }
        assertNull(state.sheet)
        assertNull(state.error)
        assertEquals("u3", state.openedUserId)
        assertEquals("token-u3", app.accounts.sessionToken("srv-1", "u3"))
    }

    @Test
    fun anotherUserSignsInWithAUsernameAndPassword() = runTest {
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onOtherUserTapped()
        assertIs<SignInSheet.OtherUser>(viewModel.uiState.first { it.sheet != null }.sheet)
        viewModel.onSubmitOtherUser("  alex ", "right")

        // Not a tile, so nothing to zoom into
        assertNull(viewModel.uiState.first { it.signedIn }.openedUserId)
        assertTrue(app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").single().body.contains(""""Username":"alex""""))
    }

    @Test
    fun aWrongUsernameOrPasswordForAnotherUserSaysSoAndKeepsTheSheet() = runTest {
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onOtherUserTapped()

        viewModel.onSubmitOtherUser("alex", "wrong")

        val state = viewModel.uiState.first { !it.signingIn && it.error != null }
        assertEquals(SignInMessages.WRONG_CREDENTIALS, state.error)
        assertIs<SignInSheet.OtherUser>(state.sheet)
    }

    @Test
    fun aBlankUsernameIsNotSent() = runTest {
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onOtherUserTapped()

        viewModel.onSubmitOtherUser("   ", "right")

        assertEquals(0, app.jellyfin.requestsTo("POST", "/Users/AuthenticateByName").size)
    }

    @Test
    fun aSavedSessionWhenTheServerIsDownShowsAnErrorInsteadOfASheet() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        val viewModel = viewModel()
        val alex = viewModel.loaded().profiles.first { it.name == "Alex" }
        server.refuseConnections()

        viewModel.onProfileTapped(alex)

        val state = viewModel.uiState.first { !it.signingIn && it.error != null }
        assertEquals(SignInMessages.UNREACHABLE, state.error)
        assertNull(state.sheet)
        assertEquals("token-u1", app.accounts.sessionToken("srv-1", "u1"))
    }

    @Test
    fun closingTheSheetClearsItsError() = runTest {
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })
        viewModel.onSubmitPassword("wrong")
        viewModel.uiState.first { it.error != null }

        viewModel.onDismissSheet()

        assertNull(viewModel.uiState.value.sheet)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun theServerCanBeAskedAgainAfterItWasUnreachable() = runTest {
        server.refuseConnections()
        val viewModel = viewModel()
        assertFalse(viewModel.loaded().serverReachable)

        server.refuseConnections(false)
        viewModel.refresh()

        val state = viewModel.uiState.first { it.serverReachable }
        assertEquals(listOf("Alex", "Kim", "Sam"), state.profiles.map { it.name })
    }

    // Quick Connect

    private fun quickConnectOn(approveOnPoll: Int = 2) {
        server.on("GET", "/QuickConnect/Enabled", Reply.json("true"))
        server.on("POST", "/QuickConnect/Initiate", Reply.json(quickConnectJson(authenticated = false)))
        var polls = 0
        server.on("GET", "/QuickConnect/Connect") { Reply.json(quickConnectJson(authenticated = ++polls >= approveOnPoll)) }
        server.on("POST", "/Users/AuthenticateWithQuickConnect", Reply.json(authJson(userId = "u9", name = "Quinn", token = "qc-token")))
    }

    @Test
    fun quickConnectIsOfferedOnlyWhenTheServerHasItOn() = runTest {
        assertFalse(viewModel().loaded().quickConnectAvailable)
        quickConnectOn()

        assertTrue(viewModel().loaded().quickConnectAvailable)
    }

    @Test
    fun quickConnectShowsACodeAndSignsInWhenItIsApproved() = runTest {
        quickConnectOn(approveOnPoll = 2)
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onQuickConnectTapped()

        val sheet = viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.code != null }.sheet
        assertEquals("123456", assertIs<SignInSheet.QuickConnect>(sheet).code)
        val done = viewModel.uiState.first { it.signedIn }
        assertNull(done.sheet)
        assertEquals("qc-token", app.accounts.sessionToken("srv-1", "u9"))
    }

    @Test
    fun anExpiredCodeSaysSoAndCanBeTriedAgain() = runTest {
        quickConnectOn(approveOnPoll = Int.MAX_VALUE)
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onQuickConnectTapped()
        val expired = viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.message != null }
        val stopped = assertIs<SignInSheet.QuickConnect>(expired.sheet)
        assertEquals(SignInMessages.QUICK_CONNECT_EXPIRED, stopped.message)
        assertEquals(QuickConnectStop.Expired, stopped.stop)
        assertEquals("123 456", stopped.shownCode)

        viewModel.onQuickConnectTapped()
        val again = viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.let { qc -> qc.code != null && qc.stop == null } == true }
        assertEquals("123456", assertIs<SignInSheet.QuickConnect>(again.sheet).code)
    }

    @Test
    fun aServerWithQuickConnectOffSaysToUseAPassword() = runTest {
        server.on("POST", "/QuickConnect/Initiate", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onQuickConnectTapped()

        val sheet = viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.message != null }.sheet
        assertEquals(SignInMessages.QUICK_CONNECT_OFF, assertIs<SignInSheet.QuickConnect>(sheet).message)
    }

    @Test
    fun closingTheQuickConnectSheetStopsPolling() = runTest {
        quickConnectOn(approveOnPoll = Int.MAX_VALUE)
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onQuickConnectTapped()
        viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.code != null }

        viewModel.onDismissSheet()
        val pollsAtClose = app.jellyfin.requestsTo("GET", "/QuickConnect/Connect").size

        assertNull(viewModel.uiState.value.sheet)
        kotlinx.coroutines.delay(10_000)
        assertEquals(pollsAtClose, app.jellyfin.requestsTo("GET", "/QuickConnect/Connect").size)
    }

    // Alex has a saved session and the PIN "1234", with every switch on.
    private suspend fun alexWithPin() {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        app.setPin("u1", "1234")
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
    }

    @Test
    fun `entering a profile with open-this-profile on asks for its PIN`() = runTest {
        alexWithPin()
        val viewModel = viewModel()
        val alex = viewModel.loaded().profiles.first { it.name == "Alex" }
        assertTrue(alex.pinRequiredToOpen)
        assertFalse(viewModel.loaded().profiles.first { it.name == "Kim" }.pinRequiredToOpen)

        viewModel.onProfileTapped(alex)
        assertEquals(PinReason.OpenProfile, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)

        viewModel.onSubmitPin("0000")
        assertEquals(Strings.pinPromptWrong, viewModel.uiState.first { it.pinPrompt?.error != null }.pinPrompt!!.error)
        viewModel.onSubmitPin("1234")

        val state = viewModel.uiState.first { it.signedIn }
        assertNull(state.pinPrompt)
        assertIs<ConnectionState.Connected>(app.connection.state.value)
    }

    @Test
    fun `a profile whose open switch is off opens without its PIN`() = runTest {
        alexWithPin()
        app.database.accountDao().setPinOpenProfile("srv-1", "u1", false)
        val viewModel = viewModel()
        val alex = viewModel.loaded().profiles.first { it.name == "Alex" }

        viewModel.onProfileTapped(alex)

        assertNull(viewModel.uiState.first { it.signedIn }.pinPrompt)
    }

    @Test
    fun `the picker at launch asks for the last profile's PIN and then lets anyone pick`() = runTest {
        alexWithPin()
        val viewModel = viewModel(pinFor = ProfileRef("srv-1", "u1"))
        assertEquals(PinReason.SwitchProfile, viewModel.uiState.first { it.pinPrompt != null }.pinPrompt!!.reason)

        viewModel.onSubmitPin("1234")

        val state = viewModel.uiState.first { it.pinPrompt == null }
        assertFalse(state.signedIn)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun `dismissing the launch PIN goes back to the last profile`() = runTest {
        alexWithPin()
        app.database.accountDao().setPinOpenProfile("srv-1", "u1", false)
        val viewModel = viewModel(pinFor = ProfileRef("srv-1", "u1"))
        viewModel.uiState.first { it.pinPrompt != null }

        viewModel.onDismissPin()

        assertTrue(viewModel.uiState.first { it.signedIn }.signedIn)
        assertEquals("u1", assertIs<ConnectionState.Connected>(app.connection.state.value).account.userId)
    }

    @Test
    fun `Forgot PIN on a tile signs in with the password and removes the PIN`() = runTest {
        alexWithPin()
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Alex" })
        viewModel.uiState.first { it.pinPrompt != null }

        viewModel.onForgotPin()
        viewModel.uiState.first { it.pinPrompt?.forgot == true }
        viewModel.onSubmitPassword("right")

        assertTrue(viewModel.uiState.first { it.signedIn }.signedIn)
        assertFalse(app.pins.hasPin("srv-1", "u1"))
    }

    @Test
    fun `dismissing the launch PIN with open-this-profile on asks for that PIN before opening`() = runTest {
        alexWithPin()
        app.preferences.setAlwaysAskWhoIsWatching(true)
        val viewModel = viewModel(pinFor = ProfileRef("srv-1", "u1"))
        viewModel.uiState.first { it.pinPrompt?.reason == PinReason.SwitchProfile }

        viewModel.onDismissPin()

        assertEquals(PinReason.OpenProfile, viewModel.uiState.first { it.pinPrompt?.reason == PinReason.OpenProfile }.pinPrompt!!.reason)
        assertFalse(viewModel.uiState.value.signedIn)
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)

        viewModel.onSubmitPin("1234")
        assertTrue(viewModel.uiState.first { it.signedIn }.signedIn)
    }

    @Test
    fun `Forgot PIN on the launch PIN signs in as the last profile and leaves the grid usable`() = runTest {
        alexWithPin()
        app.database.accountDao().setKidsProfile("srv-1", "u1", true)
        val viewModel = viewModel(pinFor = ProfileRef("srv-1", "u1"))
        viewModel.uiState.first { it.pinPrompt != null }

        viewModel.onForgotPin()
        viewModel.uiState.first { it.pinPrompt?.forgot == true }
        viewModel.onSubmitPassword("right")

        // A real sign-in as Alex: the PIN is gone, kids mode stays, and the picker stays open for whoever picks next.
        val state = viewModel.uiState.first { it.pinPrompt == null }
        assertFalse(state.signedIn)
        assertEquals("u1", assertIs<ConnectionState.Connected>(app.connection.state.value).account.userId)
        assertFalse(app.pins.hasPin("srv-1", "u1"))
        assertEquals(true, app.database.accountDao().observeKidsProfile("srv-1", "u1").first())
    }

    // The grid's own notes

    @Test
    fun aSessionThatEndedIsNamedAboveTheGridAndInItsSheet() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.Unauthorized))
        app.connection.activate("srv-1", "u1")
        val viewModel = viewModel()

        val state = viewModel.uiState.first { !it.loading && it.notice != null }
        assertEquals(Strings.sessionEndedFor("Alex"), state.notice)

        viewModel.onProfileTapped(state.profiles.first { it.name == "Alex" })
        val sheet = viewModel.uiState.first { it.sheet != null }
        assertEquals("Alex", assertIs<SignInSheet.Password>(sheet.sheet).profile.name)
        assertEquals(SignInMessages.SESSION_ENDED, sheet.sheetNotice)
    }

    @Test
    fun anUnreachableServerSaysWhichAddressesWereTried() = runTest {
        server.refuseConnections()

        val state = viewModel().loaded()

        assertEquals(listOf(address), state.triedAddresses)
        assertEquals("Can't reach Home", Strings.cantReachServer(state.serverName!!))
    }

    @Test
    fun theOpeningTileIsClearedWhenOpeningFails() = runTest {
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-u1")
        val viewModel = viewModel()
        val alex = viewModel.loaded().profiles.first { it.name == "Alex" }
        server.refuseConnections()

        viewModel.onProfileTapped(alex)

        val state = viewModel.uiState.first { it.error != null }
        assertNull(state.openingUserId)
        assertFalse(state.busy)
    }

    // Quick Connect, finished

    @Test
    fun theCodeCountsDownToItsExpiry() = runTest {
        quickConnectOn(approveOnPoll = Int.MAX_VALUE)
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.onQuickConnectTapped()
        val first = assertIs<SignInSheet.QuickConnect>(viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.code != null }.sheet)
        assertEquals(5.minutes, first.expiresIn)
        assertEquals("123 456", first.shownCode)
        assertEquals("Code 1 2 3 4 5 6", first.spokenCode)

        delay(10.seconds)

        val expected = "Waiting for approval \u00b7 code expires in 4:50"
        assertEquals(expected, viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.status == expected }.let { (it.sheet as SignInSheet.QuickConnect).status })
        viewModel.onDismissSheet()
    }

    @Test
    fun comingBackToTheAppAsksAtOnce() = runTest {
        quickConnectOn(approveOnPoll = 1)
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.onQuickConnectTapped()
        viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.code != null }

        viewModel.onAppResumed()

        assertTrue(viewModel.uiState.first { it.signedIn }.signedIn)
        assertTrue(testScheduler.currentTime < 3_000L)
    }

    @Test
    fun quickConnectTurnedOffMeanwhileTakesTheTileAway() = runTest {
        quickConnectOn()
        server.on("POST", "/QuickConnect/Initiate", Reply.status(HttpStatusCode.Unauthorized))
        val viewModel = viewModel()
        assertTrue(viewModel.loaded().quickConnectAvailable)

        viewModel.onQuickConnectTapped()

        val state = viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.stop != null }
        assertEquals(QuickConnectStop.Off, assertIs<SignInSheet.QuickConnect>(state.sheet).stop)
        assertNull(assertIs<SignInSheet.QuickConnect>(state.sheet).code)
        assertFalse(state.quickConnectAvailable)
    }

    @Test
    fun aPasswordSheetCanSwitchToQuickConnectAndBack() = runTest {
        quickConnectOn(approveOnPoll = Int.MAX_VALUE)
        val viewModel = viewModel()
        viewModel.onProfileTapped(viewModel.loaded().profiles.first { it.name == "Sam" })
        viewModel.uiState.first { it.sheet is SignInSheet.Password }

        viewModel.onQuickConnectTapped()
        viewModel.uiState.first { (it.sheet as? SignInSheet.QuickConnect)?.code != null }
        viewModel.onOtherUserTapped()
        val pollsAtSwitch = app.jellyfin.requestsTo("GET", "/QuickConnect/Connect").size

        assertIs<SignInSheet.OtherUser>(viewModel.uiState.value.sheet)
        delay(10.seconds)
        assertEquals(pollsAtSwitch, app.jellyfin.requestsTo("GET", "/QuickConnect/Connect").size)
    }
}
