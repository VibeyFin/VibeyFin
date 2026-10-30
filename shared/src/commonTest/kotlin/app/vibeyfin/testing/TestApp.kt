package app.vibeyfin.testing

import app.vibeyfin.data.api.ClientInfoProvider
import app.vibeyfin.data.api.ServerReachability
import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.data.prefs.DataStoreDevicePreferences
import app.vibeyfin.domain.AccountRepository
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.BingeCounter
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.domain.DownloadSpeeds
import app.vibeyfin.domain.DownloadWake
import app.vibeyfin.domain.LanguagePreferences
import app.vibeyfin.domain.LibraryRepository
import app.vibeyfin.domain.LocalPlaybackReporter
import app.vibeyfin.domain.OfflineLibrary
import app.vibeyfin.domain.OfflineMode
import app.vibeyfin.domain.OfflineProgress
import app.vibeyfin.domain.OfflineSync
import app.vibeyfin.domain.PROGRESS_INTERVAL
import app.vibeyfin.domain.PendingReports
import app.vibeyfin.domain.PinLock
import app.vibeyfin.domain.PlaybackRecovery
import app.vibeyfin.domain.PlaybackReporter
import app.vibeyfin.domain.PlaybackRepository
import app.vibeyfin.domain.ProfilePolicy
import app.vibeyfin.domain.ProfileSettings
import app.vibeyfin.domain.SearchHistory
import app.vibeyfin.domain.ServerConnector
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.StartupRouter
import app.vibeyfin.domain.WatchChanges
import app.vibeyfin.domain.WatchRepository
import app.vibeyfin.domain.hashPin
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
    val reachability = ServerReachability()
    val jellyfin = FakeJellyfin(reachability = reachability)

    val servers = ServerRepository(database.serverDao(), database.accountDao(), vault, clock)
    val accounts = AccountRepository(database.accountDao(), vault, clock)
    val preferences = DataStoreDevicePreferences(InMemoryPreferencesDataStore())
    val connector = ServerConnector(servers, jellyfin.apiFactory)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val connection = ConnectionManager(connector, servers, accounts, preferences, jellyfin.unauthorized, scope)
    val auth = AuthRepository(connector, accounts, connection)
    val pins = PinLock(database.accountDao(), connection, clock)
    val policy = ProfilePolicy(connection)
    val languages = LanguagePreferences(connection, database.accountDao())
    val router = StartupRouter(servers, accounts, preferences, connection, pins)
    val library = LibraryRepository(connection)
    val searchHistory = SearchHistory(InMemoryPreferencesDataStore(), connection)
    val watchChanges = WatchChanges()
    val watch = WatchRepository(connection, watchChanges)
    val capabilities = FakePlaybackCapabilities()
    val profileSettings = ProfileSettings(database.accountDao(), connection)
    val binge = BingeCounter()
    val playback = PlaybackRepository(connection, capabilities, ClientInfoProvider(FakeDeviceInfo(), preferences), profileSettings)

    val network = FakeNetworkMonitor()
    val offline = OfflineMode(network, preferences, scope)
    val downloads = database.downloadDao()
    val files = FakeDownloadFiles()
    val scheduler = FakeDownloadScheduler()
    val wake = DownloadWake()
    val speeds = DownloadSpeeds()
    val downloadRepository = DownloadRepository(downloads, connection, files, scheduler, wake, languages, clock, speeds)
    val runner = DownloadRunner(downloads, database.accountDao(), connection, files, network, wake, clock, speeds = speeds)

    val pending = PendingReports(database.pendingReportDao(), clock)
    val offlineLibrary = OfflineLibrary(downloads, files, languages)
    val offlineProgress = OfflineProgress(downloads, pending, clock)
    val offlineSync = OfflineSync(pending, downloads, connection, watchChanges)
    val progressScheduler = FakeProgressSyncScheduler()
    val recovery = PlaybackRecovery(pending, connection)

    /** The reporter a download plays with, wired like the app's, working on [scope], which the test cancels before closing the app. */
    fun localReporter(scope: CoroutineScope, interval: Duration = PROGRESS_INTERVAL) = LocalPlaybackReporter(connection, scope, offlineProgress, offlineSync, progressScheduler, watchChanges, interval)

    /** A reporter wired like the app's, working on [scope], which the test cancels before closing the app. */
    fun reporter(scope: CoroutineScope, interval: Duration = PROGRESS_INTERVAL) = PlaybackReporter(connection, scope, pending, recovery, watchChanges, interval)

    /** Saves a server reachable at [address] and returns its fake. */
    suspend fun serverAt(address: String, id: String = "srv-1", name: String = "Home"): FakeServer {
        servers.save(id, name, "10.10.7", address)
        return jellyfin.server(address)
    }

    /**
     * Saves a server and a profile with a session, and signs in as it ("Alex" on "Home", token-1).
     */
    suspend fun signedIn(address: String = "http://nas:8096"): FakeServer {
        val server = serverAt(address)
        server.on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex"}"""))
        accounts.saveSession("srv-1", "u1", "Alex", null, "token-1")
        connection.activate("srv-1", "u1")
        return server
    }

    /** Gives a saved profile a [pin] without signing in as it; the switches keep their stored values (on by default). */
    suspend fun setPin(userId: String, pin: String, serverId: String = "srv-1") {
        val salt = ByteArray(16) { it.toByte() }
        database.accountDao().setPin(serverId, userId, hashPin(salt, pin), salt)
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
fun authJson(userId: String = "u1", name: String = "Alex", token: String = "token-1", imageTag: String? = null) = """{"User":{"Id":"$userId","Name":"$name","HasPassword":true,"PrimaryImageTag":${imageTag?.let { "\"$it\"" } ?: "null"}},""" +
    """"AccessToken":"$token","ServerId":"srv-1","SessionInfo":{"Id":"s1"}}"""

fun publicUsersJson(vararg users: Triple<String, String, Boolean>) = users.joinToString(prefix = "[", postfix = "]") { (id, name, hasPassword) ->
    """{"Id":"$id","Name":"$name","HasPassword":$hasPassword,"PrimaryImageTag":null}"""
}

fun quickConnectJson(authenticated: Boolean, secret: String = "sec", code: String = "123456") = """{"Authenticated":$authenticated,"Secret":"$secret","Code":"$code"}"""
