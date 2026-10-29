import SwiftUI
import Shared

/// What opens inside a tab, over its root.
enum TabRoute: Hashable {
    /// The root page of Settings; the pages its links open are `settingsPage`.
    case settings
    case settingsPage(SettingsPageId)
    /// What is inside a library, folder, series or season; `title` shows while it loads.
    case children(parentId: String, title: String)
    case item(itemId: String)
    /// A cast member's page.
    case person(personId: String)
    /// A category page: `kind` is `shows`, `movies`, `new` or `genre`; `id` and `name` are the genre's own.
    case filtered(kind: String, id: String?, name: String?)
}

private extension TabRoute {
    /// The route for a category button or a genre, from what the shared Home state asked to open.
    static func opening(_ filter: HomeFilter) -> TabRoute {
        if let genre = filter as? HomeFilterGenre {
            return .filtered(kind: "genre", id: genre.id, name: genre.name)
        }
        if filter is HomeFilterMovies { return .filtered(kind: "movies", id: nil, name: nil) }
        if filter is HomeFilterNew { return .filtered(kind: "new", id: nil, name: nil) }
        if filter is HomeFilterMyList { return .filtered(kind: "mylist", id: nil, name: nil) }
        if filter is HomeFilterLibrary { return .filtered(kind: "library", id: nil, name: nil) }
        return .filtered(kind: "shows", id: nil, name: nil)
    }

    /// The shared filter a category page is built from.
    func filter() -> HomeFilter? {
        guard case .filtered(let kind, let id, let name) = self else { return nil }
        switch kind {
        case "movies": return HomeFilterMovies.shared
        case "new": return HomeFilterNew.shared
        case "mylist": return HomeFilterMyList.shared
        case "library": return HomeFilterLibrary.shared
        case "genre": return HomeFilterGenre(id: id ?? "", name: name ?? "")
        default: return HomeFilterShows.shared
        }
    }
}

/// Holds the open Settings' model by reference. The stack can build a pushed page with the destination closure of the body before the
/// push, so a model held in `@State` directly was still nil there on the first open (an empty, black page) and the previous one after.
private final class SettingsSession {
    var current: SettingsObservable?
}

private enum MainTab: Hashable {
    case home, search, library, downloads
}

/// Home, Library and Downloads, each with its own navigation stack, so a tab keeps its place while
/// another is open. The system draws the bar (floating glass on iOS 26).
struct TabShell: View {
    /// Called when the tabs should be left: the user chose to, or the session ended.
    let onExit: (HomeExit) -> Void

    @State private var selection: MainTab = .home
    @Namespace private var zoomNamespace
    @StateObject private var tileMenu = TileMenuObservable()
    /// One shared Settings ViewModel for the whole page tree, made again each time Settings opens so the PIN to open it is asked every time.
    @State private var settings = SettingsSession()
    @StateObject private var player = PlayerSession()
    // Offline (no network, or the switch in Settings): three tabs, from what is on the device. A toast says so as it starts.
    @StateObject private var offlineMode = OfflineModeObservable()
    @StateObject private var banner = AppBannerObservable()
    @Environment(\.openURL) private var openURL
    @State private var homePath: [TabRoute] = []
    @State private var searchPath: [TabRoute] = []
    @State private var libraryPath: [TabRoute] = []
    @State private var downloadsPath: [TabRoute] = []
    @ObservedObject private var taps = NotificationTaps.shared

    private var offline: Bool { offlineMode.isOffline }

    var body: some View {
        VStack(spacing: 0) {
            OfflineBannerView(model: banner)
            tabs
        }
        // Going offline is said once, in a toast over the bottom, not a strip across the top.
        .offlineToast(banner)
        .onAppear {
            offlineMode.start()
            banner.start()
        }
        // Library needs the server: going offline while on it moves to Home, which the bar still offers.
        .onChange(of: offline) { isOffline in
            if isOffline && selection == .library { selection = .home }
        }
    }

