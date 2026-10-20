import SwiftUI
import Shared

struct AddServerView: View {
    @StateObject private var model: AddServerObservable
    @Environment(\.dismiss) private var dismiss

    init(initialAddress: String?) {
        _model = StateObject(wrappedValue: AddServerObservable(initialAddress: initialAddress))
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            if let state = model.state {
                ScrollView {
                    content(state)
                        .screenMargin()
                        .padding(.top, Space.s2)
                }
                .scrollDismissesKeyboard(.interactively)
            }
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.saved) { saved in
            if saved == true { dismiss() }
        }
    }

    private func content(_ state: AddServerUiState) -> some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            Text(Strings.shared.addAServer.titleCased())
                .screenTitle()
                .padding(.bottom, Space.s2)
            addressField(state)
            // Connect is the main action until a server has been found; then it is Add server.
            if !(state.step is AddServerStepFound) {
                connectButton(state)
            }
            stepContent(state)
        }
    }

    private func addressField(_ state: AddServerUiState) -> some View {
        let failed = state.step is AddServerStepFailed
        let address = Binding<String>(
            get: { model.state?.address ?? "" },
            set: { model.setAddress($0) }
        )
        return FormField(label: Strings.shared.serverAddress, isError: failed) {
            TextField(
                Strings.shared.serverAddress,
                text: address,
                prompt: Text(Strings.shared.serverAddressExample).foregroundColor(.secondary)
            )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .keyboardType(.URL)
            .submitLabel(.go)
            .onSubmit { model.connect() }
        }
    }

    private func connectButton(_ state: AddServerUiState) -> some View {
        let checking = state.step is AddServerStepChecking
        return PrimaryButton(action: { model.connect() }) {
            if checking {
                HStack(spacing: Space.s3) {
                    PrimaryButtonSpinner()
                    Text(Strings.shared.connecting.titleCased())
                }
            } else {
                Text(Strings.shared.connect.titleCased())
            }
        }
        // Disabled only while the address is blank. While checking it stays aqua with its spinner; the
        // view model ignores a second tap.
        .disabled(!state.canConnect && !checking)
    }

    @ViewBuilder
    private func stepContent(_ state: AddServerUiState) -> some View {
        if let failed = state.step as? AddServerStepFailed {
            ErrorCard(message: failed.message)
                .padding(.top, Space.s2)
        } else if let found = state.step as? AddServerStepFound {
            FoundCard(found: found)
            PrimaryButton(confirmLabel(found)) { model.confirm() }
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
        Card {
            VStack(alignment: .leading, spacing: Space.s1) {
                HStack(spacing: Space.s2) {
                    // The server answered: a check and the word, in the text colour. Aqua is for the
                    // primary action.
                    Image(systemName: "checkmark")
                        .font(.caption.bold())
                        .foregroundStyle(.primary)
                    Text(Strings.shared.found)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.primary)
                }
                .padding(.bottom, Space.s1)
                Text(found.server.name)
                    .font(.title2.bold())
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
        }
    }
}

private struct ErrorCard: View {
    let message: String

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                Text(Strings.shared.cantConnectTitle)
                    .font(.headline)
                    .foregroundStyle(Color(.systemRed))
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
            }
        }
    }
}
