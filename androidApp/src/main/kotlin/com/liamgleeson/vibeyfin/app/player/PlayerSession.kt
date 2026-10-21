package com.liamgleeson.vibeyfin.app.player

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.ForwardingPlayer
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
 * controls in the shade) has play and pause, and is posted from this process by Media3's
 * [PlayerNotificationManager] while the player has something loaded.
 *
 * Both exist for the viewer only while the screen is in front: resumed, or a picture-in-picture
 * window. Leaving the app any other way (Home on a paused video, the lock button) pauses the player
 * and tells the server it stopped, so the notification is taken down then and put back when the
 * screen resumes, and a play from a media key or other session command is ignored meanwhile. Nothing
 * can start the video again in the background. There is no service: when the screen goes, so do the
 * session and the notification.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerSession(player: Player) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    DisposableEffect(player, activity) {
        fun inFront(): Boolean = activity == null ||
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            activity.isInPictureInPictureMode
        // What the session and the notification control: the player, except that play does nothing in the background.
        val guarded = object : ForwardingPlayer(player) {
            override fun play() {
                if (inFront()) super.play()
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (!playWhenReady || inFront()) super.setPlayWhenReady(playWhenReady)
            }
        }
        val session = MediaSession.Builder(context, guarded).setId("vibeyfin-player-${nextSessionId++}").build()
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
            }
        fun follow() = notification.setPlayer(if (inFront()) guarded else null)
        follow()
        val lifecycleChanged = LifecycleEventObserver { _, _ -> follow() }
        val modeChanged = Consumer<PictureInPictureModeChangedInfo> { follow() }
        activity?.lifecycle?.addObserver(lifecycleChanged)
        activity?.addOnPictureInPictureModeChangedListener(modeChanged)
        onDispose {
            activity?.lifecycle?.removeObserver(lifecycleChanged)
            activity?.removeOnPictureInPictureModeChangedListener(modeChanged)
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
