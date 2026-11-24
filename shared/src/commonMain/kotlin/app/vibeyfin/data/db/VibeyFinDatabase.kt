package app.vibeyfin.data.db

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.DeleteColumn
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

@Database(
    entities = [
        ServerEntity::class,
        ServerAddressEntity::class,
        AccountEntity::class,
        AccountSettingsEntity::class,
        PendingReportEntity::class,
        NotifiedItemEntity::class,
        DownloadEntity::class,
        DownloadItemEntity::class,
        DismissedNextUpEntity::class,
    ],
    version = 22,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = DropAllowTranscoding::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
    ],
)
@ConstructedBy(VibeyFinDatabaseConstructor::class)
abstract class VibeyFinDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao

    abstract fun accountDao(): AccountDao

    abstract fun pendingReportDao(): PendingReportDao

    abstract fun downloadDao(): DownloadDao
}

/**
 * The move to version 4: it adds the stats and binge columns and drops `allowTranscoding`, a flag
 * version 3 still has.
 */
@DeleteColumn(tableName = "account_settings", columnName = "allowTranscoding")
class DropAllowTranscoding : AutoMigrationSpec

// Room generates the actual implementations for each target.
@Suppress("KotlinNoActualForExpect")
expect object VibeyFinDatabaseConstructor : RoomDatabaseConstructor<VibeyFinDatabase> {
    override fun initialize(): VibeyFinDatabase
}

/** Applies the settings every platform shares: the bundled SQLite driver and an IO context ([queryContext]: tests run queries inline). */
fun RoomDatabase.Builder<VibeyFinDatabase>.buildVibeyFinDatabase(queryContext: CoroutineContext = Dispatchers.IO): VibeyFinDatabase = setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(queryContext)
    .build()
