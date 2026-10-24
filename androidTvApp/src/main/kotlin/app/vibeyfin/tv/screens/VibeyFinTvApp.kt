package app.vibeyfin.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.domain.StartDestination
import app.vibeyfin.tv.theme.VibeyFinTvTheme
import app.vibeyfin.ui.home.HomeExit
import kotlinx.serialization.Serializable

@Serializable data object Startup
@Serializable data object Servers
@Serializable data class AddServer(val address: String? = null)
@Serializable data class WhoIsWatching(val serverId: String)
@Serializable data object Home
@Serializable data class Player(val itemId: String)

@Composable
fun VibeyFinTvApp() {
    VibeyFinTvTheme {
        val nav = rememberNavController()
        val leave: (HomeExit) -> Unit = { exit ->
            nav.navigate(Servers) { popUpTo(nav.graph.id) }
            if (exit is HomeExit.WhoIsWatching) nav.navigate(WhoIsWatching(exit.serverId))
        }
        Box(Modifier.fillMaxSize().background(VibeyFinTokens.Colors.bg)) {
            NavHost(nav, startDestination = Startup) {
                composable<Startup> {
                    StartupRoute(onDecided = { destination ->
                        when (destination) {
                            StartDestination.Servers -> nav.navigate(Servers) { popUpTo(Startup) { inclusive = true } }
                            is StartDestination.WhoIsWatching -> {
                                nav.navigate(Servers) { popUpTo(Startup) { inclusive = true } }
                                nav.navigate(WhoIsWatching(destination.serverId))
                            }
                            StartDestination.Home -> nav.navigate(Home) { popUpTo(Startup) { inclusive = true } }
                        }
                    })
                }
                composable<Servers> {
                    ServersRoute(
                        onOpenServer = { nav.navigate(WhoIsWatching(it)) },
                        onAddServer = { address -> nav.navigate(AddServer(address)) },
                    )
                }
                composable<AddServer> { entry ->
                    AddServerRoute(entry.toRoute<AddServer>().address, onDone = { nav.popBackStack() })
                }
                composable<WhoIsWatching> { entry ->
                    WhoIsWatchingRoute(
                        serverId = entry.toRoute<WhoIsWatching>().serverId,
                        onSignedIn = { nav.navigate(Home) { popUpTo(Servers) { inclusive = true } } },
                    )
                }
                composable<Home> {
                    HomeRoute(onExit = leave, onPlay = { nav.navigate(Player(it)) })
                }
                composable<Player> { entry ->
                    PlayerRoute(entry.toRoute<Player>().itemId, onBack = { nav.popBackStack() }, onExit = leave)
                }
            }
        }
    }
}
