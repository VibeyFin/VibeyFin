package app.vibeyfin.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.vibeyfin.testing.count
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every released database version is rebuilt from its exported schema (`shared/schemas`), given a profile's data, and opened by the
 * current code, which migrates it. A profile that was signed in must still be signed in, with its addresses and settings, and a
 * setting added since keeps its default. Room's own test helper is not in the stack, so the old database is built by hand.
 */
class MigrationTest {
    private val files = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = files.forEach { it.delete() }

    private fun schema(version: Int): JsonObject =
        Json.parseToJsonElement(File("schemas/app.vibeyfin.data.db.VibeyFinDatabase/$version.json").readText()).jsonObject["database"]!!.jsonObject

    /** A database file as version [version] left it, holding one server, two addresses and one signed-in profile with default settings. */
    private fun databaseAt(version: Int): File {
        val file = File.createTempFile("vibeyfin-v$version-", ".db").also { files += it; it.delete() }
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            val schema = schema(version)
            for (entity in schema["entities"]!!.jsonArray) {
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                connection.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("INSERT INTO servers (id, name, version, addedAt) VALUES ('srv-1', 'Home', '10.10.7', 100)")
            connection.execSQL("INSERT INTO server_addresses (serverId, url, addedAt, lastSuccessAt) VALUES ('srv-1', 'http://nas:8096', 100, 500)")
            connection.execSQL("INSERT INTO server_addresses (serverId, url, addedAt, lastSuccessAt) VALUES ('srv-1', 'https://jelly.example.com', 110, NULL)")
            connection.execSQL("INSERT INTO accounts (serverId, userId, name, imageTag, sealedToken, lastUsedAt) VALUES ('srv-1', 'u1', 'Alex', 'tag', 'sealed', 900)")
            connection.execSQL("INSERT INTO account_settings (serverId, userId) VALUES ('srv-1', 'u1')")
            if (version >= 3) {
                connection.execSQL(
                    "INSERT INTO pending_reports (serverId, userId, kind, itemId, mediaSourceId, playSessionId, positionTicks, failed, updatedAt) " +
                        "VALUES ('srv-1', 'u1', 'stopped', 'm1', 'ms1', 'ps1', 1200, 0, 700)",
                )
            }
            connection.execSQL("PRAGMA user_version = $version")
        } finally {
            connection.close()
        }
        return file
    }

    private fun assertMigratesFrom(version: Int) = runBlocking {
        val file = databaseAt(version)
        val database = Room.databaseBuilder<VibeyFinDatabase>(name = file.absolutePath).buildVibeyFinDatabase()
        try {
            assertEquals(1, database.count("servers", "id = 'srv-1' AND name = 'Home' AND addedAt = 100"), "server, from v$version")
            assertEquals(2, database.count("server_addresses", "serverId = 'srv-1'"), "addresses, from v$version")
            assertEquals(1, database.count("server_addresses", "url = 'http://nas:8096' AND lastSuccessAt = 500"), "last address that worked, from v$version")
            assertEquals(
                1,
                database.count("accounts", "serverId = 'srv-1' AND userId = 'u1' AND name = 'Alex' AND sealedToken = 'sealed' AND lastUsedAt = 900"),
                "the signed-in profile and its token, from v$version",
            )
            // Settings a profile never changed take the default of every column added since.
            assertEquals(
                1,
                database.count("account_settings", "userId = 'u1' AND showPlaybackStats = 0 AND bingeThreshold = 3 AND homeGenreRows = 0 AND showHomeHero = 1 AND skipBackSeconds = 10 AND skipForwardSeconds = 10 AND autoSkipSegments = 0 AND bufferAheadSeconds = 20 AND maxStreamingBitrate = 0 AND captionsFollowSystem = 1 AND subtitleSize = 1 AND subtitleTextColor = 0 AND subtitleBackground = 1 AND kidsProfile = 0 AND pinHash IS NULL AND pinSalt IS NULL AND pinOpenProfile = 1 AND pinOpenSettings = 1 AND pinSwitchProfile = 1 AND notificationsEnabled = 0 AND notificationScope = 0 AND pictureInPicture = 1 " +
                    "AND downloadOverMobile = 0 AND autoCleanWatched = 0 AND storageLimitMb = 0 AND homeHeroItems = 1 AND swipeGestures = 1"),
                "settings defaults, from v$version",
            )
            if (version >= 3) {
                assertEquals(1, database.count("pending_reports", "itemId = 'm1' AND positionTicks = 1200"), "pending report, from v$version")
                // The offline columns take their defaults on a report that existed before them.
                assertEquals(1, database.count("pending_reports", "itemId = 'm1' AND played = 0 AND watchedAt = 0"), "pending report defaults, from v$version")
            }
            assertEquals(0, database.count("downloads"), "no downloads yet, from v$version")
        } finally {
            database.close()
        }
    }

    @Test fun version1MigratesToTheCurrentSchema() = assertMigratesFrom(1)

    @Test fun version2MigratesToTheCurrentSchema() = assertMigratesFrom(2)

    @Test fun version3MigratesToTheCurrentSchema() = assertMigratesFrom(3)

    @Test fun version4MigratesToTheCurrentSchema() = assertMigratesFrom(4)

    @Test fun version5MigratesToTheCurrentSchema() = assertMigratesFrom(5)

    @Test fun version6MigratesToTheCurrentSchema() = assertMigratesFrom(6)

    @Test fun version7MigratesToTheCurrentSchema() = assertMigratesFrom(7)

    @Test fun version8MigratesToTheCurrentSchema() = assertMigratesFrom(8)

    @Test fun version9MigratesToTheCurrentSchema() = assertMigratesFrom(9)

    @Test fun version10MigratesToTheCurrentSchema() = assertMigratesFrom(10)

    @Test fun version11MigratesToTheCurrentSchema() = assertMigratesFrom(11)

    @Test fun version12MigratesToTheCurrentSchema() = assertMigratesFrom(12)

    @Test fun version13MigratesToTheCurrentSchema() = assertMigratesFrom(13)

    @Test fun version14MigratesToTheCurrentSchema() = assertMigratesFrom(14)

    @Test fun version15MigratesToTheCurrentSchema() = assertMigratesFrom(15)

    @Test fun theCurrentVersionOpensAsItIs() = assertMigratesFrom(16)
}
