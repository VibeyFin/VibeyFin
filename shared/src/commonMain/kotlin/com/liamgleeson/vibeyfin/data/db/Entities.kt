package com.liamgleeson.vibeyfin.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
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
 * A profile on a server. [sealedToken] is the [com.liamgleeson.vibeyfin.data.vault.TokenVault]
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
)
