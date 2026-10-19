package com.liamgleeson.vibeyfin.testing

import com.liamgleeson.vibeyfin.data.db.VibeyFinDatabase
import com.liamgleeson.vibeyfin.data.prefs.DataStoreDevicePreferences
import com.liamgleeson.vibeyfin.domain.AccountRepository
import com.liamgleeson.vibeyfin.domain.AuthRepository
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.ServerConnector
import com.liamgleeson.vibeyfin.domain.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The real repositories, connector, connection manager and auth, wired as the app wires them but
 * over an in-memory database, an in-memory DataStore and a [FakeJellyfin] network.
 */
class TestApp {
    val database: VibeyFinDatabase = inMemoryDatabase()
    val clock = FakeClock()
    val vault = FakeTokenVault()
    val jellyfin = FakeJellyfin()

    val servers = ServerRepository(database.serverDao(), database.accountDao(), vault, clock)
    val accounts = AccountRepository(database.accountDao(), vault, clock)
    val preferences = DataStoreDevicePreferences(InMemoryPreferencesDataStore())
    val connector = ServerConnector(servers, jellyfin.apiFactory)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val connection = ConnectionManager(connector, servers, accounts, preferences, jellyfin.unauthorized, scope)
    val auth = AuthRepository(connector, accounts, connection)

    /** Saves a server reachable at [address] and returns its fake. */
    suspend fun serverAt(address: String, id: String = "srv-1", name: String = "Home"): FakeServer {
        servers.save(id, name, "10.10.7", address)
        return jellyfin.server(address)
    }

    fun close() {
        scope.cancel()
        database.close()
    }
}

/** A successful `AuthenticateByName` or `AuthenticateWithQuickConnect` answer. */
fun authJson(userId: String = "u1", name: String = "Alex", token: String = "token-1", imageTag: String? = null) =
    """{"User":{"Id":"$userId","Name":"$name","HasPassword":true,"PrimaryImageTag":${imageTag?.let { "\"$it\"" } ?: "null"}},""" +
        """"AccessToken":"$token","ServerId":"srv-1","SessionInfo":{"Id":"s1"}}"""

fun publicUsersJson(vararg users: Triple<String, String, Boolean>) =
    users.joinToString(prefix = "[", postfix = "]") { (id, name, hasPassword) ->
        """{"Id":"$id","Name":"$name","HasPassword":$hasPassword,"PrimaryImageTag":null}"""
    }
