package com.liamgleeson.vibeyfin.app

import androidx.compose.runtime.Composable
import com.liamgleeson.vibeyfin.app.navigation.VibeyFinNavHost
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTheme

@Composable
fun VibeyFinApp() {
    VibeyFinTheme {
        VibeyFinNavHost()
    }
}
