package app.vibeyfin.app.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.vibeyfin.MainActivity
import app.vibeyfin.data.db.AccountDao
import kotlinx.coroutines.flow.first
import java.net.URLEncoder

/** What a tapped new-title notification opens, carried as extras of the intent that starts [MainActivity]. */
data class NewTitleTap(val serverId: String, val userId: String, val itemId: String) {
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        // Brings the running app forward (onNewIntent) instead of starting a second copy of it
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // A data URI of its own makes each notification's PendingIntent distinct, so one never takes another's extras
        .setData(Uri.parse(uri(serverId, userId, itemId)))
        .putExtra(EXTRA_SERVER, serverId)
        .putExtra(EXTRA_USER, userId)
        .putExtra(EXTRA_ITEM, itemId)

    companion object {
        private const val EXTRA_SERVER = "app.vibeyfin.extra.SERVER_ID"
        private const val EXTRA_USER = "app.vibeyfin.extra.USER_ID"
        private const val EXTRA_ITEM = "app.vibeyfin.extra.ITEM_ID"

        /** The identity of a notification's tap intent: one per profile and title. */
        fun uri(serverId: String, userId: String, itemId: String) = "vibeyfin://new-title/${encode(serverId)}/${encode(userId)}/${encode(itemId)}"

        private fun encode(part: String) = URLEncoder.encode(part, "UTF-8")

        /** Recents relaunches a task with its first intent again; that is not a tap. */
        fun isReplay(flags: Int) = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

        /** The tap an intent carries, or null. The values are untrusted: [NewTitleTapGate] checks them before use. */
        fun from(intent: Intent?): NewTitleTap? {
            intent ?: return null

            if (isReplay(intent.flags)) return null

            val serverId = intent.getStringExtra(EXTRA_SERVER) ?: return null
            val userId = intent.getStringExtra(EXTRA_USER) ?: return null
            val itemId = intent.getStringExtra(EXTRA_ITEM) ?: return null

            return NewTitleTap(serverId, userId, itemId)
        }

        /** Takes the tap's extras off [intent], so it is acted on once. */
        fun consume(intent: Intent) {
            intent.removeExtra(EXTRA_SERVER)
            intent.removeExtra(EXTRA_USER)
            intent.removeExtra(EXTRA_ITEM)
        }
    }
}

/**
 * Checks a tap before the app acts on it. The launcher activity is exported, so any app can send the extras: a tap is
 * taken only when every id has the shape of a Jellyfin id, the profile is saved here with notifications on (and is not
 * a kids profile), and the profile was notified about that item. Anything else is dropped without a word.
 */
class NewTitleTapGate(
    private val notificationsOn: suspend (serverId: String, userId: String) -> Boolean,
    private val wasNotified: suspend (serverId: String, userId: String, itemId: String) -> Boolean,
) {
    suspend fun accept(tap: NewTitleTap): Boolean {
        if (!isJellyfinId(tap.serverId) || !isJellyfinId(tap.userId) || !isJellyfinId(tap.itemId)) return false
        return notificationsOn(tap.serverId, tap.userId) && wasNotified(tap.serverId, tap.userId, tap.itemId)
    }

    companion object {
        // 32 hex digits, or the same as a dashed GUID
        private val JELLYFIN_ID = Regex("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        fun isJellyfinId(value: String) = JELLYFIN_ID.matches(value)

        fun from(dao: AccountDao) = NewTitleTapGate(
            notificationsOn = { serverId, userId ->
                dao.observeNotificationsEnabled(serverId, userId).first() == true && dao.observeKidsProfile(serverId, userId).first() != true
            },
            wasNotified = dao::wasNotified,
        )
    }
}
