package app.vibeyfin.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import app.vibeyfin.domain.DownloadRunner
import app.vibeyfin.ui.common.Strings

/** The notification that Android requires while downloads run in a foreground service, and the channel it sits in. */
internal class DownloadNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun info(progress: DownloadRunner.Progress?): ForegroundInfo {
        ensureChannel()
        return ForegroundInfo(NOTIFICATION_ID, notification(progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    /** Replaces the text and bar of the notification the foreground service already shows, which is the one under [NOTIFICATION_ID]. */
    fun update(progress: DownloadRunner.Progress?) {
        ensureChannel()
        manager.notify(NOTIFICATION_ID, notification(progress))
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, Strings.downloadsChannelName, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    private fun notification(progress: DownloadRunner.Progress?): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        }
        if (progress == null) return builder.setContentTitle(Strings.downloadsNotificationIdle).build()
        progress.waiting?.let { waiting ->
            val text = when (waiting) {
                DownloadRunner.Waiting.Network -> Strings.downloadWaitingForNetwork
                DownloadRunner.Waiting.Wifi -> Strings.downloadWaitingForWifi
                DownloadRunner.Waiting.Server -> Strings.downloadWaitingForServer
            }
            return builder.setContentTitle(Strings.downloadingNotificationTitle(progress.title)).setContentText(text).build()
        }
        builder.setContentTitle(Strings.downloadingNotificationTitle(progress.title))
        progress.bytesTotal?.takeIf { it > 0 }?.let { total ->
            builder.setProgress(PROGRESS_MAX, (progress.bytesDone * PROGRESS_MAX / total).toInt().coerceIn(0, PROGRESS_MAX), false)
        } ?: builder.setProgress(0, 0, true)
        val text = buildList {
            add(Strings.downloadProgress(progress.bytesDone, progress.bytesTotal))
            // How long this file has left at the speed it is coming in; absent until there is a measure.
            progress.timeLeft?.let { add(Strings.downloadTimeLeft(it.inWholeSeconds)) }
            if (progress.remaining > 1) add(Strings.downloadsWaiting(progress.remaining - 1))
        }.joinToString(" · ")
        return builder.setContentText(text).build()
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 4711
        const val PROGRESS_MAX = 1000
    }
}
