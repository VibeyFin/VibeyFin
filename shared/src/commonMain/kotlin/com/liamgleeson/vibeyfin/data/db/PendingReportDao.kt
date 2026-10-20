package com.liamgleeson.vibeyfin.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
abstract class PendingReportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(report: PendingReportEntity): Long

    @Query(
        "UPDATE pending_reports SET positionTicks = :positionTicks, failed = :failed, updatedAt = :now " +
            "WHERE serverId = :serverId AND userId = :userId AND kind = :kind AND playSessionId = :playSessionId",
    )
    abstract suspend fun touch(
        serverId: String,
        userId: String,
        kind: String,
        playSessionId: String,
        positionTicks: Long,
        failed: Boolean,
        now: Long,
    )

    /** Adds the report if its play session isn't there yet, and brings its position and time up to date. */
    @Transaction
    open suspend fun save(report: PendingReportEntity) {
        insert(report)
        touch(report.serverId, report.userId, report.kind, report.playSessionId, report.positionTicks, report.failed, report.updatedAt)
    }

    @Query("SELECT * FROM pending_reports WHERE kind = :kind ORDER BY updatedAt, id")
    abstract suspend fun all(kind: String): List<PendingReportEntity>

    @Query(
        "DELETE FROM pending_reports " +
            "WHERE serverId = :serverId AND userId = :userId AND kind = :kind AND playSessionId = :playSessionId",
    )
    abstract suspend fun delete(serverId: String, userId: String, kind: String, playSessionId: String)

    @Query("DELETE FROM pending_reports WHERE kind = :kind AND updatedAt < :cutoff")
    abstract suspend fun deleteOlderThan(kind: String, cutoff: Long)
}
