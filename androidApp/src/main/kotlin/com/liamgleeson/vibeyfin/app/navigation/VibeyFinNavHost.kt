package com.liamgleeson.vibeyfin.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.liamgleeson.vibeyfin.app.addserver.AddServerRoute
import com.liamgleeson.vibeyfin.app.home.HomeRoute
import com.liamgleeson.vibeyfin.app.servers.ServersRoute
import com.liamgleeson.vibeyfin.app.settings.SettingsRoute
import com.liamgleeson.vibeyfin.app.startup.StartupRoute
import com.liamgleeson.vibeyfin.app.whoswatching.WhoIsWatchingRoute
import com.liamgleeson.vibeyfin.domain.StartDestination
import com.liamgleeson.vibeyfin.ui.home.HomeExit

@Composable
fun VibeyFinNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Startup) {
        composable<Startup> {
            StartupRoute(
                onDecided = { destination ->
                    when (destination) {
                        StartDestination.Servers ->
                            navController.navigate(Servers) { popUpTo(Startup) { inclusive = true } }
                        is StartDestination.WhoIsWatching -> {
                            // Servers sits underneath so back from "Who's watching?" has somewhere to go.
                            navController.navigate(Servers) { popUpTo(Startup) { inclusive = true } }
                            navController.navigate(WhoIsWatching(destination.serverId))
                        }
                        StartDestination.Home ->
                            navController.navigate(Home) { popUpTo(Startup) { inclusive = true } }
                    }
                },
            )
        }
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
                onExit = { exit ->
                    // Servers goes underneath "Who's watching?" so back from it has somewhere to go.
                    navController.navigate(Servers) { popUpTo(Home) { inclusive = true } }
                    if (exit is HomeExit.WhoIsWatching) navController.navigate(WhoIsWatching(exit.serverId))
                },
                onSettings = { navController.navigate(Settings) },
            )
        }
        composable<Settings> {
            SettingsRoute(onBack = { navController.popBackStack() })
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
