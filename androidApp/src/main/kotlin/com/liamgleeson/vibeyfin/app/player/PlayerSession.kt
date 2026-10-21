package com.liamgleeson.vibeyfin.app.player

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.liamgleeson.vibeyfin.ui.common.Strings

private var nextSessionId = 0

private const val CHANNEL_ID = "playback"
private const val NOTIFICATION_ID = 1

/**
 * The media session of the player screen, and the media notification that goes with it, both made
 * and released with the screen. The session drives the picture-in-picture window buttons and media
 * keys. The notification (a MediaStyle one tied to the session, so the system shows it as media
 * controls in the shade and on the lock screen) has play and pause, and is posted from this process
 * by Media3's [PlayerNotificationManager] while the player has something loaded. There is no
 * service: when the screen goes, so do the session and the notification.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerSession(player: Player) {
    val context = LocalContext.current
    DisposableEffect(player) {
        val session = MediaSession.Builder(context, player).setId("vibeyfin-player-${nextSessionId++}").build()
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(Strings.playbackNotificationChannel)
                .build(),
        )
        val notification = PlayerNotificationManager.Builder(context, NOTIFICATION_ID, CHANNEL_ID)
            .setMediaDescriptionAdapter(Description(context))
            .build()
            .apply {
                setMediaSessionToken(session.platformToken)
                // Play and pause only: the player has no queue to step through from the shade.
                setUseNextAction(false)
                setUsePreviousAction(false)
                setUseFastForwardAction(false)
                setUseRewindAction(false)
                setPlayer(player)
            }
        onDispose {
            notification.setPlayer(null)
            session.release()
        }
    }
}

/** The notification's text is the title and subtitle the player was given; a touch brings the app back. */
@OptIn(UnstableApi::class)
private class Description(private val context: Context) : PlayerNotificationManager.MediaDescriptionAdapter {
    override fun getCurrentContentTitle(player: Player): CharSequence = player.mediaMetadata.title ?: ""

    override fun getCurrentContentText(player: Player): CharSequence? = player.mediaMetadata.subtitle

    override fun createCurrentContentIntent(player: Player): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? = null
}
