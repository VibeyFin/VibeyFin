package com.liamgleeson.vibeyfin.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ServerDao {
    @Query("SELECT * FROM servers ORDER BY addedAt, id")
    abstract fun observeServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM server_addresses")
    abstract fun observeAddresses(): Flow<List<ServerAddressEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    abstract suspend fun getServer(id: String): ServerEntity?

    @Query("SELECT * FROM server_addresses WHERE serverId = :serverId")
    abstract suspend fun getAddresses(serverId: String): List<ServerAddressEntity>

    @Query("UPDATE server_addresses SET lastSuccessAt = :at WHERE serverId = :serverId AND url = :url")
    abstract suspend fun markAddressWorked(serverId: String, url: String, at: Long)

    @Query("DELETE FROM servers WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertServer(server: ServerEntity): Long

    @Query("UPDATE servers SET name = :name, version = :version WHERE id = :id")
    protected abstract suspend fun updateServerInfo(id: String, name: String, version: String?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAddress(address: ServerAddressEntity): Long

    /**
     * Saves a server that was just reached at [url]. A known server keeps its row and gains the
     * address (or refreshes it), so one server reached by IP and by domain stays one entry.
     *
     * [fallbackUrls] also reached the server. They are stored behind [url] in the order given and
     * never overwrite what a known address already records.
     */
    @Transaction
    open suspend fun save(
        id: String,
        name: String,
        version: String?,
        url: String,
        now: Long,
        fallbackUrls: List<String> = emptyList(),
    ) {
        insertServer(ServerEntity(id = id, name = name, version = version, addedAt = now))
        updateServerInfo(id, name, version)
        // Offsetting addedAt by a millisecond each keeps the fallbacks in the order they were given.
        fallbackUrls.forEachIndexed { index, fallback ->
            insertAddress(ServerAddressEntity(serverId = id, url = fallback, addedAt = now + index + 1, lastSuccessAt = null))
        }
        insertAddress(ServerAddressEntity(serverId = id, url = url, addedAt = now, lastSuccessAt = now))
        markAddressWorked(id, url, now)
    }
}
