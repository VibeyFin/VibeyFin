package com.liamgleeson.vibeyfin.domain

import kotlinx.coroutines.flow.first

/** Where the app opens. */
sealed interface StartDestination {
    /** Nothing saved yet, or the saved server is gone. */
    data object Servers : StartDestination

    data class WhoIsWatching(val serverId: String) : StartDestination

    /** The last profile still has a session and is already the current connection. */
    data object Home : StartDestination
}

/**
 * Decides the first screen:
 * - no saved servers: Servers;
 * - the last profile has a session and "always ask" is off: straight to Home;
 * - otherwise: "Who's watching?" for the last used server.
 */
class StartupRouter(
    private val servers: ServerRepository,
    private val accounts: AccountRepository,
    private val preferences: DevicePreferences,
    private val connection: ConnectionManager,
) {
    suspend fun decide(): StartDestination {
        val saved = servers.servers.first()
        if (saved.isEmpty()) return StartDestination.Servers

        val lastProfile = preferences.lastProfile.first()
        val serverId = (preferences.lastServerId.first()?.takeIf { id -> saved.any { it.id == id } })
            ?: saved.first().id
        val whoIsWatching = StartDestination.WhoIsWatching(serverId)

        if (preferences.alwaysAskWhoIsWatching.first() || lastProfile == null) return whoIsWatching
        if (saved.none { it.id == lastProfile.serverId }) return whoIsWatching

        val account = accounts.account(lastProfile.serverId, lastProfile.userId)
        if (account == null || !account.hasSession) return StartDestination.WhoIsWatching(lastProfile.serverId)

        return when (connection.resume(lastProfile.serverId, lastProfile.userId)) {
            ActivateResult.Activated -> StartDestination.Home
            else -> StartDestination.WhoIsWatching(lastProfile.serverId)
        }
    }
}
