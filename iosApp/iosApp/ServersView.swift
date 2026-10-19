import SwiftUI
import Shared

struct ServersView: View {
    @StateObject private var model = ServersObservable()

    let onOpenServer: (Server) -> Void
    let onAddServer: () -> Void
    let onDiscoveredServer: (DiscoveredServer) -> Void

    var body: some View {
        ZStack {
            Palette.ink.ignoresSafeArea()
            // Nothing is drawn until the saved servers have been read, so the empty state never flashes.
            if let state = model.state, !state.loading {
                if state.servers.isEmpty && state.discovered.isEmpty {
                    emptyState(state)
                } else {
                    list(state)
                }
            }
        }
        .onAppear { model.start() }
    }

    private func emptyState(_ state: ServersUiState) -> some View {
        VStack(spacing: 32) {
            Text("VibeyFin").vibeTitle(size: 56, weight: .black, tracking: -2)
            VStack(spacing: 16) {
                Button("Add server", action: onAddServer)
                    .buttonStyle(PrimaryButtonStyle())
                if state.canSearch {
                    if state.searching {
                        SearchingLabel()
                    } else {
                        VStack(spacing: 4) {
                            Text("No servers found on your network.")
                                .font(.subheadline)
                                .foregroundColor(Palette.textMuted)
                            Button("Search again") { model.searchAgain() }
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(Palette.text)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 24)
    }

    private func list(_ state: ServersUiState) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Servers")
                .vibeTitle(size: 34)
                .padding(.top, 32)
                .padding(.bottom, 24)
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(state.servers, id: \.id) { server in
                        Button { onOpenServer(server) } label: {
                            ServerRow(server: server)
                        }
                        .buttonStyle(.plain)
                    }
                    if state.canSearch {
                        nearbyHeader(state)
                        ForEach(state.discovered, id: \.id) { server in
                            Button { onDiscoveredServer(server) } label: {
                                DiscoveredRow(server: server)
                            }
                            .buttonStyle(.plain)
                        }
                        if state.discovered.isEmpty && !state.searching {
                            Text("No new servers found.")
                                .font(.subheadline)
                                .foregroundColor(Palette.textMuted)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
            }
            Button("Add server", action: onAddServer)
                .buttonStyle(OutlineButtonStyle())
                .padding(.vertical, 16)
        }
        .padding(.horizontal, 24)
    }

    private func nearbyHeader(_ state: ServersUiState) -> some View {
        HStack {
            Text("On your network")
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(Palette.textMuted)
            Spacer()
            if state.searching {
                SearchingLabel()
            } else {
                Button("Search again") { model.searchAgain() }
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Palette.text)
            }
        }
        .frame(height: 40)
        .padding(.top, 12)
    }
}

private struct ServerRow: View {
    let server: Server

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 2) {
                Text(server.name)
                    .font(.system(size: 22, weight: .bold))
                    .tracking(-0.5)
                    .foregroundColor(Palette.text)
                Text(server.addresses.first ?? "")
                    .font(.subheadline)
                    .foregroundColor(Palette.textMuted)
                if let version = server.version {
                    Text("Jellyfin \(version)")
                        .font(.footnote)
                        .foregroundColor(Palette.textMuted)
                }
            }
        }
    }
}

private struct DiscoveredRow: View {
    let server: DiscoveredServer

    var body: some View {
        Card {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(server.name)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(Palette.text)
                    Text(server.address)
                        .font(.subheadline)
                        .foregroundColor(Palette.textMuted)
                }
                Spacer()
                Text("Add")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Palette.textMuted)
            }
        }
    }
}
