import SwiftUI
import Shared

enum Route: Hashable {
    case addServer(address: String?, backAfterSave: Bool = false)
    /// `pinUserId` is the profile whose PIN the screen asks for first.
    case whoIsWatching(serverId: String, pinUserId: String? = nil)
    case serverDetails(serverId: String)
}

struct RootView: View {
    /// The screens before sign-in. Once signed in the tabs replace them.
    @State private var path: [Route] = []
    @State private var signedIn = false
    @State private var home: HomeObservable?
    @State private var flight: ProfileFlight?
    @State private var buttonFrame: CGRect = .zero
    @State private var homeButtonFrame: CGRect = .zero
    @State private var ready = false
    @StateObject private var startup = StartupObservable()
    @ObservedObject private var taps = NotificationTaps.shared

    /// Leaves the tabs and everything opened from them. Servers is the root, so back from "Who's
    /// watching?" has somewhere to go.
    private func leave(_ exit: HomeExit) {
        signedIn = false
        flight = nil
        homeButtonFrame = .zero
        home = nil
        if let who = exit as? HomeExitWhoIsWatching {
            path = [.whoIsWatching(serverId: who.serverId)]
        } else {
            path = []
        }
    }

    var body: some View {
        ZStack {
            if !signedIn {
                NavigationStack(path: $path) {
                    ServersView(
                        onOpenServer: { path.append(.whoIsWatching(serverId: $0.id)) },
                        onServerDetails: { path.append(.serverDetails(serverId: $0.id)) },
                        onAddServer: { path.append(.addServer(address: nil)) },
                        onDiscoveredServer: { path.append(.addServer(address: $0.address)) }
                    )
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: Route.self) { route in
                        switch route {
                        case .addServer(let address, let backAfterSave):
                            AddServerView(initialAddress: address, showNext: !backAfterSave) { serverId in
                                if backAfterSave {
                                    path.removeLast()
                                } else {
                                    path = [.whoIsWatching(serverId: serverId)]
                                }
                            }
                        case .whoIsWatching(let serverId, let pinUserId):
                            WhoIsWatchingView(
                                serverId: serverId,
                                pinFor: pinUserId.map { ProfileRef(serverId: serverId, userId: $0) },
                                flight: $flight,
                                buttonFrame: $buttonFrame,
                                onSwitchServer: { path = [] },
                                onServerDetails: { path.append(.serverDetails(serverId: serverId)) },
                                onSigningIn: { home = HomeObservable() },
                                // The tabs become the only screens. `leave` sets the path again on the way out.
                                onSignedIn: {
                                    if home == nil { home = HomeObservable() }
                                    if flight == nil {
                                        withAnimation(.easeOut(duration: 0.2)) { signedIn = true }
                                    } else {
                                        flight = nil
                                        signedIn = true
                                    }
                                }
                            )
                        case .serverDetails(let serverId):
                            ServerDetailsView(
                                serverId: serverId,
                                onAddAddress: { path.append(.addServer(address: nil, backAfterSave: true)) },
                                onRemoved: { path = [] }
                            )
                        }
                    }
                }
                .transition(.opacity)
            }
            if signedIn || flight != nil, let home {
                TabShell(onExit: leave, home: home)
                    .opacity(signedIn || flight?.step == 2 ? 1 : 0)
                    .allowsHitTesting(signedIn)
                    .environment(\.profileButtonHidden, flight != nil)
                    .environment(\.profileButtonFrame) { frame in
                        if UIScreen.main.bounds.intersects(frame) { homeButtonFrame = frame }
                    }
                    .transition(.opacity)
            }
            if let flight {
                ProfileFlightView(flight: flight, button: homeButtonFrame == .zero ? buttonFrame : homeButtonFrame)
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
                    home = HomeObservable()
                    signedIn = true
                } else if let who = destination as? StartDestinationWhoIsWatching {
                    path = [.whoIsWatching(serverId: who.serverId, pinUserId: who.pinFor?.userId)]
                }
                ready = true
                if !signedIn { taps.pending = nil }
            }
        }
        // A tapped notification opens its title only from the tabs (TabShell). Before sign-in the app stays where it decided to open,
        // "Who's watching?" with whatever PIN it asks for, and the tap is dropped.
        .onReceive(taps.$pending) { tap in
            if tap != nil && ready && !signedIn { taps.pending = nil }
        }
    }
}
