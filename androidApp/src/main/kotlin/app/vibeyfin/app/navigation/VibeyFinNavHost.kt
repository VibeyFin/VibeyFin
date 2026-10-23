@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.vibeyfin.app.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.navigation
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.vibeyfin.app.addserver.AddServerRoute
import app.vibeyfin.app.children.ChildrenRoute
import app.vibeyfin.app.person.PersonRoute
import app.vibeyfin.app.search.SearchRoute
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.app.filtered.FilteredListRoute
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.app.home.HomeRoute
import app.vibeyfin.app.item.ItemRoute
import app.vibeyfin.app.player.PlayerRoute
import app.vibeyfin.app.servers.ServersRoute
import app.vibeyfin.app.settings.SettingsRoute
import app.vibeyfin.app.startup.StartupRoute
import app.vibeyfin.app.whoswatching.WhoIsWatchingRoute
import app.vibeyfin.domain.StartDestination
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.home.HomeExit

private const val SLIDE_MS = 180

/** The detail page fades in over the shared poster-to-picture transition. */
private const val DETAIL_FADE_MS = 300

/** The screen underneath fades out a little quicker than the page fades in, so it dims first and the page comes up out of the dark. */
private const val DETAIL_UNDER_FADE_MS = 200

/** Switching tabs is a quick cross-fade, with no scale: it is a switch, not opening something. */
private const val TAB_FADE_MS = 110

/** Screens slide a fifth of their width, not all of it: enough to show direction without a long wait. */
private const val SLIDE_FRACTION = 5

/** Opening a screen grows it in from slightly smaller, as if opening the thing that was tapped. */
private const val OPEN_START_SCALE = 0.92f

/** The player is full-screen landscape video, so it only fades in: a scale would fight the rotation. */
private const val PLAYER_FADE_MS = 160

private fun NavBackStackEntry.opensPlayer() = destination.hasRoute<Player>()

private fun NavBackStackEntry.isTabRoot() = destination.hasRoute<Home>() || destination.hasRoute<Search>() || destination.hasRoute<Library>() || destination.hasRoute<Downloads>()

private fun NavBackStackEntry.isDetail() = destination.hasRoute<Item>()

private fun enter(target: NavBackStackEntry) =
    if (target.isDetail()) {
        // The poster grows into the page's picture (a shared element), so the page itself only fades in under it.
        fadeIn(tween(DETAIL_FADE_MS))
    } else if (target.isTabRoot()) {
        fadeIn(tween(TAB_FADE_MS))
    } else if (target.opensPlayer()) {
        fadeIn(tween(PLAYER_FADE_MS))
    } else {
        scaleIn(tween(SLIDE_MS), initialScale = OPEN_START_SCALE) + fadeIn(tween(SLIDE_MS))
    }

/** The screen underneath stays put and fades out behind the one opening over it. */
private fun exit(target: NavBackStackEntry) = fadeOut(tween(if (target.isDetail()) DETAIL_UNDER_FADE_MS else if (target.isTabRoot()) TAB_FADE_MS else if (target.opensPlayer()) PLAYER_FADE_MS else SLIDE_MS))

private fun popEnter(leaving: NavBackStackEntry) =
    if (leaving.isDetail()) fadeIn(tween(DETAIL_FADE_MS)) else slideInHorizontally(tween(SLIDE_MS)) { -it / SLIDE_FRACTION } + fadeIn(tween(SLIDE_MS))

private fun popExit(leaving: NavBackStackEntry) =
    if (leaving.isDetail()) fadeOut(tween(DETAIL_FADE_MS)) else slideOutHorizontally(tween(SLIDE_MS)) { it / SLIDE_FRACTION } + fadeOut(tween(SLIDE_MS))

