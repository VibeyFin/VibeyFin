package com.liamgleeson.vibeyfin

import androidx.compose.runtime.Composable
import com.liamgleeson.vibeyfin.ui.navigation.VibeyFinNavHost
import com.liamgleeson.vibeyfin.ui.theme.VibeyFinTheme

@Composable
fun App() {
    VibeyFinTheme {
        VibeyFinNavHost()
    }
}
