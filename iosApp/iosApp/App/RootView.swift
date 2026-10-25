import SwiftUI
import Shared

enum Route: Hashable {
    /// A non-nil address is checked as soon as the screen opens.
    case addServer(address: String?)
    /// `pinUserId` is the profile whose PIN the screen asks for first; with `openAfterPin` the right PIN opens it.
    case whoIsWatching(serverId: String, pinUserId: String? = nil, openAfterPin: Bool = false)
}

struct RootView: View {
    /// The screens before sign-in. Once signed in the tabs replace them.
    @State private var path: [Route] = []
    @State private var signedIn = false
    @State private var ready = false
    @StateObject private var startup = StartupObservable()

    /// Leaves the tabs and everything opened from them. Servers is the root, so back from "Who's
    /// watching?" has somewhere to go.
    private func leave(_ exit: HomeExit) {
        signedIn = false
        if let who = exit as? HomeExitWhoIsWatching {
            path = [.whoIsWatching(serverId: who.serverId)]
        } else {
            path = []
        }
    }

    var body: some View {
        Group {
            if signedIn {
                TabShell(onExit: leave)
            } else {
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
                        case .whoIsWatching(let serverId, let pinUserId, let openAfterPin):
                            // The tabs become the only screens: back from them must not return to sign-in.
                            WhoIsWatchingView(
                                serverId: serverId,
                                pinFor: pinUserId.map { ProfileRef(serverId: serverId, userId: $0) },
                                openAfterPin: openAfterPin,
                                onSignedIn: { path = []; signedIn = true }
                            )
                        }
                    }
                }
            }
        }
        .tint(Brand.accent)
        // Blank background until the app has worked out where to open, a moment of storage reads.
        .overlay {
            if !ready { Color(.systemBackground).ignoresSafeArea() }
        }
        .onAppear {
            startup.start { destination in
                if destination is StartDestinationHome {
                    signedIn = true
                } else if let who = destination as? StartDestinationWhoIsWatching {
                    path = [.whoIsWatching(serverId: who.serverId, pinUserId: who.pinFor?.userId, openAfterPin: who.openAfterPin)]
                }
                ready = true
            }
        }
    }
}
