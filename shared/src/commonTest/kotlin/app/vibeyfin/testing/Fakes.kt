package app.vibeyfin.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.room3.useReaderConnection
import app.vibeyfin.data.db.VibeyFinDatabase
import app.vibeyfin.data.db.buildVibeyFinDatabase
import app.vibeyfin.data.discovery.MulticastLock
import app.vibeyfin.data.platform.DeviceInfo
import app.vibeyfin.data.vault.TokenVault
import app.vibeyfin.domain.DiscoveredServer
import app.vibeyfin.domain.DownloadFiles
import app.vibeyfin.domain.DownloadNetwork
import app.vibeyfin.domain.DownloadOutput
import app.vibeyfin.domain.DownloadScheduler
import app.vibeyfin.domain.EngineCapabilities
import app.vibeyfin.domain.LanDiscovery
import app.vibeyfin.domain.NetworkMonitor
import app.vibeyfin.domain.NetworkState
import app.vibeyfin.domain.PlaybackCapabilities
import app.vibeyfin.domain.PlaybackCapabilitiesSource
import app.vibeyfin.domain.ProgressSyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.virtualClock(start: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)): Clock = object : Clock {
    override fun now(): Instant = start + testScheduler.currentTime.milliseconds
}

/** A clock that only moves when the test says so. */
class FakeClock(private var current: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)) : Clock {
    override fun now(): Instant = current

    fun advance(by: Duration) {
        current += by
    }
}

/**
 * Reversible stand-in for the platform vault. [unreadable] makes every blob fail to open, like a
 * Keystore key that was lost; [discarded] records the blobs that were released.
 */
class FakeTokenVault : TokenVault {
    var unreadable = false
    val discarded = mutableListOf<String>()

    override fun seal(token: String): ByteArray = "sealed:$token".encodeToByteArray()

    override fun open(sealed: ByteArray): String? = if (unreadable) null else sealed.decodeToString().removePrefix("sealed:")

    override fun discard(sealed: ByteArray) {
        discarded += sealed.decodeToString()
    }
}

/** A search that answers with [servers] and then, if [hold] is set, stays "in progress" until it completes. */
class FakeLanDiscovery(
    var servers: List<DiscoveredServer> = emptyList(),
    override val isSupported: Boolean = true,
) : LanDiscovery {
    var searches = 0
        private set
    var hold: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null

    override fun discover(): Flow<DiscoveredServer> = flow {
        searches++
        failure?.let { throw it }
        servers.forEach { emit(it) }
        hold?.await()
    }
}

/** Records when the lock is held, so tests can see it taken and released around a search. */
class FakeMulticastLock : MulticastLock {
    var held = false
        private set
    var acquired = 0
        private set

    override suspend fun <T> withLock(block: suspend () -> T): T {
        held = true
        acquired++
        try {
            return block()
        } finally {
            held = false
        }
    }
}

/** A device that plays H.264 and AAC in MP4 and Matroska, unless a test says otherwise. */
class FakePlaybackCapabilities(
    var capabilities: PlaybackCapabilities = PlaybackCapabilities(
        containers = listOf("mp4", "mkv"),
        videoCodecs = listOf("h264"),
        audioCodecs = listOf("aac"),
    ),
) : PlaybackCapabilitiesSource {
    /** Set to offer several players, tried in order. */
    var engines: List<EngineCapabilities>? = null

    override fun current() = capabilities

    override fun candidates() = engines ?: super.candidates()
}

class FakeDeviceInfo(
    override val deviceName: String = "Test phone",
    override val appVersion: String = "1.0.0",
) : DeviceInfo

/**
 * Hands out one elapsed time per `markNow()`, in call order. Probes start in candidate order, so
 * `ScriptedTimeSource(100, 10)` makes the first candidate take 100 ms and the second 10 ms
 * however the requests interleave.
 */
class ScriptedTimeSource(private vararg val elapsedMillis: Long) : TimeSource {
    private var calls = 0

    override fun markNow(): TimeMark {
        val duration = elapsedMillis.getOrElse(calls++) { 0L }.milliseconds
        return object : TimeMark {
            override fun elapsedNow(): Duration = duration
        }
    }
}

/** A DataStore that lives in memory, so preferences logic can be tested without files. */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val lock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = lock.withLock { transform(state.value).also { state.value = it } }
}

/**
 * A fresh database in memory, built the same way as the real one except that its queries run on the caller's thread: like the fake
 * server, it is done by the time the code that asked resumes, so a test never races a database thread.
 */
fun inMemoryDatabase(): VibeyFinDatabase = Room.inMemoryDatabaseBuilder<VibeyFinDatabase>().buildVibeyFinDatabase(Dispatchers.Unconfined)

/** Counts rows straight from SQLite, so cascades can be checked without a DAO for every table. */
suspend fun VibeyFinDatabase.count(table: String, where: String = "1"): Int = useReaderConnection { connection ->
    connection.usePrepared("SELECT COUNT(*) FROM $table WHERE $where") { statement ->
        statement.step()
        statement.getLong(0).toInt()
    }
}

/** A network the test switches: [state] starts connected on an unmetered connection. */
class FakeNetworkMonitor(initial: NetworkState = NetworkState.Unmetered) : NetworkMonitor {
    private val current = MutableStateFlow(initial)
    override val state: StateFlow<NetworkState> = current

    fun set(state: NetworkState) {
        current.value = state
    }
}

/** The downloads folder in memory: bytes are kept as text (a test writes plain ASCII), so a file can be read back as a string. */
class FakeDownloadFiles(
    /** What [freeBytes] answers; lower it to run out of room. */
    var free: Long = 10_000_000_000,
) : DownloadFiles {
    val contents = mutableMapOf<String, StringBuilder>()

    /** When set, writing throws it, as a full disk does. */
    var failWrites: Exception? = null

    /** Called after each write, for a test that lets time pass while bytes arrive. */
    var onWrite: (() -> Unit)? = null

    fun text(path: String): String? = contents[path]?.toString()

    override fun length(path: String): Long = contents[path]?.length?.toLong() ?: 0

    override fun exists(path: String) = path in contents

    override fun open(path: String, append: Boolean): DownloadOutput {
        if (!append || path !in contents) contents[path] = StringBuilder()
        val target = contents.getValue(path)
        return object : DownloadOutput {
            override fun write(bytes: ByteArray, count: Int) {
                failWrites?.let { throw it }
                target.append(bytes.decodeToString(0, count))
                onWrite?.invoke()
            }

            override fun close() = Unit
        }
    }

    override fun move(from: String, to: String) {
        contents[to] = contents.remove(from) ?: error("No file at $from")
    }

    override fun delete(path: String) {
        contents.keys.filter { it == path || it.startsWith("$path/") }.forEach { contents.remove(it) }
    }

    override fun freeBytes() = free

    override fun uri(path: String) = "file:///fake/$path"
}

/** Records what the platform was asked to run, and when. */
class FakeDownloadScheduler(override val isSupported: Boolean = true) : DownloadScheduler {
    val requests = mutableListOf<DownloadNetwork>()

    override fun schedule(network: DownloadNetwork) {
        requests += network
    }
}

/** Counts the times the platform was asked to send the offline progress. */
class FakeProgressSyncScheduler : ProgressSyncScheduler {
    var scheduled = 0
        private set

    override fun schedule() {
        scheduled++
    }
}
