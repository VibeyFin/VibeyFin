package com.liamgleeson.vibeyfin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.liamgleeson.vibeyfin.ui.addserver.AddServerRoute
import com.liamgleeson.vibeyfin.ui.servers.ServersRoute

@Composable
fun VibeyFinNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Servers) {
        composable<Servers> {
            ServersRoute(
                onAddServer = { navController.navigate(AddServer()) },
                onDiscoveredServer = { navController.navigate(AddServer(address = it.address)) },
            )
        }
        composable<AddServer> { entry ->
            AddServerRoute(
                initialAddress = entry.toRoute<AddServer>().address,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
