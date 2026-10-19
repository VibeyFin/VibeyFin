import SwiftUI
import Shared

struct ServersView: View {
    @StateObject private var model = ServersObservable()

    let onOpenServer: (Server) -> Void
    let onAddServer: () -> Void
    let onDiscoveredServer: (DiscoveredServer) -> Void

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
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
        VStack(spacing: Space.s6) {
            Text(Strings.shared.appName)
                .font(.brandDisplay)
                .tracking(-0.8)
                .foregroundStyle(.primary)
            VStack(spacing: Space.s4) {
                PrimaryButton(Strings.shared.addServer, action: onAddServer)
                if state.canSearch {
                    if state.searching {
                        SearchingLabel()
                    } else {
                        VStack(spacing: Space.s1) {
                            Text(Strings.shared.noServersFound)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            GhostButton(title: Strings.shared.searchAgain) { model.searchAgain() }
                        }
                    }
                }
            }
        }
        .screenMargin()
    }

    private func list(_ state: ServersUiState) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(Strings.shared.servers.titleCased())
                .screenTitle()
                .padding(.top, Space.s6)
                .padding(.bottom, Space.s5)
            ScrollView {
                LazyVStack(spacing: Space.s3) {
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
                            Text(Strings.shared.noNewServersFound)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
            }
            SecondaryButton(title: Strings.shared.addServer, action: onAddServer)
                .padding(.vertical, Space.s4)
        }
        .screenMargin()
    }

    private func nearbyHeader(_ state: ServersUiState) -> some View {
        HStack {
            Text(Strings.shared.onYourNetwork)
                .font(.headline)
                .foregroundStyle(.secondary)
            Spacer()
            if state.searching {
                SearchingLabel()
            } else {
                GhostButton(title: Strings.shared.searchAgain) { model.searchAgain() }
            }
        }
        .frame(height: 40)
        .padding(.top, Space.s3)
    }
}

private struct ServerRow: View {
    let server: Server

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 2) {
                Text(server.name)
                    .font(.title3.bold())
                    .foregroundStyle(.primary)
                Text(server.addresses.first ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if let version = server.version {
                    Text(Strings.shared.jellyfinVersion(version: version))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
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
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text(server.address)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text(Strings.shared.addToServers)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
    }
}