@Composable
fun VibeyFinNavHost() {
    val navController = rememberNavController()
    // A folder opens its list; anything else opens its page. Both open inside the tab the user is on.
    val open: (MediaItem) -> Unit = { item ->
        navController.navigate(if (item.opensList) Children(item.id, item.title, item.kind == ItemKind.Collection, item.backdropUrl ?: item.posterUrl) else Item(item.id))
    }
    // Leaves the tabs and everything opened from them. Servers goes underneath "Who's watching?" so
    // back from it has somewhere to go. The tabs' saved back stacks belong to this profile: dropped.
    val leave: (HomeExit) -> Unit = { exit ->
        navController.navigate(Servers) { popUpTo(navController.graph.id) }
        Tab.entries.forEach { navController.clearBackStack(it.graph) }
        if (exit is HomeExit.WhoIsWatching) navController.navigate(WhoIsWatching(exit.serverId))
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    // The bar belongs to the three tabs and what opens from them; sign-in, settings and the player are without it.
    val currentTab = backStackEntry?.destination?.hierarchy?.let { path ->
        Tab.entries.firstOrNull { tab -> path.any { it.hasRoute(tab.graph::class) } }
    }
    val select: (Tab) -> Unit = { tab ->
        if (tab == currentTab) {
            // Tapping the tab you are on goes back to its root.
            navController.popBackStack(tab.root, inclusive = false)
        } else {
            navController.navigate(tab.graph) {
                // Everything above the root graph is a tab's stack: keep it for when the tab returns.
                popUpTo(navController.graph.id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    // Back from the root of Library or Downloads goes to Home, as on any app with a bar, not out of the app.
    val atOtherRoot = currentTab != null && currentTab != Tab.Home && backStackEntry?.destination?.hasRoute(currentTab.root::class) == true
    BackHandler(enabled = atOtherRoot) { select(Tab.Home) }
    // The player is the whole screen: no inset of its own for the camera cut-out, so no bar of the page's colour beside the picture
    // (a foldable's cover screen, a phone turned sideways), and a black window behind it.
    val onPlayer = backStackEntry?.opensPlayer() == true
    Scaffold(
        containerColor = if (onPlayer) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.background,
        contentWindowInsets = if (onPlayer) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            if (currentTab != null) MainNavigationBar(currentTab, select)
        },
    ) { padding ->
        SharedTransitionLayout(Modifier.padding(padding).consumeWindowInsets(padding)) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        NavHost(
            navController = navController,
            modifier = Modifier,
            startDestination = Startup,
            enterTransition = { enter(targetState) },
            exitTransition = { exit(targetState) },
            popEnterTransition = { popEnter(initialState) },
            popExitTransition = { popExit(initialState) },
            // The back gesture scrubs these two. Left alone they are the library's own scale-down, which
            // looks nothing like the slide the back button plays, so they are the pop pair again.
            predictivePopEnterTransition = { popEnter(initialState) },
            predictivePopExitTransition = { popExit(initialState) },
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
                                navController.navigate(HomeTab) { popUpTo(Startup) { inclusive = true } }
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
                    onSignedIn = { navController.navigate(HomeTab) { popUpTo(Servers) { inclusive = true } } },
                )
            }
            // One graph per tab, each with the screens that open inside it: the same Item and Children
            // screens, once per graph, so each tab has its own back stack.
            navigation<HomeTab>(startDestination = Home) {
                sharedComposable<Home> {
                    HomeRoute(
                        onExit = leave,
                        onOpen = open,
                        // A Continue watching card plays at once; it never goes through the detail page.
                        onPlay = { itemId -> navController.navigate(Player(itemId)) },
                        onDetails = { itemId -> navController.navigate(Item(itemId)) },
                        onSettings = { navController.navigate(Settings) },
                        onOpenList = { navController.navigate(it.toRoute()) },
                    )
                }
                details(navController, leave)
            }
            navigation<SearchTab>(startDestination = Search) {
                sharedComposable<Search> {
                    SearchRoute(
                        onOpenResult = { result ->
                            navController.navigate(
                                when (result.kind) {
                                    SearchKind.Person -> Person(result.id)
                                    SearchKind.Collection -> Children(result.id, result.title, collection = true, imageUrl = result.backdropUrl ?: result.imageUrl)
                                    else -> Item(result.id)
                                },
                            )
                        },
                        onExit = leave,
                    )
                }
                details(navController, leave)
            }
            navigation<LibraryTab>(startDestination = Library) {
                sharedComposable<Library> {
                    FilteredListRoute(filter = HomeFilter.Library, isRoot = true, onBack = {}, onOpen = open, onExit = leave)
                }
                details(navController, leave)
            }
            navigation<DownloadsTab>(startDestination = Downloads) {
                sharedComposable<Downloads> { NotAvailableYet() }
                details(navController, leave)
            }
            composable<Player> { entry ->
                val route = entry.toRoute<Player>()
                PlayerRoute(
                    itemId = route.itemId,
                    startOver = route.startOver,
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
        }
    }
}

/** What opens from a tab: a folder's list and an item's page, over the tab's own root. */
private fun NavGraphBuilder.details(navController: NavHostController, leave: (HomeExit) -> Unit) {
    val open: (MediaItem) -> Unit = { item ->
        navController.navigate(if (item.opensList) Children(item.id, item.title, item.kind == ItemKind.Collection, item.backdropUrl ?: item.posterUrl) else Item(item.id))
    }
    sharedComposable<Children> { entry ->
        val route = entry.toRoute<Children>()
        ChildrenRoute(
            parentId = route.parentId,
            title = route.title,
            collectionHint = route.collection,
            imageHint = route.imageUrl,
            onBack = { navController.popBackStack() },
            onOpen = open,
            onOpenSeries = { seriesId -> navController.navigate(Item(seriesId)) },
            onExit = leave,
        )
    }
    sharedComposable<FilteredList> { entry ->
        FilteredListRoute(
            filter = entry.toRoute<FilteredList>().toFilter(),
            onBack = { navController.popBackStack() },
            onOpen = open,
            onExit = leave,
        )
    }
    sharedComposable<Item> { entry ->
        val itemId = entry.toRoute<Item>().itemId
        ItemRoute(
            itemId = itemId,
            onBack = { navController.popBackStack() },
            // The item itself, or for a show its next episode, or an episode tapped in a season's list.
            onPlay = { playId, startOver -> navController.navigate(Player(playId, startOver)) },
            onOpen = open,
            onOpenSeries = { seriesId -> navController.navigate(Item(seriesId)) },
            onOpenPerson = { personId -> navController.navigate(Person(personId)) },
            onExit = leave,
        )
    }
    sharedComposable<Person> { entry ->
        PersonRoute(
            personId = entry.toRoute<Person>().personId,
            onBack = { navController.popBackStack() },
            onOpen = open,
            onExit = leave,
        )
    }
}
