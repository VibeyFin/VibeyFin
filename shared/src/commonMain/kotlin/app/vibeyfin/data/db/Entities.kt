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
    /** The Seerr linked to this server (its root URL), shared by every profile on it; null when none is set up. */
    val seerrUrl: String? = null,
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
    /** The profile's Seerr session cookie, sealed like [sealedToken]; null when it isn't signed in to Seerr. */
    val sealedSeerrSession: ByteArray? = null,
    /** The profile's user id on Seerr, while [sealedSeerrSession] is set. */
    val seerrUserId: Int? = null,
    /** The profile disconnected Seerr: it isn't signed in to it again by itself, though the server keeps its address. */
    @ColumnInfo(defaultValue = "0")
    val seerrOff: Boolean = false,
    /** The Seerr session came from the Jellyfin password (Quick Connect was off), so it is asked again when it ends. */
    @ColumnInfo(defaultValue = "0")
    val seerrPasswordSignIn: Boolean = false,
)

/** What [AccountDao.getSeerrProfile] reads: a profile's Seerr session and whether it turned Seerr off. */
class SeerrProfileRow(
    val sealedSeerrSession: ByteArray?,
    val seerrUserId: Int?,
    val seerrOff: Boolean,
    val seerrPasswordSignIn: Boolean,
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
    /** Whether Home features anything above Continue watching; off, [homeHeroItems] is kept for when it is switched on again. */
    @ColumnInfo(defaultValue = "1")
    val showHomeHero: Boolean = true,
    /** How many items the hero features while [showHomeHero] is on: 1 to 5, a carousel from 2. */
    @ColumnInfo(defaultValue = "1")
    val homeHeroItems: Int = 1,
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
    /** Whether a vertical swipe on the player changes the volume (right half) and the brightness (left half). */
    @ColumnInfo(defaultValue = "1")
    val swipeGestures: Boolean = true,
    /** Whether downloads may use mobile data (or any metered network). Off: they wait for Wi-Fi. */
    @ColumnInfo(defaultValue = "0")
    val downloadOverMobile: Boolean = false,
    /** Whether a download is removed once the user has watched it. */
    @ColumnInfo(defaultValue = "0")
    val autoCleanWatched: Boolean = false,
    /** The most the profile's downloads may take, in megabytes; 0 is no limit. */
    @ColumnInfo(defaultValue = "0")
    val storageLimitMb: Long = 0,
    /**
     * The profile's preferred audio and subtitle languages as last read from the Jellyfin account, as JSON, so a download picks
     * its tracks the same way without the server. Null until they have been read once.
     */
    val languagePrefs: String? = null,
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
 * [PendingReportEntity.kind] of what the user did to an item without a connection: where they stopped, and whether they finished it.
 * There is one row per item and profile, the latest, with an empty `playSessionId`: the server never knew these sessions.
 */
const val PENDING_PROGRESS = "progress"

/**
 * A report the server hasn't confirmed, kept so it survives a crash or a killed process. A [kind] is
 * [PENDING_STOPPED] or [PENDING_PROGRESS] (what was watched offline).
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
    indices = [Index(value = ["serverId", "userId", "kind", "itemId", "playSessionId"], unique = true)],
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
    /** [PENDING_PROGRESS] only: the user finished the item. */
    @ColumnInfo(defaultValue = "0")
    val played: Boolean = false,
    /** [PENDING_PROGRESS] only: when the user last watched it, epoch milliseconds. It decides whether the server's own record is newer. */
    @ColumnInfo(defaultValue = "0")
    val watchedAt: Long = 0,
)

/** [DownloadEntity.state]: waiting for its turn, or for a worker to start. */
const val DOWNLOAD_QUEUED = "queued"

/** [DownloadEntity.state]: bytes are coming in. */
const val DOWNLOAD_RUNNING = "running"

/** [DownloadEntity.state]: the user paused it. */
const val DOWNLOAD_PAUSED = "paused"

/** [DownloadEntity.state]: it may go on once the network is one it is allowed to use (Wi-Fi, unless the profile allows mobile data). */
const val DOWNLOAD_WAITING_FOR_NETWORK = "waiting"

/** [DownloadEntity.state]: it stopped with [DownloadEntity.error]; the user can try again. */
const val DOWNLOAD_FAILED = "failed"

/** [DownloadEntity.state]: the file and its artwork are on the device. */
const val DOWNLOAD_DONE = "done"

/**
 * A movie or an episode the profile wants on the device, and how far it is. A series or a season is not a row: it is
 * its episodes. Downloads belong to a profile, so removing the profile removes them (the files are cleaned up by the
 * repository, which knows their paths). [bytesTotal] is -1 until the server has said how big the file is.
 */
@Entity(
    tableName = "downloads",
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
class DownloadEntity(
    val serverId: String,
    val userId: String,
    val itemId: String,
    /** `Movie` or `Episode`, the [app.vibeyfin.domain.model.ItemKind] name. */
    val kind: String,
    val seriesId: String?,
    val seasonId: String?,
    val state: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val mediaSourceId: String?,
    val container: String?,
    /** The file's version on the server when it was started, so a resume can tell it changed. */
    val etag: String?,
    /** The file, relative to the downloads folder; it is the `.part` file until the download is done. */
    val filePath: String?,
    /** Why it failed, as a short code the repository maps to a message; null unless [state] is [DOWNLOAD_FAILED]. */
    val error: String?,
    val addedAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

/**
 * What the screens need to show a downloaded item without the server: the columns the lists sort and search by, and
 * the rest of it (cast, streams, local artwork, subtitle files) in [snapshotJson], whose shape the repository owns so it can
 * change without a migration. A series and a season are kept too ([kind]), as the parents their downloaded episodes sit under.
 * [positionTicks] and [played] are the profile's own record of the item, the offline fallback: the server answers once online.
 */
@Entity(
    tableName = "download_items",
    primaryKeys = ["serverId", "userId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["serverId", "userId"],
            childColumns = ["serverId", "userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["serverId", "userId", "seriesId"])],
)
class DownloadItemEntity(
    val serverId: String,
    val userId: String,
    val itemId: String,
    /** The [app.vibeyfin.domain.model.ItemKind] name: `Movie`, `Series`, `Season` or `Episode`. */
    val kind: String,
    val title: String,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    /** Lower-case title, show name and cast names, so offline search is one LIKE. */
    val searchText: String,
    val positionTicks: Long,
    val played: Boolean,
    /** When the position was last set, by the server (when the snapshot was taken) or by watching offline. */
    val userDataAt: Long,
    val snapshotJson: String,
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
