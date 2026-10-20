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

/** What is inside a library, folder, series or season. [title] is shown at the top while it loads. */
@Serializable
data class Children(val parentId: String, val title: String)

@Serializable
data class Item(val itemId: String)

/** Plays one movie or episode, full screen. It resumes where the server says the user stopped, unless [startOver]. */
@Serializable
data class Player(val itemId: String, val startOver: Boolean = false)
