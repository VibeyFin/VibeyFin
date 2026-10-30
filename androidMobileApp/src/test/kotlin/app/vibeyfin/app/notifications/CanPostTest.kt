package app.vibeyfin.app.notifications

import androidx.core.app.NotificationManagerCompat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanPostTest {
    @Test
    fun `posting needs the permission and the app's notifications on`() {
        assertTrue(mayPost(permissionGranted = true, appEnabled = true, channelImportance = NotificationManagerCompat.IMPORTANCE_DEFAULT))
        assertFalse(mayPost(permissionGranted = false, appEnabled = true, channelImportance = NotificationManagerCompat.IMPORTANCE_DEFAULT))
        assertFalse(mayPost(permissionGranted = true, appEnabled = false, channelImportance = NotificationManagerCompat.IMPORTANCE_DEFAULT))
    }

    @Test
    fun `a channel turned off stops posting and a channel not made yet does not`() {
        assertFalse(mayPost(permissionGranted = true, appEnabled = true, channelImportance = NotificationManagerCompat.IMPORTANCE_NONE))
        assertTrue(mayPost(permissionGranted = true, appEnabled = true, channelImportance = null))
    }
}
