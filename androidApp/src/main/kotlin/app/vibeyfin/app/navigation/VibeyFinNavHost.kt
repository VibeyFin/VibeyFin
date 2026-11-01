@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.vibeyfin.app.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.vibeyfin.app.addserver.AddServerRoute
import app.vibeyfin.app.children.ChildrenRoute
import app.vibeyfin.app.components.AppBanner
import app.vibeyfin.app.components.TileMenuSheet
import app.vibeyfin.app.downloads.DownloadConfirmDialog
import app.vibeyfin.app.downloads.DownloadsRoute
import app.vibeyfin.app.downloads.RemoveConfirmDialog
import app.vibeyfin.app.downloads.rememberDownloadNotificationPermission
import app.vibeyfin.app.filtered.FilteredListRoute
import app.vibeyfin.app.home.HomeRoute
import app.vibeyfin.app.home.OfflineHomeRoute
import app.vibeyfin.app.item.ItemRoute
import app.vibeyfin.app.person.PersonRoute
import app.vibeyfin.app.player.PlayerRoute
import app.vibeyfin.app.search.SearchRoute
import app.vibeyfin.app.seerr.MyRequestsRoute
import app.vibeyfin.app.seerr.SeerrListRoute
import app.vibeyfin.app.seerr.SeerrMenuSheet
import app.vibeyfin.app.seerr.SeerrTitleRoute
import app.vibeyfin.app.servers.ServersRoute
import app.vibeyfin.app.settings.SeerrSettingsRoute
import app.vibeyfin.app.settings.SettingsRoute
import app.vibeyfin.app.settings.pageIdOrRoot
import app.vibeyfin.app.startup.StartupRoute
import app.vibeyfin.app.theme.isTabletWidth
import app.vibeyfin.app.whoswatching.WhoIsWatchingRoute
import app.vibeyfin.domain.HomeFilter
import app.vibeyfin.domain.OfflineMode
import app.vibeyfin.domain.StartDestination
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.domain.model.SeerrMediaType
import app.vibeyfin.domain.model.SeerrTitle
import app.vibeyfin.ui.banner.AppBannerViewModel
import app.vibeyfin.ui.home.HomeExit
import app.vibeyfin.ui.item.Trailer
import app.vibeyfin.ui.notifications.NotificationOpenViewModel
import app.vibeyfin.ui.notifications.NotificationRoute
import app.vibeyfin.ui.seerr.SeerrListKind
import app.vibeyfin.ui.seerr.SeerrNoticeAction
import app.vibeyfin.ui.settings.SettingsPageId
import app.vibeyfin.ui.tile.TileMenuViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val SLIDE_MS = 180

private const val DETAIL_FADE_MS = 300

// Quicker than the page fades in, so it dims first and the page rises out of the dark
private const val DETAIL_UNDER_FADE_MS = 200

// A switch, not an opening: cross-fade with no scale
private const val TAB_FADE_MS = 110

private const val SLIDE_FRACTION = 5

private const val OPEN_START_SCALE = 0.92f

// Gentle, so it does not fight the rotation
private const val PLAYER_FADE_MS = 320

private const val PLAYER_START_SCALE = 0.94f

private fun NavBackStackEntry.opensPlayer() = destination.hasRoute<Player>()

private fun NavBackStackEntry.isTabRoot() = destination.hasRoute<Home>() || destination.hasRoute<Search>() || destination.hasRoute<Library>() || destination.hasRoute<Downloads>()

private fun NavBackStackEntry.isDetail() = destination.hasRoute<Item>()

private fun enter(target: NavBackStackEntry) = if (target.isDetail()) {
    // The poster grows into the page's picture (a shared element), so the page itself only fades in under it
    fadeIn(tween(DETAIL_FADE_MS))
} else if (target.isTabRoot()) {
    fadeIn(tween(TAB_FADE_MS))
} else if (target.opensPlayer()) {
    fadeIn(tween(PLAYER_FADE_MS)) + scaleIn(tween(PLAYER_FADE_MS), initialScale = PLAYER_START_SCALE)
} else {
    scaleIn(tween(SLIDE_MS), initialScale = OPEN_START_SCALE) + fadeIn(tween(SLIDE_MS))
}

