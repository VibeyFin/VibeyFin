package app.vibeyfin.app

import androidx.compose.runtime.Composable
import app.vibeyfin.app.navigation.VibeyFinNavHost
import app.vibeyfin.app.theme.VibeyFinTheme
import app.vibeyfin.ui.notifications.NotificationOpenViewModel

@Composable
fun VibeyFinApp(notificationOpen: NotificationOpenViewModel) {
    VibeyFinTheme {
        VibeyFinNavHost(notificationOpen)
    }
}
