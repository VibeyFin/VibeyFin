package app.vibeyfin.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

// Times are epoch milliseconds. Deleting a server cascades to its addresses, accounts and settings.

/** A Jellyfin server, keyed by the server's own ID (from `/System/Info/Public`), not by URL. */
@Entity(tableName = "servers")
class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String?,
    val addedAt: Long,
)

/** One way to reach a server. [lastSuccessAt] is null until the address has worked once. */
@Entity(
    tableName = "server_addresses",
    primaryKeys = ["serverId", "url"],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class ServerAddressEntity(
    val serverId: String,
    val url: String,
    val addedAt: Long,
    val lastSuccessAt: Long?,
)

/**
 * A profile on a server. [sealedToken] is the [app.vibeyfin.data.vault.TokenVault]
 * blob, never a plain token; null means the profile is locked (expired or signed out).
 */
@Entity(
    tableName = "accounts",
    primaryKeys = ["serverId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class AccountEntity(
    val serverId: String,
    val userId: String,
    val name: String,
    val imageTag: String?,
    val sealedToken: ByteArray?,
    val lastUsedAt: Long,
)

/** Per-profile settings. Each option becomes one column here as it is added. */
@Entity(
    tableName = "account_settings",
    primaryKeys = ["serverId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["serverId", "userId"],
            childColumns = ["serverId", "userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class AccountSettingsEntity(
    val serverId: String,
    val userId: String,
    /** Whether the player shows its stats overlay for this profile. */
    @ColumnInfo(defaultValue = "0")
    val showPlaybackStats: Boolean = false,
    /** Episodes in a row before the player asks "Still watching?". 0 is off. */
    @ColumnInfo(defaultValue = "3")
    val bingeThreshold: Int = 3,
)

/** [PendingReportEntity.kind] of a play session whose `Stopped` the server may not have heard yet. */
const val PENDING_STOPPED = "stopped"

/**
 * A report the server hasn't confirmed, kept so it survives a crash or a killed process. Today the only
 * [kind] is [PENDING_STOPPED]; offline progress and played marks (roadmap Phase 10) use the same table.
 * It belongs to the profile that made it, because that profile's token is the one that can send it.
 * A play session is one row: it is added when playback starts, its position follows playback, and it
 * is deleted once the server has answered.
 */
@Entity(
    tableName = "pending_reports",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["serverId", "userId"],
            childColumns = ["serverId", "userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["serverId", "userId", "kind", "playSessionId"], unique = true)],
)
class PendingReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    val userId: String,
    val kind: String,
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val positionTicks: Long,
    val failed: Boolean,
    val updatedAt: Long,
)