private fun exit(target: NavBackStackEntry) = fadeOut(
    tween(
        if (target.isDetail()) {
            DETAIL_UNDER_FADE_MS
        } else if (target.isTabRoot()) {
            TAB_FADE_MS
        } else if (target.opensPlayer()) {
            PLAYER_FADE_MS
        } else {
            SLIDE_MS
        },
    ),
)

private fun popEnter(leaving: NavBackStackEntry) = if (leaving.isDetail()) fadeIn(tween(DETAIL_FADE_MS)) else slideInHorizontally(tween(SLIDE_MS)) { -it / SLIDE_FRACTION } + fadeIn(tween(SLIDE_MS))

private fun popExit(leaving: NavBackStackEntry) = if (leaving.isDetail()) fadeOut(tween(DETAIL_FADE_MS)) else slideOutHorizontally(tween(SLIDE_MS)) { it / SLIDE_FRACTION } + fadeOut(tween(SLIDE_MS))

@Composable
fun VibeyFinNavHost(notificationOpen: NotificationOpenViewModel) {
    val navController = rememberNavController()

    val open: (MediaItem) -> Unit = navController::openItem

    // Leaves the tabs and everything opened from them. Servers goes underneath "Who's watching?" so
    // back from it has somewhere to go. The tabs' saved back stacks belong to this profile: dropped.
    val leave: (HomeExit) -> Unit = { exit ->
        navController.navigate(Servers) { popUpTo(navController.graph.id) }
        Tab.entries.forEach { navController.clearBackStack(it.graph) }

        if (exit is HomeExit.WhoIsWatching) navController.navigate(WhoIsWatching(exit.serverId))
    }

    val appOffline: OfflineMode = koinInject()
    val offline by appOffline.isOffline.collectAsStateWithLifecycle()
    val bannerModel: AppBannerViewModel = koinViewModel()
    val banner by bannerModel.banner.collectAsStateWithLifecycle()

    ToastEffect(bannerModel)

    val backStackEntry by navController.currentBackStackEntryAsState()

    // The bar belongs to the three tabs and what opens from them; sign-in, settings and the player are without it
    val currentTab = backStackEntry?.destination?.hierarchy?.let { path ->
        Tab.entries.firstOrNull { tab -> path.any { it.hasRoute(tab.graph::class) } }
    }

    val select: (Tab) -> Unit = { tab ->
        if (tab == currentTab) {
            navController.popBackStack(tab.root, inclusive = false)
        } else {
            navController.navigate(tab.graph) {
                // Everything above the root graph is a tab's stack: keep it for when the tab returns
                popUpTo(navController.graph.id) { saveState = true }

                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Library needs the server: going offline while on it moves to Home (what the bar still offers)
    LaunchedEffect(offline, currentTab) { if (offline && currentTab == Tab.Library) select(Tab.Home) }

    NotificationRouteEffect(notificationOpen, backStackEntry, currentTab, select, leave, navController)

    // Back from the root of Library or Downloads goes to Home, as on any app with a bar, not out of the app
    val atOtherRoot = currentTab != null && currentTab != Tab.Home && backStackEntry?.destination?.hasRoute(currentTab.root::class) == true

    BackHandler(enabled = atOtherRoot) { select(Tab.Home) }

    // The player is the whole screen: no inset of its own for the camera cut-out, so no bar of the page's color beside the picture
    // (a foldable's cover screen, a phone turned sideways), and a black window behind it.
    val onPlayer = backStackEntry?.opensPlayer() == true
    val tabs = Tab.entries.filter { !offline || it != Tab.Library }

    Scaffold(
        containerColor = if (onPlayer) Color.Black else MaterialTheme.colorScheme.background,
        // No top inset: each screen keeps clear of the status bar itself, so a detail page's artwork runs up behind it
        // instead of under a strip of the page's color
        contentWindowInsets = if (onPlayer) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        },
    ) { padding ->
        val tileMenu: TileMenuViewModel = koinViewModel()
        val uriHandler = LocalUriHandler.current
        val openTrailerUrl: (String) -> Unit = { url -> uriHandler.openUri(url) }

        TileMenuHost(tileMenu, navController, openTrailerUrl)

        var seerrMenu by remember { mutableStateOf<SeerrTitle?>(null) }
        val seerrNotices = remember { SnackbarHostState() }

        seerrMenu?.let { title ->
            SeerrMenuHost(title, navController, seerrNotices, openTrailerUrl, onReopen = { seerrMenu = it }) { seerrMenu = null }
        }

        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Column(Modifier.fillMaxSize()) {
                val shownBanner = banner.takeIf { !onPlayer }

                // The banner takes the status bar's space, so the screen under it doesn't leave room for it again
                shownBanner?.let { AppBanner(it, onAction = bannerModel::onAction) }

                SharedTransitionLayout(Modifier.weight(1f).then(if (shownBanner != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides this,
                        LocalBottomBarPadding provides if (currentTab != null) FLOATING_BAR_SPACE else 0.dp,
                        LocalProfileButton provides { ProfileButton(offline, onSettings = { navController.navigate(Settings) }, onExit = leave) },
                        LocalTileLongPress provides { item -> tileMenu.onOpen(item) },
                        LocalSeerrLongPress provides { title -> seerrMenu = title },
                        LocalOpenTrailerUrl provides openTrailerUrl,
                    ) {
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
                            appGraph(navController, offline, select, leave)
                        }
                    }
                }
            }

            // What a request from a Seerr poster menu came to, over whatever screen the menu was on
            SnackbarHost(seerrNotices, Modifier.align(Alignment.BottomCenter).padding(bottom = if (currentTab != null) FLOATING_BAR_SPACE else 0.dp))

            if (currentTab != null) {
                FloatingNavigationBar(
                    selected = currentTab,
                    tabs = tabs,
                    onSelect = select,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private fun NavGraphBuilder.details(navController: NavHostController, leave: (HomeExit) -> Unit) {
    val open: (MediaItem) -> Unit = navController::openItem

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
            onPlay = { playId, startOver -> navController.navigate(Player(playId, startOver)) },
            onOpen = open,
            onOpenSeries = { seriesId -> navController.navigate(Item(seriesId)) },
            onOpenPerson = { personId -> navController.navigate(Person(personId)) },
            onExit = leave,
            onOpenSeerr = navController::openSeerr,
        )
    }

    sharedComposable<SeerrPage> { entry ->
        val route = entry.toRoute<SeerrPage>()

        SeerrTitleRoute(
            type = if (route.mediaType == "tv") SeerrMediaType.Show else SeerrMediaType.Movie,
            tmdbId = route.tmdbId,
            onBack = { navController.popBackStack() },
            // The library has it: its own page takes this one's place
            onOpenItem = { itemId -> navController.navigate(Item(itemId)) { popUpTo<SeerrPage> { inclusive = true } } },
            onOpenSeerr = navController::openSeerr,
        )
    }

    sharedComposable<SeerrList> { entry ->
        val kind = SeerrListKind.entries.firstOrNull { it.name == entry.toRoute<SeerrList>().kind } ?: SeerrListKind.Trending

        SeerrListRoute(kind, onBack = { navController.popBackStack() }, onOpen = navController::openSeerr)
    }

    sharedComposable<MyRequests> {
        MyRequestsRoute(onBack = { navController.popBackStack() }, onOpen = navController::openSeerr)
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

@Composable
private fun TileMenuHost(tileMenu: TileMenuViewModel, navController: NavHostController, openTrailerUrl: (String) -> Unit) {
    val menu by tileMenu.uiState.collectAsStateWithLifecycle()
    val ensureNotifications = rememberDownloadNotificationPermission()

    TileMenuSheet(
        state = menu,
        onDetails = {
            val item = menu.item

            tileMenu.onClose()

            if (item != null) navController.openItem(item)
        },
        onTrailer = { trailer ->
            tileMenu.onClose()

            when (trailer) {
                is Trailer.Local -> navController.navigate(Player(trailer.itemId))
                is Trailer.Remote -> openTrailerUrl(trailer.url)
            }
        },
        onMyList = tileMenu::onToggleFavorite,
        onDownload = tileMenu::onDownload,
        onDownloadAction = tileMenu::onDownloadAction,
        onDismiss = tileMenu::onClose,
    )

    menu.downloadPrompt?.let { prompt ->
        DownloadConfirmDialog(
            prompt,
            onToggleUnwatched = tileMenu::onToggleUnwatchedOnly,
            onConfirm = {
                ensureNotifications()
                tileMenu.onConfirmDownload()
            },
            onDismiss = tileMenu::onDismissDownloadPrompt,
        )
    }

    menu.removePrompt?.let { RemoveConfirmDialog(it, onConfirm = tileMenu::onConfirmRemove, onDismiss = tileMenu::onDismissRemoval) }
}

/** A Seerr title: the library's page when the library has it, otherwise Seerr's. */
internal fun NavHostController.openSeerr(title: SeerrTitle) {
    title.jellyfinItemId?.let { navigate(Item(it)) } ?: navigate(SeerrPage(title.tmdbId, if (title.type == SeerrMediaType.Show) "tv" else "movie"))
}

// The Seerr poster menu, over the whole app; what a request came to shows in the snackbar under it
@Composable
private fun SeerrMenuHost(
    title: SeerrTitle,
    navController: NavHostController,
    notices: SnackbarHostState,
    openTrailerUrl: (String) -> Unit,
    onReopen: (SeerrTitle) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    SeerrMenuSheet(
        title = title,
        onDetails = {
            onClose()
            navController.openSeerr(title)
        },
        onTrailer = { url ->
            onClose()
            openTrailerUrl(url)
        },
        onNotice = { notice ->
            scope.launch {
                val result = notices.showSnackbar(notice.text, actionLabel = notice.actionLabel, duration = SnackbarDuration.Short)

                // View opens the title; Try again brings the menu back for it (the request is one tap away again)
                if (result == SnackbarResult.ActionPerformed) {
                    when (notice.action) {
                        SeerrNoticeAction.View -> navController.openSeerr(title)
                        SeerrNoticeAction.TryAgain -> onReopen(title)
                        null -> Unit
                    }
                }
            }
        },
        onDismiss = onClose,
    )
}

private fun NavHostController.openItem(item: MediaItem) {
    navigate(if (item.opensList) Children(item.id, item.title, item.kind == ItemKind.Collection, item.backdropUrl ?: item.posterUrl) else Item(item.id))
}

private fun NavGraphBuilder.appGraph(navController: NavHostController, offline: Boolean, select: (Tab) -> Unit, leave: (HomeExit) -> Unit) {
    entryScreens(navController)
    tabGraphs(navController, offline, select, leave)
    appScreens(navController, leave)
}

// Startup and signing in: everything before the tabs
private fun NavGraphBuilder.entryScreens(navController: NavHostController) {
    composable<Startup> {
        StartupRoute(
            onDecided = { destination ->
                when (destination) {
                    StartDestination.Servers ->
                        navController.navigate(Servers) { popUpTo(Startup) { inclusive = true } }

                    is StartDestination.WhoIsWatching -> {
                        // Servers sits underneath so back from "Who's watching?" has somewhere to go
                        navController.navigate(Servers) { popUpTo(Startup) { inclusive = true } }
                        navController.navigate(WhoIsWatching(destination.serverId, destination.pinFor?.userId))
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
        val route = entry.toRoute<WhoIsWatching>()

        WhoIsWatchingRoute(
            serverId = route.serverId,
            pinFor = route.pinUserId?.let { ProfileRef(route.serverId, it) },
            onBack = { navController.popBackStack() },
            // Home becomes the root: back from it leaves the app instead of returning to sign-in
            onSignedIn = { navController.navigate(HomeTab) { popUpTo(Servers) { inclusive = true } } },
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

private fun NavGraphBuilder.tabGraphs(navController: NavHostController, offline: Boolean, select: (Tab) -> Unit, leave: (HomeExit) -> Unit) {
    val open: (MediaItem) -> Unit = navController::openItem

    // One graph per tab, each with the screens that open inside it: the same Item and Children
    // screens, once per graph, so each tab has its own back stack.
    navigation<HomeTab>(startDestination = Home) {
        sharedComposable<Home> {
            if (offline) {
                OfflineHomeRoute(
                    onPlay = { itemId -> navController.navigate(Player(itemId)) },
                    onOpenDownloads = { select(Tab.Downloads) },
                    onExit = leave,
                )
            } else {
                HomeRoute(
                    onExit = leave,
                    onOpen = open,
                    onOpenSeerr = navController::openSeerr,
                    onSeerrSeeAll = { kind -> navController.navigate(SeerrList(kind.name)) },
                    onMyRequests = { navController.navigate(MyRequests) },
                    onPlay = { itemId -> navController.navigate(Player(itemId)) },
                    onDetails = { itemId -> navController.navigate(Item(itemId)) },
                    onOpenList = { navController.navigate(it.toRoute()) },
                )
            }
        }

        details(navController, leave)
    }

    navigation<SearchTab>(startDestination = Search) {
        sharedComposable<Search> {
            SearchRoute(
                onOpenSeerr = navController::openSeerr,
                onOpenResult = { result ->
                    navController.navigate(
                        when {
                            // Offline there is no page to open: what was found is on the device, so it plays
                            offline && (result.kind == SearchKind.Movie || result.kind == SearchKind.Episode) -> Player(result.id)

                            result.kind == SearchKind.Person -> Person(result.id)

                            result.kind == SearchKind.Collection -> Children(result.id, result.title, collection = true, imageUrl = result.backdropUrl ?: result.imageUrl)

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
        sharedComposable<Downloads> {
            DownloadsRoute(
                onOpen = { itemId -> navController.navigate(Item(itemId)) },
                onPlay = { itemId -> navController.navigate(Player(itemId)) },
                offline = offline,
                onExit = leave,
            )
        }

        details(navController, leave)
    }
}

// Playback and Settings, which are opened from the tabs and leave them
private fun NavGraphBuilder.appScreens(navController: NavHostController, leave: (HomeExit) -> Unit) {
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
        SettingsRoute(
            pageId = SettingsPageId.Root,
            onOpen = { navController.navigate(SettingsPageRoute(it.name)) },
            onExit = leave,
            onBack = { navController.popBackStack() },
            twoPane = isTabletWidth(),
        )
    }

    composable<SettingsMyRequests> {
        MyRequestsRoute(onBack = { navController.popBackStack() }, onOpen = navController::openSeerr)
    }

    composable<SettingsPageRoute> { entry ->
        // An id this build does not know opens the root page instead of crashing
        val id = pageIdOrRoot(entry.toRoute<SettingsPageRoute>().id)

        // One SettingsViewModel for the whole Settings flow, kept by the root entry: the PIN is asked once and
        // leaving Settings discards it. Without a root entry on the stack (deep link, restored state) the page keeps its own.
        val settingsRoot = remember(entry) { runCatching { navController.getBackStackEntry<Settings>() }.getOrNull() }

        // Seerr has an address field, which the shared Settings rows don't draw: it is a screen of its own
        if (id == SettingsPageId.Seerr) {
            SeerrSettingsRoute(onBack = { navController.popBackStack() }, onMyRequests = { navController.navigate(SettingsMyRequests) })
            return@composable
        }

        SettingsRoute(
            pageId = id,
            viewModel = koinViewModel(viewModelStoreOwner = settingsRoot ?: entry),
            onOpen = { navController.navigate(SettingsPageRoute(it.name)) },
            onExit = leave,
            onBack = { navController.popBackStack() },
        )
    }
}

// Going offline is said once, in the system's own toast, not a strip over the page
@Composable
private fun ToastEffect(bannerModel: AppBannerViewModel) {
    val toast by bannerModel.toast.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(toast) {
        val message = toast ?: return@LaunchedEffect

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        bannerModel.onToastShown()
    }
}

// A tapped new-title notification is routed once the app is inside a profile, never during startup or "Who's watching?"
@Composable
private fun NotificationRouteEffect(
    notificationOpen: NotificationOpenViewModel,
    backStackEntry: NavBackStackEntry?,
    currentTab: Tab?,
    select: (Tab) -> Unit,
    leave: (HomeExit) -> Unit,
    navController: NavHostController,
) {
    val inApp = backStackEntry?.destination?.isInProfile() == true

    LaunchedEffect(inApp) { notificationOpen.onInApp(inApp) }

    val notificationRoute by notificationOpen.route.collectAsStateWithLifecycle()

    LaunchedEffect(notificationRoute) {
        when (val route = notificationRoute ?: return@LaunchedEffect) {
            is NotificationRoute.Item -> {
                // The item opens inside the tab the user is on, or inside Home from the player or Settings
                if (currentTab == null) select(Tab.Home)

                navController.navigate(Item(route.itemId))
            }

            is NotificationRoute.WhoIsWatching -> leave(HomeExit.WhoIsWatching(route.serverId))
        }

        notificationOpen.onRouted()
    }
}

private fun NavDestination.isInProfile() = !(hasRoute<Startup>() || hasRoute<Servers>() || hasRoute<WhoIsWatching>() || hasRoute<AddServer>())
