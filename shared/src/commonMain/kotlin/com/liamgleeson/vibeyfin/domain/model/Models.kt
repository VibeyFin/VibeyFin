package com.liamgleeson.vibeyfin.domain.model

import kotlin.time.Instant

/** A saved Jellyfin server. [addresses] are ordered best first: the one that last worked leads. */
data class Server(
    val id: String,
    val name: String,
    val version: String?,
    val addresses: List<String>,
)

/** A profile on a server. A profile without a session is locked and needs a password again. */
data class Account(
    val serverId: String,
    val userId: String,
    val name: String,
    val imageTag: String?,
    val hasSession: Boolean,
    val lastUsedAt: Instant,
)

/** Points at one profile on one server. */
data class ProfileRef(
    val serverId: String,
    val userId: String,
)
