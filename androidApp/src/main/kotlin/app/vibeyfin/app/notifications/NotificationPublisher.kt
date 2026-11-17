package app.vibeyfin.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.vibeyfin.R
import app.vibeyfin.domain.ProfileNewTitles
import app.vibeyfin.ui.common.Strings

/** Posts what a poll found. */
fun interface NewTitlesPublisher {
    fun post(found: List<ProfileNewTitles>, nameProfiles: Boolean)
}

/** The channel of new-title notifications. */
internal const val NEW_TITLES_CHANNEL_ID = "new_titles"

/**
 * Whether the app may post new-title notifications: the permission on Android 13 and up, the app's notifications on,
 * and their channel not turned off in system settings.
 */
fun canPostNotifications(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return mayPost(granted, manager.areNotificationsEnabled(), manager.getNotificationChannelCompat(NEW_TITLES_CHANNEL_ID)?.importance)
}

/** [channelImportance] is null before the first post creates the channel: that does not stop posting. */
internal fun mayPost(permissionGranted: Boolean, appEnabled: Boolean, channelImportance: Int?): Boolean = permissionGranted && appEnabled && channelImportance != NotificationManagerCompat.IMPORTANCE_NONE

/** Posts new-title notifications on their own channel; tapping one opens the app on the item, see [NewTitleTap]. */
class NotificationPublisher(private val context: Context) : NewTitlesPublisher {
    // Checked by the worker just before posting, through [canPostNotifications]
    @SuppressLint("MissingPermission")
    override fun post(found: List<ProfileNewTitles>, nameProfiles: Boolean) {
        val manager = NotificationManagerCompat.from(context)

        ensureChannel(manager)
        planNotifications(found, nameProfiles).forEach { planned ->
            val notification = NotificationCompat.Builder(context, NEW_TITLES_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(planned.title)
                .setContentText(planned.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(planned.body))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(openIntent(planned))
                .apply {
                    planned.group?.let { setGroup(it) }

                    // Only the episodes alert; the summary that comes with them stays quiet
                    if (planned.summary) setGroupSummary(true).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                }
                .build()
            manager.notify(planned.tag, NOTIFICATION_ID, notification)
        }
    }

    private fun openIntent(planned: PlannedNotification): PendingIntent {
        val intent = NewTitleTap(planned.serverId, planned.userId, planned.itemId).intent(context)

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    // Creating an existing channel only updates it, so this runs before every post
    private fun ensureChannel(manager: NotificationManagerCompat) {
        NotificationChannels.register(manager)
    }

    private companion object {
        const val NOTIFICATION_ID = 7001
    }
}

/** Creating a channel again with the same id only updates its name and description, which is how a language change reaches system settings. */
object NotificationChannels {
    internal fun register(manager: NotificationManagerCompat) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(NEW_TITLES_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(Strings.addedTitlesChannel)
                .setDescription(Strings.addedTitlesChannelDescription)
                .build(),
        )
    }

    fun rename(context: Context) {
        val manager: NotificationManagerCompat = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannelCompat(NEW_TITLES_CHANNEL_ID) != null) register(manager)
    }
}
