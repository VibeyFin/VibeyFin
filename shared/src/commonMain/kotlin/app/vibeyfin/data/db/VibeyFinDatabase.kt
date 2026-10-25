package app.vibeyfin.data.db

import androidx.room3.AutoMigration
import androidx.room3.DeleteColumn
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.IO

@Database(
    entities = [
        ServerEntity::class,
        ServerAddressEntity::class,
        AccountEntity::class,
        AccountSettingsEntity::class,
        PendingReportEntity::class,
    ],
    version = 10,
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
    ],
)
@ConstructedBy(VibeyFinDatabaseConstructor::class)
abstract class VibeyFinDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun accountDao(): AccountDao
    abstract fun pendingReportDao(): PendingReportDao
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
fun RoomDatabase.Builder<VibeyFinDatabase>.buildVibeyFinDatabase(queryContext: CoroutineContext = Dispatchers.IO): VibeyFinDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryContext)
        .build()
