import SwiftUI
import Shared

enum Route: Hashable {
    /// A non-nil address is checked as soon as the screen opens.
    case addServer(address: String?)
    case whoIsWatching(serverId: String)
    case home
    case settings
}

struct RootView: View {
    @State private var path: [Route] = []
    @State private var ready = false
    @StateObject private var startup = StartupObservable()

    var body: some View {
        NavigationStack(path: $path) {
            ServersView(
                onOpenServer: { path.append(.whoIsWatching(serverId: $0.id)) },
                onAddServer: { path.append(.addServer(address: nil)) },
                onDiscoveredServer: { path.append(.addServer(address: $0.address)) }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .addServer(let address):
                    AddServerView(initialAddress: address)
                case .whoIsWatching(let serverId):
                    // Home becomes the only screen: back from it must not return to sign-in.
                    WhoIsWatchingView(serverId: serverId, onSignedIn: { path = [.home] })
                case .home:
                    HomeView(
                        onExit: { exit in
                            // Servers is the root, so back from "Who's watching?" has somewhere to go.
                            if let who = exit as? HomeExitWhoIsWatching {
                                path = [.whoIsWatching(serverId: who.serverId)]
                            } else {
                                path = []
                            }
                        },
                        onSettings: { path.append(.settings) }
                    )
                    .navigationBarBackButtonHidden(true)
                case .settings:
                    SettingsView()
                }
            }
        }
        .tint(Palette.text)
        // Blank ink until the app has worked out where to open, a moment of storage reads.
        .overlay {
            if !ready { Palette.ink.ignoresSafeArea() }
        }
        .onAppear {
            startup.start { destination in
                if destination is StartDestinationHome {
                    path = [.home]
                } else if let who = destination as? StartDestinationWhoIsWatching {
                    path = [.whoIsWatching(serverId: who.serverId)]
                }
                ready = true
            }
        }
    }
}