    private var tabs: some View {
        TabView(selection: tappedSelection) {
            stack($homePath) {
                if offline {
                    OfflineHomeView(
                        onOpenDownloads: { selection = .downloads },
                        onSettings: { settings.current = SettingsObservable(); homePath.append(.settings) },
                        onExit: onExit
                    )
                    .navigationBarBackButtonHidden(true)
                    .toolbar(.hidden, for: .navigationBar)
                } else {
                    HomeView(
                        onExit: onExit,
                        onOpen: { open($0, in: $homePath) },
                        onDetails: { homePath.append(.item(itemId: $0)) },
                        onSettings: { settings.current = SettingsObservable(); homePath.append(.settings) },
                        onOpenList: { homePath.append(TabRoute.opening($0)) }
                    )
                    .navigationBarBackButtonHidden(true)
                    .backMenuTitle(Strings.shared.tabHome.titleCased())
                    // Home draws its own title, so the system bar above it would only be empty space.
                    .toolbar(.hidden, for: .navigationBar)
                }
            }
            .tabItem { Label(Strings.shared.tabHome.titleCased(), systemImage: "house") }
            .tag(MainTab.home)

            stack($searchPath) {
                SearchView(
                    onOpenResult: { result in
                        // Offline there is no page to open: what was found is on the device, so it plays (a show opens its downloads).
                        if offline {
                            if result.kind == .movie || result.kind == .episode {
                                player.play(result.id)
                            } else {
                                selection = .downloads
                            }
                            return
                        }
                        switch result.kind {
                        case .person: searchPath.append(.person(personId: result.id))
                        case .collection: searchPath.append(.children(parentId: result.id, title: result.title))
                        default: searchPath.append(.item(itemId: result.id))
                        }
                    },
                    onExit: onExit
                )
            }
            .tabItem { Label(Strings.shared.tabSearch.titleCased(), systemImage: "magnifyingglass") }
            .tag(MainTab.search)

            if !offline {
                stack($libraryPath) { FilteredListView(filter: HomeFilterLibrary.shared, isRoot: true, onOpen: { open($0, in: $libraryPath) }, onExit: onExit) }
                    .tabItem { Label(Strings.shared.tabLibrary.titleCased(), systemImage: "play.rectangle.on.rectangle") }
                    .tag(MainTab.library)
            }

            stack($downloadsPath) {
                DownloadsView(onOpen: { downloadsPath.append(.item(itemId: $0)) }, offline: offline, onExit: onExit)
            }
                .tabItem { Label(Strings.shared.tabDownloads.titleCased(), systemImage: "arrow.down.circle") }
                .tag(MainTab.downloads)
        }
        .environment(\.zoomNamespace, zoomNamespace)
        .environment(\.tileMenu, tileMenu)
        .onAppear { tileMenu.start() }
        .onReceive(taps.$pending) { tap in
            if let tap { openTapped(tap) }
        }
        // The long-press menu of a poster, wherever the poster is.
        .sheet(isPresented: Binding(get: { tileMenu.state?.item != nil }, set: { if !$0 { tileMenu.close() } })) {
            if let state = tileMenu.state {
                TileMenuSheetView(
                    state: state,
                    onDetails: {
                        let item = state.item
                        tileMenu.close()
                        if let item { openFromMenu(item) }
                    },
                    onTrailer: { trailer in
                        tileMenu.close()
                        if let local = trailer as? TrailerLocal {
                            player.play(local.itemId)
                        } else if let remote = trailer as? TrailerRemote, let url = URL(string: remote.url) {
                            openURL(url)
                        }
                    },
                    onMyList: { tileMenu.toggleFavorite() },
                    onDownload: { tileMenu.download() },
                    onDownloadAction: { tileMenu.act($0) }
                )
                // The questions the menu's Download row can raise: how many episodes of a show, and before a download is deleted.
                .downloadPrompts(
                    prompt: state.downloadPrompt,
                    removal: state.removePrompt,
                    onToggleUnwatched: { tileMenu.toggleUnwatchedOnly() },
                    onConfirm: { tileMenu.confirmDownload() },
                    onDismiss: { tileMenu.dismissDownloadPrompt() },
                    onConfirmRemove: { tileMenu.confirmRemove() },
                    onDismissRemoval: { tileMenu.dismissRemoval() }
                )
            }
        }
        .environment(\.playerSession, player)
        // Playback covers the tabs, and goes out of sight while picture-in-picture plays, so the page it was opened from shows.
        .overlay {
            if let request = player.request {
                PlayerView(itemId: request.id, startOver: request.startOver, onExit: onExit)
                    .id(request.token)
                    .environment(\.playerSession, player)
                    .opacity(player.minimised ? 0 : 1)
                    .allowsHitTesting(!player.minimised)
                    .accessibilityHidden(player.minimised)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: player.request)
    }

    /// Choosing the tab you are already on goes back to its root.
    private var tappedSelection: Binding<MainTab> {
        Binding(
            get: { selection },
            set: { tab in
                if tab == selection {
                    switch tab {
                    case .home: homePath = []
                    case .search: searchPath = []
                    case .library: libraryPath = []
                    case .downloads: downloadsPath = []
                    }
                }
                selection = tab
            }
        )
    }

    /// A tapped new-title notification opens the title's page on Home, but only for the profile signed in now: it already got past any
    /// PIN to open. A notification for another profile changes nothing, since leaving this one could skip its PIN to switch profile.
    private func openTapped(_ tap: NewTitleTap) {
        taps.pending = nil
        guard NewTitlesModel().isSignedIn(serverId: tap.serverId, userId: tap.userId) else { return }
        selection = .home
        homePath.append(.item(itemId: tap.itemId))
    }

    /// Details from a poster's menu: opens in the tab on screen.
    private func openFromMenu(_ item: MediaItem) {
        switch selection {
        case .home: open(item, in: $homePath)
        case .search: open(item, in: $searchPath)
        case .library: open(item, in: $libraryPath)
        case .downloads: open(item, in: $downloadsPath)
        }
    }

    /// A folder opens its list; anything else opens its page.
    private func open(_ item: MediaItem, in path: Binding<[TabRoute]>) {
        path.wrappedValue.append(item.opensList ? .children(parentId: item.id, title: item.title) : .item(itemId: item.id))
    }

    private func stack<Root: View>(_ path: Binding<[TabRoute]>, @ViewBuilder root: () -> Root) -> some View {
        NavigationStack(path: path) {
            root()
                .navigationDestination(for: TabRoute.self) { route in
                    switch route {
                    case .settings:
                        if let model = settings.current { SettingsEntryView(model: model, onExit: onExit) }
                    case .settingsPage(let id):
                        if let model = settings.current { SettingsPageView(pageId: id, model: model, onExit: onExit) }
                    case .children(let parentId, let title):
                        ChildrenView(parentId: parentId, title: title, onOpen: { open($0, in: path) }, onOpenSeries: { path.wrappedValue.append(.item(itemId: $0)) }, onExit: onExit)
                    case .item(let itemId):
                        // On iOS 18 the page zooms out of the poster that was tapped.
                        ItemView(itemId: itemId, onOpen: { open($0, in: path) }, onOpenSeries: { path.wrappedValue.append(.item(itemId: $0)) }, onOpenPerson: { path.wrappedValue.append(.person(personId: $0)) }, onExit: onExit)
                            .zoomDestination(itemId)
                    case .person(let personId):
                        PersonView(personId: personId, onOpen: { open($0, in: path) }, onExit: onExit)
                    case .filtered:
                        if let filter = route.filter() {
                            FilteredListView(filter: filter, onOpen: { open($0, in: path) }, onExit: onExit)
                        }
                    }
                }
        }
    }
}
