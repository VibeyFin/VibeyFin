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
    /**
     * How many genre rows Home shows: 0 is every genre, -1 none, otherwise the biggest this many. A new profile starts at
     * [NEW_PROFILE_HOME_GENRE_ROWS]; the column's own default stays 0, so a profile that existed before keeps every genre.
     */
    @ColumnInfo(defaultValue = "0")
    val homeGenreRows: Int = NEW_PROFILE_HOME_GENRE_ROWS,
    /** Whether Home features one item above Continue watching. */
    @ColumnInfo(defaultValue = "1")
    val showHomeHero: Boolean = true,
    /** Seconds the player's skip-back button jumps. */
    @ColumnInfo(defaultValue = "10")
    val skipBackSeconds: Int = 10,
    /** Seconds the player's skip-forward button jumps. */
    @ColumnInfo(defaultValue = "10")
    val skipForwardSeconds: Int = 10,
    /** Whether the player skips intros and recaps without asking. */
    @ColumnInfo(defaultValue = "0")
    val autoSkipSegments: Boolean = false,
    /** Seconds of video the player loads ahead; 0 leaves it to the platform player (Auto). */
    @ColumnInfo(defaultValue = "20")
    val bufferAheadSeconds: Int = 20,
    /** Highest streaming bitrate in bits per second; 0 is Maximum. */
    @ColumnInfo(defaultValue = "0")
    val maxStreamingBitrate: Int = 0,
    /** Whether subtitles follow the system caption style (Android) or the device's own (iOS) instead of the options below. */
    @ColumnInfo(defaultValue = "1")
    val captionsFollowSystem: Boolean = true,
    /** Subtitle size: 0 small, 1 medium, 2 large, 3 extra large. */
    @ColumnInfo(defaultValue = "1")
    val subtitleSize: Int = 1,
    /** Subtitle text colour: 0 white, 1 yellow, 2 light blue. */
    @ColumnInfo(defaultValue = "0")
    val subtitleTextColor: Int = 0,
    /** Subtitle background: 0 none, 1 shadow, 2 box. */
    @ColumnInfo(defaultValue = "1")
    val subtitleBackground: Int = 1,
    /** Whether this is a Kids profile. */
    @ColumnInfo(defaultValue = "0")
    val kidsProfile: Boolean = false,
    /** SHA-256 of [pinSalt] followed by the PIN; null when the profile has no PIN. The PIN itself is never stored. */
    val pinHash: ByteArray? = null,
    val pinSalt: ByteArray? = null,
    /** Whether opening this profile asks for the PIN. Only matters once a PIN exists. */
    @ColumnInfo(defaultValue = "1")
    val pinOpenProfile: Boolean = true,
    /** Whether opening Settings asks for the PIN. Only matters once a PIN exists. */
    @ColumnInfo(defaultValue = "1")
    val pinOpenSettings: Boolean = true,
    /** Whether switching away from this profile asks for the PIN. Only matters once a PIN exists. */
    @ColumnInfo(defaultValue = "1")
    val pinSwitchProfile: Boolean = true,
    /** Whether the app posts notifications for this profile. */
    @ColumnInfo(defaultValue = "0")
    val notificationsEnabled: Boolean = false,
    /** What notifies: 0 shows the profile follows and new movies, 1 everything new. */
    @ColumnInfo(defaultValue = "0")
    val notificationScope: Int = 0,
    /** Whether the player offers picture-in-picture and starts it when the app is left. Off pauses on leaving. */
    @ColumnInfo(defaultValue = "1")
    val pictureInPicture: Boolean = true,
)

/** A profile's stored PIN: both null when it has none. */
class PinCredentials(val pinHash: ByteArray?, val pinSalt: ByteArray?)

/** Whether a profile has a PIN, and its three switches as stored (they only count once there is a PIN). */
data class PinGateRow(val hasPin: Boolean, val pinOpenProfile: Boolean, val pinOpenSettings: Boolean, val pinSwitchProfile: Boolean)

/** A profile to poll for new titles: notifications on, not a kids profile, and a stored token. */
data class NotifiableProfile(val serverId: String, val userId: String, val name: String)

/** Home's genre rows for a profile that has never chosen: the top five, so Home stays a sensible length on a big library. */
const val NEW_PROFILE_HOME_GENRE_ROWS = 5

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

/** An item the profile was already notified about, so it is never notified twice. Old rows are pruned by [notifiedAt]. */
@Entity(
    tableName = "notified_items",
    primaryKeys = ["serverId", "userId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["serverId", "userId"],
            childColumns = ["serverId", "userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class NotifiedItemEntity(
    val serverId: String,
    val userId: String,
    val itemId: String,
    val notifiedAt: Long,
)
