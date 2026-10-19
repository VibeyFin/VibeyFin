import SwiftUI
import Shared

enum Route: Hashable {
    /// A non-nil address is checked as soon as the screen opens.
    case addServer(address: String?)
    case whoIsWatching(serverId: String)
    case home
}

struct RootView: View {
    @State private var path: [Route] = []

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
                    HomeView(onSessionEnded: { serverId in path = [.whoIsWatching(serverId: serverId)] })
                        .navigationBarBackButtonHidden(true)
                }
            }
        }
        .tint(Palette.text)
    }
}
