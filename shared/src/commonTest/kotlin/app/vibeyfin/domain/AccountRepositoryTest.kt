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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AccountRepositoryTest {
    private lateinit var database: VibeyFinDatabase
    private lateinit var clock: FakeClock
    private lateinit var vault: FakeTokenVault
    private lateinit var accounts: AccountRepository

    @BeforeTest
    fun setUp() = runTest {
        database = inMemoryDatabase()
        clock = FakeClock()
        vault = FakeTokenVault()
        accounts = AccountRepository(database.accountDao(), vault, clock)
        ServerRepository(database.serverDao(), database.accountDao(), vault, clock)
            .save("srv", "Home", "10.10.7", "http://nas:8096")
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun aSavedSessionCanBeReadBack() = runTest {
        accounts.saveSession("srv", "u1", "Alex", "tag-1", "token-1")

        assertEquals("token-1", accounts.sessionToken("srv", "u1"))
        val account = accounts.observeAccounts("srv").first().single()
        assertEquals("Alex", account.name)
        assertEquals("tag-1", account.imageTag)
        assertTrue(account.hasSession)
    }

    @Test
    fun theDatabaseNeverHoldsThePlainToken() = runTest {
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")

        val stored = database.accountDao().getAccount("srv", "u1")!!.sealedToken!!

        assertEquals("sealed:token-1", stored.decodeToString())
    }

    @Test
    fun expiringKeepsTheProfileButLocksIt() = runTest {
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")

        accounts.expire("srv", "u1")

        val account = accounts.observeAccounts("srv").first().single()
        assertEquals("Alex", account.name)
        assertFalse(account.hasSession)
        assertNull(accounts.sessionToken("srv", "u1"))
        assertEquals(listOf("sealed:token-1"), vault.discarded)
    }

    @Test
    fun expiringKeepsTheProfileSettings() = runTest {
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")

        accounts.expire("srv", "u1")

        assertEquals(1, database.count("account_settings", "userId = 'u1'"))
    }

    @Test
    fun signingInAgainUnlocksTheSameProfile() = runTest {
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")
        accounts.expire("srv", "u1")

        accounts.saveSession("srv", "u1", "Alex B", "tag-2", "token-2")

        val account = accounts.observeAccounts("srv").first().single()
        assertEquals("Alex B", account.name)
        assertEquals("tag-2", account.imageTag)
        assertTrue(account.hasSession)
        assertEquals("token-2", accounts.sessionToken("srv", "u1"))
        assertEquals(1, database.count("account_settings", "userId = 'u1'"))
    }

    @Test
    fun replacingATokenReleasesTheOldOne() = runTest {
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")

        accounts.saveSession("srv", "u1", "Alex", null, "token-2")

        assertEquals(listOf("sealed:token-1"), vault.discarded)
    }

    @Test
    fun anUnreadableTokenIsTreatedAsExpiredAndCleared() = runTest {
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")
        vault.unreadable = true

        assertNull(accounts.sessionToken("srv", "u1"))

        val account = accounts.observeAccounts("srv").first().single()
        assertFalse(account.hasSession)
        assertNull(database.accountDao().getAccount("srv", "u1")!!.sealedToken)
        // Even if the vault recovers, the cleared token does not come back.
        vault.unreadable = false
        assertNull(accounts.sessionToken("srv", "u1"))
    }

    @Test
    fun aProfileWithoutATokenHasNoSession() = runTest {
        assertNull(accounts.sessionToken("srv", "nobody"))
    }

    @Test
    fun profilesAreOrderedByLastUse() = runTest {
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")
        clock.advance(1.minutes)
        accounts.saveSession("srv", "u2", "Sam", null, "token-2")
        clock.advance(1.minutes)
        accounts.saveSession("srv", "u3", "Kim", null, "token-3")

        assertEquals(listOf("u3", "u2", "u1"), accounts.userIds())

        clock.advance(1.minutes)
        accounts.markUsed("srv", "u1")

        assertEquals(listOf("u1", "u3", "u2"), accounts.userIds())
    }

    @Test
    fun signingBackInCountsAsUse() = runTest {
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")
        clock.advance(1.minutes)
        accounts.saveSession("srv", "u2", "Sam", null, "token-2")
        clock.advance(1.minutes)

        accounts.saveSession("srv", "u1", "Alex", null, "token-3")

        assertEquals(listOf("u1", "u2"), accounts.userIds())
    }

    @Test
    fun profilesBelongToTheirServer() = runTest {
        ServerRepository(database.serverDao(), database.accountDao(), vault, clock)
            .save("other", "Other", "10.10.7", "http://other:8096")
        accounts.saveSession("srv", "u1", "Alex", null, "token-1")
        accounts.saveSession("other", "u1", "Alex elsewhere", null, "token-9")

        assertEquals("token-1", accounts.sessionToken("srv", "u1"))
        assertEquals("token-9", accounts.sessionToken("other", "u1"))
        assertEquals(listOf("Alex"), accounts.observeAccounts("srv").first().map { it.name })
    }

    private suspend fun AccountRepository.userIds() = observeAccounts("srv").first().map { it.userId }

    @Test
    fun countsTheProfilesSavedForEachServer() = runTest {
        ServerRepository(database.serverDao(), database.accountDao(), vault, clock).save("srv-2", "Cabin", "10.10.7", "http://cabin:8096")
        accounts.saveSession("srv", "u1", "Alex", null, "t1")
        accounts.saveSession("srv", "u2", "Kim", null, "t2")
        accounts.saveSession("srv-2", "u1", "Alex", null, "t3")

        assertEquals(mapOf("srv" to 2, "srv-2" to 1), accounts.observeProfileCounts().first())
    }
}
