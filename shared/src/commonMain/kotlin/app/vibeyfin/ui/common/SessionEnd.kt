package app.vibeyfin.ui.common

import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Non-null once the server has rejected the profile's token: whatever screen is showing must leave
 * for "Who's watching?", where the profile now shows as locked. Any call can end the session, so
 * every screen that talks to the server watches this.
 */
internal fun ConnectionManager.sessionEnded(): Flow<HomeExit?> = state
    .map { (it as? ConnectionState.Expired)?.let { expired -> HomeExit.WhoIsWatching(expired.serverId) } }
    .distinctUntilChanged()
