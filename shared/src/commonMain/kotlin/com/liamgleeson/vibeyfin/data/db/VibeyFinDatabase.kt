package com.liamgleeson.vibeyfin.data.db

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        ServerEntity::class,
        ServerAddressEntity::class,
        AccountEntity::class,
        AccountSettingsEntity::class,
        PendingReportEntity::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
@ConstructedBy(VibeyFinDatabaseConstructor::class)
abstract class VibeyFinDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun accountDao(): AccountDao
    abstract fun pendingReportDao(): PendingReportDao
}

// Room generates the actual implementations for each target.
@Suppress("KotlinNoActualForExpect")
expect object VibeyFinDatabaseConstructor : RoomDatabaseConstructor<VibeyFinDatabase> {
    override fun initialize(): VibeyFinDatabase
}

/** Applies the settings every platform shares: the bundled SQLite driver and an IO context. */
fun RoomDatabase.Builder<VibeyFinDatabase>.buildVibeyFinDatabase(): VibeyFinDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
