package com.liamgleeson.vibeyfin.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/** Counts sessions, for an id no other live session has; only touched on the main thread. */
private var nextSessionId = 0

/**
 * A media session on the player, for as long as the screen holds it. It drives the buttons in the
 * picture-in-picture window and routes hardware media keys and headset buttons to the player. It is
 * released with the screen, before the player is.
 *
 * It posts no media notification: Media3 posts one only from a `MediaSessionService`, so there are no
 * controls in the notification shade or on the lock screen. That would need the player to live in such
 * a service, which is left out.
 */
@Composable
fun rememberPlayerSession(player: Player) {
    val context = LocalContext.current
    DisposableEffect(player) {
        // Session ids must be unique in the app, and the next player screen can compose before this one is gone.
        val session = MediaSession.Builder(context, player).setId("vibeyfin-player-${nextSessionId++}").build()
        onDispose { session.release() }
    }
}
