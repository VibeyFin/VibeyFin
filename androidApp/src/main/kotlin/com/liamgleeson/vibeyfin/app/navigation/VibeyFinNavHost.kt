package com.liamgleeson.vibeyfin.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.liamgleeson.vibeyfin.app.addserver.AddServerRoute
import com.liamgleeson.vibeyfin.app.children.ChildrenRoute
import com.liamgleeson.vibeyfin.app.home.HomeRoute
import com.liamgleeson.vibeyfin.app.item.ItemRoute
import com.liamgleeson.vibeyfin.app.player.PlayerRoute
import com.liamgleeson.vibeyfin.app.servers.ServersRoute
import com.liamgleeson.vibeyfin.app.settings.SettingsRoute
import com.liamgleeson.vibeyfin.app.startup.StartupRoute
import com.liamgleeson.vibeyfin.app.whoswatching.WhoIsWatchingRoute
import com.liamgleeson.vibeyfin.domain.StartDestination
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.home.HomeExit

private const val SLIDE_MS = 280

/** Screens slide a fifth of their width, not all of it: enough to show direction without a long wait. */
private const val SLIDE_FRACTION = 5

private fun enter() = slideInHorizontally(tween(SLIDE_MS)) { it / SLIDE_FRACTION } + fadeIn(tween(SLIDE_MS))

private fun exit() = slideOutHorizontally(tween(SLIDE_MS)) { -it / SLIDE_FRACTION } + fadeOut(tween(SLIDE_MS))

private fun popEnter() = slideInHorizontally(tween(SLIDE_MS)) { -it / SLIDE_FRACTION } + fadeIn(tween(SLIDE_MS))

private fun popExit() = slideOutHorizontally(tween(SLIDE_MS)) { it / SLIDE_FRACTION } + fadeOut(tween(SLIDE_MS))

@Composable
fun VibeyFinNavHost() {
    val navController = rememberNavController()
    // A folder opens its list; anything else opens its page.
    val open: (MediaItem) -> Unit = { item ->
        navController.navigate(if (item.isFolder) Children(item.id, item.title) else Item(item.id))
    }
    // Leaves Home and everything opened from it. Servers goes underneath "Who's watching?" so back
    // from it has somewhere to go.
    val leave: (HomeExit) -> Unit = { exit ->
        navController.navigate(Servers) { popUpTo(Home) { inclusive = true } }
        if (exit is HomeExit.WhoIsWatching) navController.navigate(WhoIsWatching(exit.serverId))
    }
    NavHost(
        navController = navController,
        startDestination = Startup,
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
        // The back gesture scrubs these two. Left alone they are the library's own scale-down, which
        // looks nothing like the slide the back button plays, so they are the pop pair again.
        predictivePopEnterTransition = { popEnter() },
        predictivePopExitTransition = { popExit() },
    ) {
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
                onExit = leave,
                onOpen = open,
                // A Continue watching card plays at once; it never goes through the detail page.
                onPlay = { itemId -> navController.navigate(Player(itemId)) },
                onSettings = { navController.navigate(Settings) },
            )
        }
        composable<Children> { entry ->
            val route = entry.toRoute<Children>()
            ChildrenRoute(
                parentId = route.parentId,
                title = route.title,
                onBack = { navController.popBackStack() },
                onOpen = open,
                onExit = leave,
            )
        }
        composable<Item> { entry ->
            val itemId = entry.toRoute<Item>().itemId
            ItemRoute(
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onPlay = { navController.navigate(Player(itemId)) },
                onExit = leave,
            )
        }
        composable<Player> { entry ->
            PlayerRoute(
                itemId = entry.toRoute<Player>().itemId,
                onBack = { navController.popBackStack() },
                onExit = leave,
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
