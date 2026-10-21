package app.vibeyfin.app

import androidx.compose.runtime.Composable
import app.vibeyfin.app.navigation.VibeyFinNavHost
import app.vibeyfin.app.theme.VibeyFinTheme

@Composable
fun VibeyFinApp() {
    VibeyFinTheme {
        VibeyFinNavHost()
    }
}
