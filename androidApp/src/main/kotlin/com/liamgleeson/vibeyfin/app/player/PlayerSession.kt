package com.liamgleeson.vibeyfin.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/**
 * A media session on the player, for as long as the screen holds it. The system draws its media
 * controls from it (the notification shade and lock screen, the buttons in a picture-in-picture
 * window) and routes hardware media keys and headset buttons to the player. It is released with the
 * screen, before the player is.
 */
@Composable
fun rememberPlayerSession(player: Player) {
    val context = LocalContext.current
    DisposableEffect(player) {
        val session = MediaSession.Builder(context, player).setId("vibeyfin-player").build()
        onDispose { session.release() }
    }
}
