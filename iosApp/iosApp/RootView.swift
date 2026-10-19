import SwiftUI
import Shared

enum Route: Hashable {
    /// A non-nil address is checked as soon as the screen opens.
    case addServer(address: String?)
}

struct RootView: View {
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            ServersView(
                onAddServer: { path.append(.addServer(address: nil)) },
                onDiscoveredServer: { path.append(.addServer(address: $0.address)) }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .addServer(let address):
                    AddServerView(initialAddress: address)
                }
            }
        }
        .tint(Palette.text)
    }
}
