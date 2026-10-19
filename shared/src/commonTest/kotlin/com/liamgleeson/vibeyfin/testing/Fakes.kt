package com.liamgleeson.vibeyfin.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.room3.useReaderConnection
import com.liamgleeson.vibeyfin.data.db.VibeyFinDatabase
import com.liamgleeson.vibeyfin.data.db.buildVibeyFinDatabase
import com.liamgleeson.vibeyfin.data.platform.DeviceInfo
import com.liamgleeson.vibeyfin.data.vault.TokenVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
