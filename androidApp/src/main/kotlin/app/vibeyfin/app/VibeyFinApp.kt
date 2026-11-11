package app.vibeyfin.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibeyfin.app.components.ProvideSkeletonShimmer
import app.vibeyfin.app.navigation.VibeyFinNavHost
import app.vibeyfin.app.theme.VibeyFinTheme
import app.vibeyfin.ui.notifications.NotificationOpenViewModel
import app.vibeyfin.ui.theme.ThemeViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun VibeyFinApp(notificationOpen: NotificationOpenViewModel) {
    val themeViewModel: ThemeViewModel = koinViewModel()
    val themeId: String by themeViewModel.themeId.collectAsStateWithLifecycle()

    VibeyFinTheme(themeId = themeId) {
        ProvideSkeletonShimmer {
            LocalNetworkPermissionGate {
                VibeyFinNavHost(notificationOpen)
            }
        }
    }
}
