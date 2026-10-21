package app.vibeyfin.domain

import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.testing.FakeClock
import app.vibeyfin.testing.FakeTokenVault
import app.vibeyfin.testing.count
import app.vibeyfin.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class ServerRepositoryTest {
    private lateinit var database: VibeyFinDatabase
    private lateinit var clock: FakeClock
    private lateinit var vault: FakeTokenVault
    private lateinit var servers: ServerRepository
    private lateinit var accounts: AccountRepository

    @BeforeTest
    fun setUp() {
        database = inMemoryDatabase()
        clock = FakeClock()
        vault = FakeTokenVault()
        servers = ServerRepository(database.serverDao(), database.accountDao(), vault, clock)
        accounts = AccountRepository(database.accountDao(), vault, clock)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun theAddressThatWorkedLastComesFirst() = runTest {
        servers.save("srv", "Home", "10.10.7", "http://192.168.1.20:8096")
        clock.advance(1.minutes)
        servers.save("srv", "Home", "10.10.7", "https://jelly.example.com")
        clock.advance(1.minutes)
        servers.save("srv", "Home", "10.10.7", "http://nas:8096")

        assertEquals(
            listOf("http://nas:8096", "https://jelly.example.com", "http://192.168.1.20:8096"),
            servers.get("srv")!!.addresses,
        )

        clock.advance(1.minutes)
        servers.markAddressWorked("srv", "http://192.168.1.20:8096")

        assertEquals(
            listOf("http://192.168.1.20:8096", "http://nas:8096", "https://jelly.example.com"),
            servers.get("srv")!!.addresses,
        )
    }

    @Test
    fun orderingIsTheSameInTheObservedList() = runTest {
        servers.save("srv", "Home", "10.10.7", "http://a")
        clock.advance(1.minutes)
        servers.save("srv", "Home", "10.10.7", "http://b")

        assertEquals(listOf("http://b", "http://a"), servers.servers.first().single().addresses)
    }

    @Test
    fun oneServerReachedByTwoUrlsIsOneEntryWithTwoAddresses() = runTest {
        servers.save("srv", "Home", "10.10.6", "http://192.168.1.20:8096")
        clock.advance(1.minutes)
        servers.save("srv", "Home server", "10.10.7", "https://jelly.example.com")

        val saved = servers.servers.first().single()

        assertEquals("srv", saved.id)
        assertEquals("Home server", saved.name)
        assertEquals("10.10.7", saved.version)
        assertEquals(2, saved.addresses.size)
    }

    @Test
    fun savingTheSameAddressAgainDoesNotDuplicateIt() = runTest {
        servers.save("srv", "Home", "10.10.7", "http://nas:8096")
        servers.save("srv", "Home", "10.10.7", "http://nas:8096")

        assertEquals(listOf("http://nas:8096"), servers.get("srv")!!.addresses)
    }

    @Test
    fun otherReachableAddressesAreKeptBehindTheBestOneInTheOrderGiven() = runTest {
        servers.save(
            "srv", "Home", "10.10.7", "https://nas",
            otherAddresses = listOf("http://nas:8096", "http://192.168.1.20:8096", "http://nas"),
        )

        assertEquals(
            listOf("https://nas", "http://nas:8096", "http://192.168.1.20:8096", "http://nas"),
            servers.get("srv")!!.addresses,
        )
    }

    @Test
    fun mergingIntoAKnownServerAddsTheNewBestAddressFirstAndKeepsTheRest() = runTest {
        servers.save("srv", "Home", "10.10.6", "http://192.168.1.20:8096")
        clock.advance(1.minutes)

        servers.save("srv", "Home", "10.10.7", "https://jelly.example.com", otherAddresses = listOf("http://192.168.1.20:8096"))

        val saved = servers.servers.first().single()
        assertEquals(listOf("https://jelly.example.com", "http://192.168.1.20:8096"), saved.addresses)
        assertEquals("10.10.7", saved.version)
    }

    @Test
    fun aFallbackDoesNotOverwriteWhenAKnownAddressLastWorked() = runTest {
        servers.save("srv", "Home", "10.10.7", "http://a")
        clock.advance(1.minutes)
        servers.save("srv", "Home", "10.10.7", "http://b")
        clock.advance(1.minutes)

        // "http://a" is reachable again but is not the best address this time.
        servers.save("srv", "Home", "10.10.7", "http://c", otherAddresses = listOf("http://a"))

        assertEquals(listOf("http://c", "http://b", "http://a"), servers.get("srv")!!.addresses)
    }

    @Test
    fun unknownServerIsNull() = runTest {
        assertNull(servers.get("missing"))
    }

    @Test
    fun deletingAServerRemovesItsAddressesProfilesAndSettings() = runTest {
        servers.save("srv", "Home", "10.10.7", "http://nas:8096")
        servers.save("other", "Other", "10.10.7", "http://other:8096")
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")
        accounts.saveSession("srv", "u2", "Sam", null, "token-2")
        accounts.saveSession("other", "u9", "Kim", null, "token-9")

        servers.delete("srv")

        assertNull(servers.get("srv"))
        assertEquals(emptyList(), accounts.observeAccounts("srv").first())
        assertEquals(0, database.count("server_addresses", "serverId = 'srv'"))
        assertEquals(0, database.count("account_settings", "serverId = 'srv'"))
        // The other server is untouched.
        assertEquals(1, accounts.observeAccounts("other").first().size)
        assertEquals(1, database.count("account_settings", "serverId = 'other'"))
    }

    @Test
    fun deletingAServerReleasesItsStoredTokens() = runTest {
        servers.save("srv", "Home", "10.10.7", "http://nas:8096")
        servers.save("other", "Other", "10.10.7", "http://other:8096")
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")
        accounts.saveSession("other", "u9", "Kim", null, "token-9")

        servers.delete("srv")

        assertEquals(listOf("sealed:token-1"), vault.discarded)
    }
}
