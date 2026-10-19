package com.liamgleeson.vibeyfin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.liamgleeson.vibeyfin.ui.placeholder.PlaceholderRoute

@Composable
fun VibeyFinNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Placeholder) {
        composable<Placeholder> { PlaceholderRoute() }
    }
}
