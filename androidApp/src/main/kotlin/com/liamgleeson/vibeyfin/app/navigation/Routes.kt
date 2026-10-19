package com.liamgleeson.vibeyfin.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. One `@Serializable` object or class per screen. */
@Serializable
data object Servers

/** [address] is filled in and checked straight away, e.g. for a server found by the search. */
@Serializable
data class AddServer(val address: String? = null)

@Serializable
data class WhoIsWatching(val serverId: String)

@Serializable
data object Home

@Serializable
data object Startup

@Serializable
data object Settings
