package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerRemovalTest {
    private val app = TestApp()

    @AfterTest
    fun tearDown() = runTest { app.close() }

    @Test
    fun removingAServerForgetsItsProfilesTokensAndSearches() = runTest {
        app.signedIn().on("GET", "/Search/Hints", Reply.json("""{"SearchHints":[]}"""))
        app.searchHistory.record("harbour")
        app.preferences.setLastServer("srv-1")
        app.preferences.setLastProfile(ProfileRef("srv-1", "u1"))

        app.serverRemoval.remove("srv-1")

        assertNull(app.servers.get("srv-1"))
        assertNull(app.accounts.account("srv-1", "u1"))
        assertTrue(app.vault.discarded.isNotEmpty())
        assertNull(app.preferences.lastServerId.first())
        assertEquals(ConnectionState.Disconnected, app.connection.state.value)
        app.servers.save("srv-1", "Home", "10.10.7", "http://nas:8096")
        app.accounts.saveSession("srv-1", "u1", "Alex", null, "token-2")
        app.connection.activate("srv-1", "u1")
        assertEquals(emptyList(), app.searchHistory.entries.first())
    }

    @Test
    fun otherServersAreLeftAlone() = runTest {
        app.serverAt("http://nas:8096")
        app.servers.save("srv-2", "Cabin", "10.10.7", "http://cabin:8096")
        app.preferences.setLastServer("srv-2")

        app.serverRemoval.remove("srv-1")

        assertEquals(listOf("srv-2"), app.servers.servers.first().map { it.id })
        assertEquals("srv-2", app.preferences.lastServerId.first())
    }
}
