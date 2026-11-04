import SwiftUI
import Shared

struct AddServerView: View {
    @StateObject private var model: AddServerObservable
    private let focusOnAppear: Bool
    private let showNext: Bool
    private let onSaved: (String) -> Void

    init(initialAddress: String?, showNext: Bool, onSaved: @escaping (String) -> Void) {
        _model = StateObject(wrappedValue: AddServerObservable(initialAddress: initialAddress))
        focusOnAppear = initialAddress == nil
        self.showNext = showNext
        self.onSaved = onSaved
    }

    var body: some View {
        ZStack {
            Color(.systemGroupedBackground).ignoresSafeArea()
            if let state = model.state {
                List { sections(state) }
                    .listStyle(.insetGrouped)
                    .scrollDismissesKeyboard(.interactively)
                    .frame(maxWidth: 720)
            }
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.savedServerId) { _, saved in
            if let saved { onSaved(saved) }
        }
    }

    @ViewBuilder
    private func sections(_ state: AddServerUiState) -> some View {
        let address = Binding<String>(
            get: { model.state?.address ?? "" },
            set: { model.setAddress($0) }
        )
        let idle = state.step is AddServerStepIdle || state.step is AddServerStepChecking
        Section {
            TabHeader(title: Strings.shared.addAServer, horizontalMargin: false) { Color.clear.frame(width: 44, height: 44) }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
        }
        AddressSection(
            label: Strings.shared.serverAddress,
            placeholder: Strings.shared.serverAddressExample,
            address: address,
            help: idle ? Strings.shared.serverAddressHelp : nil,
            error: (state.step as? AddServerStepFailed)?.message,
            focusOnAppear: focusOnAppear,
            onSubmit: { model.connect() },
            header: { EmptyView() }
        )
        if let found = state.step as? AddServerStepFound {
            Section {
                FoundCard(found: found)
            }
            Section {
                PrimaryButton(confirmLabel(found)) { model.confirm() }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            } footer: {
                if showNext && !found.alreadySaved {
                    Text(Strings.shared.addServerNext(name: found.server.name))
                }
            }
        } else {
            Section {
                PrimaryButton(Strings.shared.connect, busyTitle: Strings.shared.connecting, busy: state.step is AddServerStepChecking) {
                    dismissKeyboard()
                    model.connect()
                }
                    .disabled(!state.canConnect && !(state.step is AddServerStepChecking))
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
        }
    }

    private func confirmLabel(_ found: AddServerStepFound) -> String {
        if !found.alreadySaved { return Strings.shared.addServer }
        return found.addressIsNew ? Strings.shared.saveAddress : Strings.shared.done
    }
}

private struct FoundCard: View {
    let found: AddServerStepFound

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s1) {
            HStack(spacing: Space.s2) {
                Image(systemName: "checkmark")
                    .font(.caption.bold())
                Text(Strings.shared.found)
                    .font(.caption.weight(.semibold))
            }
            .foregroundStyle(.primary)
            .padding(.bottom, Space.s1)
            Text(found.server.name)
                .font(.title3.bold())
                .foregroundStyle(.primary)
            Text(Strings.shared.jellyfinVersion(version: found.server.version))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text(found.server.address)
                .font(.subheadline)
                .foregroundStyle(.primary)
            if !found.server.otherAddresses.isEmpty {
                Text(Strings.shared.alsoReachableAt(addresses: found.server.otherAddresses))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if found.alreadySaved {
                Text(Strings.shared.alreadySaved(addressIsNew: found.addressIsNew))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, Space.s2)
            }
        }
        .padding(.vertical, Space.s2)
    }
}
