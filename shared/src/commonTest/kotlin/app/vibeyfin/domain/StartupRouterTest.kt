package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class StartupRouterTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    @Test
    fun withNoSavedServersTheAppOpensOnServers() = runTest {
        assertEquals(StartDestination.Servers, app.router.decide())
    }

    @Test
    fun aSavedServerWithNoLastProfileOpensWhoIsWatching() = runTest {
        app.serverAt("http://nas:8096")

        assertEquals(StartDestination.WhoIsWatching("srv-1"), app.router.decide())
    }

    @Test
    fun aLastProfileWithASessionOpensStraightOnHome() = runTest {
        app.serverAt("http://nas:8096").on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))

        assertEquals(StartDestination.Home, app.router.decide())

        assertEquals("Alex", assertIs<ConnectionState.Connected>(app.connection.state.value).account.name)
    }

    @Test
    fun startingOnHomeDoesNotWaitForTheServer() = runTest {
        app.serverAt("http://nas:8096").refuseConnections()
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))

        assertEquals(StartDestination.Home, app.router.decide())
    }

    @Test
    fun alwaysAskOpensWhoIsWatchingEvenWithASession() = runTest {
        app.serverAt("http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))
        app.preferences.setAlwaysAskWhoIsWatching(true)

        assertEquals(StartDestination.WhoIsWatching("srv-1"), app.router.decide())
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun aLastProfileWhoseSessionEndedOpensWhoIsWatching() = runTest {
        app.serverAt("http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.accounts.expire("srv-1", "u1")
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))

        assertEquals(StartDestination.WhoIsWatching("srv-1"), app.router.decide())
    }

    @Test
    fun theLastProfileOnTheSecondServerOpensThatServer() = runTest {
        app.serverAt("http://nas:8096", id = "srv-1", name = "Home")
        app.clock.advance(1.minutes)
        app.serverAt("http://office:8096", id = "srv-2", name = "Office")
            .on("GET", "/Users/Me", Reply.json("""{"Id":"u7"}"""))
        app.accounts.saveSession("srv-2", "u7", "Kim", null, "token-7")
        app.preferences.setLastProfile(ProfileRef("srv-2", "u7"))

        assertEquals(StartDestination.Home, app.router.decide())

        assertEquals("Office", assertIs<ConnectionState.Connected>(app.connection.state.value).server.name)
    }

    @Test
    fun aLastServerThatIsGoneFallsBackToASavedOne() = runTest {
        app.serverAt("http://nas:8096")
        app.preferences.setLastProfile(ProfileRef("gone", "u1"))

        assertEquals(StartDestination.WhoIsWatching("srv-1"), app.router.decide())
    }

    @Test
    fun aLastProfileThatWasNeverSavedOpensWhoIsWatching() = runTest {
        app.serverAt("http://nas:8096")
        app.preferences.setLastProfile(ProfileRef("srv-1", "nobody"))

        assertEquals(StartDestination.WhoIsWatching("srv-1"), app.router.decide())
    }

    @Test
    fun `the picker at launch asks for the last profile's PIN with Always ask on`() = runTest {
        app.serverAt("http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.setPin("u1", "1234")
        app.database.accountDao().setPinOpenProfile("srv-1", "u1", false)
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))
        app.preferences.setAlwaysAskWhoIsWatching(true)

        assertEquals(StartDestination.WhoIsWatching("srv-1", pinFor = ProfileRef("srv-1", "u1")), app.router.decide())
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun `a PIN whose switch profile is off leaves the picker open with Always ask on`() = runTest {
        app.serverAt("http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.setPin("u1", "1234")
        app.database.accountDao().setPinSwitchProfile("srv-1", "u1", false)
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))
        app.preferences.setAlwaysAskWhoIsWatching(true)

        assertEquals(StartDestination.WhoIsWatching("srv-1"), app.router.decide())
    }

    @Test
    fun `a last profile with open-this-profile on is not picked and the picker stays up`() = runTest {
        app.serverAt("http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.setPin("u1", "1234")
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))

        assertEquals(StartDestination.WhoIsWatching("srv-1"), app.router.decide())
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
    }

    @Test
    fun `a PIN with only switch profile on still reopens the profile`() = runTest {
        app.serverAt("http://nas:8096").on("GET", "/Users/Me", Reply.json("""{"Id":"u1"}"""))
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        app.setPin("u1", "1234")
        app.database.accountDao().setPinOpenProfile("srv-1", "u1", false)
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))

        assertEquals(StartDestination.Home, app.router.decide())
    }
}
