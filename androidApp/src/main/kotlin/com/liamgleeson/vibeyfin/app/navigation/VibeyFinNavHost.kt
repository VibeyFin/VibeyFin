package com.liamgleeson.vibeyfin.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.liamgleeson.vibeyfin.app.addserver.AddServerRoute
import com.liamgleeson.vibeyfin.app.home.HomeRoute
import com.liamgleeson.vibeyfin.app.servers.ServersRoute
import com.liamgleeson.vibeyfin.app.whoswatching.WhoIsWatchingRoute

@Composable
fun VibeyFinNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Servers) {
        composable<Servers> {
            ServersRoute(
                onOpenServer = { navController.navigate(WhoIsWatching(it.id)) },
                onAddServer = { navController.navigate(AddServer()) },
                onDiscoveredServer = { navController.navigate(AddServer(address = it.address)) },
            )
        }
        composable<WhoIsWatching> { entry ->
            WhoIsWatchingRoute(
                serverId = entry.toRoute<WhoIsWatching>().serverId,
                onBack = { navController.popBackStack() },
                // Home becomes the root: back from it leaves the app instead of returning to sign-in.
                onSignedIn = { navController.navigate(Home) { popUpTo(Servers) { inclusive = true } } },
            )
        }
        composable<Home> {
            HomeRoute(
                onSessionEnded = { serverId ->
                    navController.navigate(WhoIsWatching(serverId)) { popUpTo(Home) { inclusive = true } }
                },
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
