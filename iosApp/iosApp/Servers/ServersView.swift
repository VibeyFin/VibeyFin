import SwiftUI
import Shared

struct ServersView: View {
    @StateObject private var model = ServersObservable()

    let onOpenServer: (Server) -> Void
    let onServerDetails: (Server) -> Void
    let onAddServer: () -> Void
    let onDiscoveredServer: (DiscoveredServer) -> Void

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            // Nothing is drawn until the saved servers have been read, so the first-launch page never flashes.
            if let state = model.state, !state.loading {
                Group {
                    if state.servers.isEmpty {
                        welcome(state)
                    } else {
                        list(state)
                    }
                }
                .frame(maxWidth: 720)
            }
        }
        .onAppear { model.start() }
        .confirmationDialog(
            model.state?.confirmRemove.map { Strings.shared.removeServerTitle(name: $0.server.name) } ?? "",
            isPresented: Binding(get: { model.state?.confirmRemove != nil }, set: { if !$0 { model.dismissRemove() } }),
            titleVisibility: .visible
        ) {
            Button(Strings.shared.removeServer.titleCased(), role: .destructive) { model.confirmRemove() }
        } message: {
            Text(Strings.shared.removeServerMessage(profiles: model.state?.confirmRemove?.profileCount ?? 0))
        }
    }

    private func welcome(_ state: ServersUiState) -> some View {
        VStack(spacing: Space.s3) {
            Spacer()
            Text(Strings.shared.appName)
                .font(.brandDisplay)
                .tracking(-0.8)
                .foregroundStyle(.primary)
            Text(Strings.shared.welcomeLead)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer()
            if state.canSearch {
                nearbyHeader(state, spinner: !state.discovered.isEmpty)
                if !state.discovered.isEmpty {
                    discoveredRows(state, compact: false)
                } else if state.searching {
                    Card { SearchingLabel(text: Strings.shared.lookingForServers) }
                } else {
                    lead(Strings.shared.noServersFoundHelp, alignment: .leading)
                }
            } else {
                lead(Strings.shared.welcomeAddressHelp, alignment: .center)
            }
            if state.discovered.isEmpty {
                PrimaryButton(Strings.shared.addServer, action: onAddServer)
            } else {
                SecondaryButton(title: Strings.shared.addServerByAddress, systemImage: "plus", action: onAddServer)
            }
        }
        .padding(.bottom, Space.s5)
        .screenMargin()
    }

    private func list(_ state: ServersUiState) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            TabHeader(title: Strings.shared.servers, horizontalMargin: false) { Color.clear.frame(width: 44, height: 44) }
            ScrollView {
                LazyVStack(spacing: Space.s3) {
                    ForEach(state.servers, id: \.server.id) { row in
                        SavedRow(
                            row: row,
                            onOpen: { onOpenServer(row.server) },
                            onDetails: { onServerDetails(row.server) },
                            onRemove: { model.removeServer(row.server.id) }
                        )
                    }
                    if state.canSearch {
                        nearbyHeader(state, spinner: true)
                        discoveredRows(state, compact: true)
                        if state.discovered.isEmpty && !state.searching {
                            lead(Strings.shared.noNewServersFound, alignment: .leading)
                        }
                    }
                }
                .padding(.top, Space.s1)
            }
            SecondaryButton(title: Strings.shared.addServer, systemImage: "plus", action: onAddServer)
                .padding(.vertical, Space.s4)
        }
        .screenMargin()
    }

    private func lead(_ text: String, alignment: TextAlignment) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(alignment)
            .frame(maxWidth: .infinity, alignment: alignment == .center ? .center : .leading)
    }

    private func nearbyHeader(_ state: ServersUiState, spinner: Bool) -> some View {
        HStack {
            Text(Strings.shared.onYourNetwork)
                .font(.headline)
                .foregroundStyle(.secondary)
            Spacer()
            if !state.searching {
                GhostButton(title: Strings.shared.searchAgain) { model.searchAgain() }
            } else if spinner {
                SearchingLabel()
            }
        }
        .frame(height: 40)
        .padding(.top, Space.s2)
    }

    private func discoveredRows(_ state: ServersUiState, compact: Bool) -> some View {
        ForEach(state.discovered, id: \.id) { server in
            Button { onDiscoveredServer(server) } label: {
                Card {
                    HStack {
                        ServerText(name: server.name, address: server.address, meta: nil)
                        if compact {
                            Text(Strings.shared.addToServers)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.primary)
                        } else {
                            Image(systemName: "chevron.right")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
            }
            .buttonStyle(PressableButtonStyle())
        }
    }
}

private struct ServerText: View {
    let name: String
    let address: String
    let meta: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name)
                .font(.headline)
                .foregroundStyle(.primary)
            Text(address)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if let meta, !meta.isEmpty {
                Text(meta)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A saved server: a tap opens its "Who's watching?", the "…" its details and Remove.
private struct SavedRow: View {
    let row: ServerRow
    let onOpen: () -> Void
    let onDetails: () -> Void
    let onRemove: () -> Void

    var body: some View {
        Card {
            HStack(spacing: 0) {
                Button(action: onOpen) {
                    ServerText(name: row.server.name, address: row.server.addresses.first ?? "", meta: row.meta)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressableButtonStyle())
                Menu {
                    Button(action: onDetails) {
                        Label(Strings.shared.serverDetails.titleCased(), systemImage: "info.circle")
                    }
                    Button(role: .destructive, action: onRemove) {
                        Label(Strings.shared.removeServer.titleCased(), systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .foregroundStyle(.secondary)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Strings.shared.moreFor(name: row.server.name))
            }
        }
    }
}
