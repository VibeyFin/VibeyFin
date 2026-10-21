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
import app.vibeyfin.domain.LanDiscovery
import app.vibeyfin.domain.PlaybackCapabilities
import app.vibeyfin.domain.PlaybackCapabilitiesSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

    override fun open(sealed: ByteArray): String? =
        if (unreadable) null else sealed.decodeToString().removePrefix("sealed:")

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
    override fun current() = capabilities
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

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        lock.withLock { transform(state.value).also { state.value = it } }
}

/** A fresh database in memory, built the same way as the real one. */
fun inMemoryDatabase(): VibeyFinDatabase =
    Room.inMemoryDatabaseBuilder<VibeyFinDatabase>().buildVibeyFinDatabase()

/** Counts rows straight from SQLite, so cascades can be checked without a DAO for every table. */
suspend fun VibeyFinDatabase.count(table: String, where: String = "1"): Int =
    useReaderConnection { connection ->
        connection.usePrepared("SELECT COUNT(*) FROM $table WHERE $where") { statement ->
            statement.step()
            statement.getLong(0).toInt()
        }
    }
