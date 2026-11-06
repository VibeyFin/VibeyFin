package app.vibeyfin.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND userId = :userId ORDER BY addedAt, itemId")
    abstract fun observeAll(serverId: String, userId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId")
    abstract fun observe(serverId: String, userId: String, itemId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId")
    abstract suspend fun find(serverId: String, userId: String, itemId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND userId = :userId ORDER BY addedAt, itemId")
    abstract suspend fun all(serverId: String, userId: String): List<DownloadEntity>

    @Query("SELECT * FROM download_items WHERE serverId = :serverId AND userId = :userId")
    abstract suspend fun allItems(serverId: String, userId: String): List<DownloadItemEntity>

    /** Every profile's downloads in [states], oldest first: what a worker starting in a cold process picks up. */
    @Query(
        "SELECT d.* FROM downloads d LEFT JOIN download_items i " +
            "ON i.serverId = d.serverId AND i.userId = d.userId AND i.itemId = d.itemId " +
            "WHERE d.state IN (:states) " +
            "ORDER BY d.addedAt, i.seasonNumber, i.episodeNumber, d.itemId",
    )
    abstract suspend fun inStates(states: List<String>): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(download: DownloadEntity)

    @Query(
        "UPDATE downloads SET state = :state, error = :error, updatedAt = :now " +
            "WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId",
    )
    abstract suspend fun setState(serverId: String, userId: String, itemId: String, state: String, error: String?, now: Long)

    /** Moves the progress on; a download that is not [DOWNLOAD_RUNNING] any more (paused, removed) is left as it is. */
    @Query(
        "UPDATE downloads SET bytesDone = :bytesDone, bytesTotal = :bytesTotal, updatedAt = :now " +
            "WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId AND state = '$DOWNLOAD_RUNNING'",
    )
    abstract suspend fun setProgress(serverId: String, userId: String, itemId: String, bytesDone: Long, bytesTotal: Long, now: Long)

    /** Remembers which version of the file the bytes so far belong to, so a resume can tell the server's file changed. */
    @Query("UPDATE downloads SET etag = :etag WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId")
    abstract suspend fun setValidator(serverId: String, userId: String, itemId: String, etag: String?)

    @Query(
        "UPDATE downloads SET state = '$DOWNLOAD_DONE', bytesDone = :bytes, bytesTotal = :bytes, filePath = :filePath, error = NULL, " +
            "updatedAt = :now, completedAt = :now WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId",
    )
    abstract suspend fun complete(serverId: String, userId: String, itemId: String, bytes: Long, filePath: String, now: Long)

    @Query("DELETE FROM downloads WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId")
    abstract suspend fun delete(serverId: String, userId: String, itemId: String)

    /** The bytes the profile's finished and partial downloads take. */
    @Query("SELECT COALESCE(SUM(bytesDone), 0) FROM downloads WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeBytesUsed(serverId: String, userId: String): Flow<Long>

    // The snapshots of items.

    @Query("SELECT * FROM download_items WHERE serverId = :serverId AND userId = :userId")
    abstract fun observeItems(serverId: String, userId: String): Flow<List<DownloadItemEntity>>

    @Query("SELECT * FROM download_items WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId")
    abstract suspend fun findItem(serverId: String, userId: String, itemId: String): DownloadItemEntity?

    @Query("UPDATE download_items SET snapshotJson = :json WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId")
    abstract suspend fun setSnapshot(serverId: String, userId: String, itemId: String, json: String)

    /** How many of the profile's downloads belong to the show; a show's snapshot goes when this reaches 0. */
    @Query("SELECT COUNT(*) FROM downloads WHERE serverId = :serverId AND userId = :userId AND seriesId = :seriesId")
    abstract suspend fun countInSeries(serverId: String, userId: String, seriesId: String): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE serverId = :serverId AND userId = :userId AND seasonId = :seasonId")
    abstract suspend fun countInSeason(serverId: String, userId: String, seasonId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertItems(items: List<DownloadItemEntity>)

    @Query("DELETE FROM download_items WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId")
    abstract suspend fun deleteItem(serverId: String, userId: String, itemId: String)

    /**
     * The movies and episodes the profile has finished downloading whose title, show or cast contains [pattern]'s
     * text (already lower-cased, with `%` around it, and `%`, `_` and `\` in the text itself escaped with `\`), by title.
     */
    @Query(
        "SELECT i.* FROM download_items i JOIN downloads d " +
            "ON d.serverId = i.serverId AND d.userId = i.userId AND d.itemId = i.itemId " +
            "WHERE i.serverId = :serverId AND i.userId = :userId AND d.state = '$DOWNLOAD_DONE' AND i.searchText LIKE :pattern ESCAPE '\\' " +
            "ORDER BY i.title",
    )
    abstract suspend fun searchDone(serverId: String, userId: String, pattern: String): List<DownloadItemEntity>

    /** Records where the profile got to in an item they downloaded; [at] is when. */
    @Query(
        "UPDATE download_items SET positionTicks = :positionTicks, played = :played, userDataAt = :at " +
            "WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId",
    )
    abstract suspend fun setUserData(serverId: String, userId: String, itemId: String, positionTicks: Long, played: Boolean, at: Long)

    /** Removes a download and its snapshot together. A show's or a season's snapshot is the repository's to remove once nothing sits under it. */
    @Transaction
    open suspend fun remove(serverId: String, userId: String, itemId: String) {
        delete(serverId, userId, itemId)
        deleteItem(serverId, userId, itemId)
    }
}
