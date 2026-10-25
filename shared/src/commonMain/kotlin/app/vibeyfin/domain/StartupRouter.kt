package app.vibeyfin.domain

import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.flow.first

/** Where the app opens. */
sealed interface StartDestination {
    /** Nothing saved yet, or the saved server is gone. */
    data object Servers : StartDestination

    /**
     * "Who's watching?" on [serverId]. With [pinFor] set, the picker asks for that profile's PIN first: to open it
     * when [openAfterPin] is true, otherwise to let anyone pick another profile (dismissing goes back to [pinFor]).
     */
    data class WhoIsWatching(
        val serverId: String,
        val pinFor: ProfileRef? = null,
        val openAfterPin: Boolean = false,
    ) : StartDestination

    /** The last profile still has a session and is already the current connection. */
    data object Home : StartDestination
}

/**
 * Decides the first screen:
 * - no saved servers: Servers;
 * - the last profile has a session and "always ask" is off: straight to Home, unless it asks for its PIN to open,
 *   when "Who's watching?" asks for that PIN and then opens it;
 * - otherwise: "Who's watching?" for the last used server. If the last profile asks for its PIN to switch profile,
 *   the picker asks for it first, even with "always ask" on: showing the picker is leaving that profile.
 */
class StartupRouter(
    private val servers: ServerRepository,
    private val accounts: AccountRepository,
    private val preferences: DevicePreferences,
    private val connection: ConnectionManager,
    private val pins: PinLock,
) {
    suspend fun decide(): StartDestination {
        val saved = servers.servers.first()
        if (saved.isEmpty()) return StartDestination.Servers

        val serverId = (preferences.lastServerId.first()?.takeIf { id -> saved.any { it.id == id } })
            ?: saved.first().id
        val alwaysAsk = preferences.alwaysAskWhoIsWatching.first()
        val lastProfile = preferences.lastProfile.first()?.takeIf { last -> saved.any { it.id == last.serverId } }
            ?: return StartDestination.WhoIsWatching(serverId)
        val account = accounts.account(lastProfile.serverId, lastProfile.userId)
            ?: return StartDestination.WhoIsWatching(if (alwaysAsk) serverId else lastProfile.serverId)

        val gates = pins.gates(lastProfile.serverId, lastProfile.userId)
        if (alwaysAsk || !account.hasSession) {
            return when {
                gates.switchProfile -> StartDestination.WhoIsWatching(lastProfile.serverId, pinFor = lastProfile)
                alwaysAsk -> StartDestination.WhoIsWatching(serverId)
                else -> StartDestination.WhoIsWatching(lastProfile.serverId)
            }
        }
        // A profile that asks for its PIN to open is never picked for the user: the picker stays up and a tap on its tile asks.
        if (gates.openProfile) return StartDestination.WhoIsWatching(lastProfile.serverId)

        return when (connection.resume(lastProfile.serverId, lastProfile.userId)) {
            ActivateResult.Activated -> StartDestination.Home
            else -> StartDestination.WhoIsWatching(lastProfile.serverId)
        }
    }
}
