import SwiftUI
import Shared

/// A saved server: its addresses, best first, its version and profiles on this device, and Remove server.
struct ServerDetailsView: View {
    @StateObject private var model: ServerDetailsObservable
    let onAddAddress: () -> Void
    let onRemoved: () -> Void

    init(serverId: String, onAddAddress: @escaping () -> Void, onRemoved: @escaping () -> Void) {
        _model = StateObject(wrappedValue: ServerDetailsObservable(serverId: serverId))
        self.onAddAddress = onAddAddress
        self.onRemoved = onRemoved
    }

    var body: some View {
        Group {
            if let state = model.state, !state.loading {
                List { sections(state) }
                    .listStyle(.insetGrouped)
            } else {
                Color(.systemGroupedBackground).ignoresSafeArea()
            }
        }
        .navigationTitle(model.state?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarRole(.editor)
        .onAppear { model.start() }
        .onChange(of: model.state?.removed == true) { _, removed in
            if removed { onRemoved() }
        }
        .confirmationDialog(
            model.state?.removeTitle ?? "",
            isPresented: Binding(get: { model.state?.confirmRemove == true }, set: { if !$0 { model.dismissRemove() } }),
            titleVisibility: .visible
        ) {
            Button(Strings.shared.removeServer.titleCased(), role: .destructive) { model.confirmRemove() }
        } message: {
            Text(model.state?.removeMessage ?? "")
        }
    }

    @ViewBuilder
    private func sections(_ state: ServerDetailsUiState) -> some View {
        Section {
            ForEach(state.addresses, id: \.address) { row in
                HStack(spacing: Space.s2) {
                    Text(row.address)
                    if row.lastWorked {
                        Text(Strings.shared.lastWorked)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, Space.s2)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color(.tertiarySystemFill)))
                    }
                }
                .swipeActions(edge: .trailing) {
                    if state.canRemoveAddress {
                        Button(Strings.shared.remove.titleCased(), role: .destructive) { model.removeAddress(row.address) }
                    }
                }
            }
            Button(action: onAddAddress) {
                Label(Strings.shared.addAddress.titleCased(), systemImage: "plus")
            }
        } header: {
            Text(Strings.shared.addresses)
        } footer: {
            Text(state.canRemoveAddress ? "\(Strings.shared.addressesHelp) \(Strings.shared.addressesSwipeHelp)" : Strings.shared.addressesHelp)
        }
        Section(Strings.shared.serverSection) {
            if let version = state.version {
                LabeledContent(Strings.shared.version, value: Strings.shared.jellyfinVersion(version: version))
            }
            LabeledContent(Strings.shared.profilesOnDevice, value: "\(state.profileCount)")
        }
        Section {
            Button(Strings.shared.removeServer.titleCased(), role: .destructive) { model.removeServer() }
        } footer: {
            Text(state.removeHelp)
        }
    }
}
