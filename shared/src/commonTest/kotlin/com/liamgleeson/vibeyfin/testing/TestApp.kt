package com.liamgleeson.vibeyfin.testing

import com.liamgleeson.vibeyfin.data.api.ClientInfoProvider
import com.liamgleeson.vibeyfin.data.db.VibeyFinDatabase
import com.liamgleeson.vibeyfin.data.prefs.DataStoreDevicePreferences
import com.liamgleeson.vibeyfin.domain.AccountRepository
import com.liamgleeson.vibeyfin.domain.AuthRepository
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.LibraryRepository
import com.liamgleeson.vibeyfin.domain.PROGRESS_INTERVAL
import com.liamgleeson.vibeyfin.domain.PendingReports
import com.liamgleeson.vibeyfin.domain.PlaybackRecovery
import com.liamgleeson.vibeyfin.domain.PlaybackReporter
import com.liamgleeson.vibeyfin.domain.PlaybackRepository
import com.liamgleeson.vibeyfin.domain.ProfileSettings
import com.liamgleeson.vibeyfin.domain.ServerConnector
import com.liamgleeson.vibeyfin.domain.ServerRepository
import com.liamgleeson.vibeyfin.domain.StartupRouter
import com.liamgleeson.vibeyfin.domain.WatchChanges
import com.liamgleeson.vibeyfin.domain.WatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlin.time.Duration

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
    val router = StartupRouter(servers, accounts, preferences, connection)
    val library = LibraryRepository(connection)
    val watchChanges = WatchChanges()
    val watch = WatchRepository(connection, watchChanges)
    val capabilities = FakePlaybackCapabilities()
    val profileSettings = ProfileSettings(database.accountDao(), connection)
    val playback = PlaybackRepository(connection, capabilities, ClientInfoProvider(FakeDeviceInfo(), preferences), profileSettings)

    val pending = PendingReports(database.pendingReportDao(), clock)
    val recovery = PlaybackRecovery(pending, connection)

    /** A reporter wired like the app's, working on [scope], which the test cancels before closing the app. */
    fun reporter(scope: CoroutineScope, interval: Duration = PROGRESS_INTERVAL) =
        PlaybackReporter(connection, scope, pending, recovery, watchChanges, interval)

    /** Saves a server reachable at [address] and returns its fake. */
    suspend fun serverAt(address: String, id: String = "srv-1", name: String = "Home"): FakeServer {
        servers.save(id, name, "10.10.7", address)
        return jellyfin.server(address)
    }

    /**
     * Saves a server and a profile with a session, and signs in as it ("Alex" on "Home", token-1).
     * The profile allows transcoding unless [allowTranscoding] says otherwise, which is not how a new profile starts.
     */
    suspend fun signedIn(address: String = "http://nas:8096", allowTranscoding: Boolean = true): FakeServer {
        val server = serverAt(address)
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        connection.activate("srv-1", "u1")
        profileSettings.setAllowTranscoding(allowTranscoding)
        return server
    }

    /**
     * Stops the connection manager's background work, waits for it, then closes the database.
     * Closing while a query is still running frees SQLite's statements under it and crashes the
     * whole test JVM.
     */
    suspend fun close() {
        scope.coroutineContext.job.cancelAndJoin()
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

fun quickConnectJson(authenticated: Boolean, secret: String = "sec", code: String = "123456") =
    """{"Authenticated":$authenticated,"Secret":"$secret","Code":"$code"}"""
