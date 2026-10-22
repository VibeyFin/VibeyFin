import SwiftUI
import Shared

/// What opens inside a tab, over its root.
enum TabRoute: Hashable {
    case settings
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

private enum MainTab: Hashable {
    case home, search, library, downloads
}

/// Home, Library and Downloads, each with its own navigation stack, so a tab keeps its place while
/// another is open. The system draws the bar (floating glass on iOS 26).
struct TabShell: View {
    /// Called when the tabs should be left: the user chose to, or the session ended.
    let onExit: (HomeExit) -> Void

    @State private var selection: MainTab = .home
    @State private var homePath: [TabRoute] = []
    @State private var searchPath: [TabRoute] = []
    @State private var libraryPath: [TabRoute] = []
    @State private var downloadsPath: [TabRoute] = []

    var body: some View {
        TabView(selection: tappedSelection) {
            stack($homePath) {
                HomeView(
                    onExit: onExit,
                    onOpen: { open($0, in: $homePath) },
                    onDetails: { homePath.append(.item(itemId: $0)) },
                    onSettings: { homePath.append(.settings) },
                    onOpenList: { homePath.append(TabRoute.opening($0)) }
                )
                .navigationBarBackButtonHidden(true)
            }
            .tabItem { Label(Strings.shared.tabHome.titleCased(), systemImage: "house") }
            .tag(MainTab.home)

            stack($searchPath) {
                SearchView(
                    onOpenResult: { result in
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

            stack($libraryPath) { FilteredListView(filter: HomeFilterLibrary.shared, isRoot: true, onOpen: { open($0, in: $libraryPath) }, onExit: onExit) }
                .tabItem { Label(Strings.shared.tabLibrary.titleCased(), systemImage: "play.rectangle.on.rectangle") }
                .tag(MainTab.library)

            stack($downloadsPath) { NotAvailableYetView() }
                .tabItem { Label(Strings.shared.tabDownloads.titleCased(), systemImage: "arrow.down.circle") }
                .tag(MainTab.downloads)
        }
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
                        SettingsView()
                    case .children(let parentId, let title):
                        ChildrenView(parentId: parentId, title: title, onOpen: { open($0, in: path) }, onOpenSeries: { path.wrappedValue.append(.item(itemId: $0)) }, onExit: onExit)
                    case .item(let itemId):
                        ItemView(itemId: itemId, onOpen: { open($0, in: path) }, onOpenSeries: { path.wrappedValue.append(.item(itemId: $0)) }, onOpenPerson: { path.wrappedValue.append(.person(personId: $0)) }, onExit: onExit)
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

/// What Library and Downloads show until their features land.
private struct NotAvailableYetView: View {
    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            Text(Strings.shared.notAvailableYet)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .screenMargin()
        }
    }
}
