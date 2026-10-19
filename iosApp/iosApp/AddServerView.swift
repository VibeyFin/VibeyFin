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
            Palette.ink.ignoresSafeArea()
            if let state = model.state {
                ScrollView {
                    content(state)
                        .padding(.horizontal, 24)
                        .padding(.top, 8)
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
        VStack(alignment: .leading, spacing: 16) {
            Text(Strings.shared.addAServer)
                .vibeTitle(size: 34)
                .padding(.bottom, 8)
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
        return VStack(alignment: .leading, spacing: 6) {
            Text(Strings.shared.serverAddress)
                .font(.caption)
                .foregroundColor(failed ? Palette.error : Palette.textMuted)
            TextField(
                Strings.shared.serverAddress,
                text: address,
                prompt: Text(Strings.shared.serverAddressExample).foregroundColor(Palette.textMuted)
            )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .keyboardType(.URL)
            .submitLabel(.go)
            .onSubmit { model.connect() }
            .foregroundColor(Palette.text)
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(failed ? Palette.error : Palette.surfaceHigh, lineWidth: 1)
            )
        }
    }

    private func connectButton(_ state: AddServerUiState) -> some View {
        let checking = state.step is AddServerStepChecking
        return Button { model.connect() } label: {
            if checking {
                HStack(spacing: 12) {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(Palette.textMuted)
                        .scaleEffect(0.8)
                    Text(Strings.shared.connecting)
                }
            } else {
                Text(Strings.shared.connect)
            }
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(!state.canConnect)
    }

    @ViewBuilder
    private func stepContent(_ state: AddServerUiState) -> some View {
        if let failed = state.step as? AddServerStepFailed {
            ErrorCard(message: failed.message)
                .padding(.top, 8)
        } else if let found = state.step as? AddServerStepFound {
            FoundCard(found: found)
            Button(confirmLabel(found)) { model.confirm() }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(state.saving)
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
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    // Teal means available: the server answered.
                    Circle().fill(Palette.available).frame(width: 8, height: 8)
                    Text(Strings.shared.found)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Palette.available)
                }
                .padding(.bottom, 4)
                Text(found.server.name)
                    .font(.system(size: 24, weight: .heavy))
                    .tracking(-0.5)
                    .foregroundColor(Palette.text)
                Text(Strings.shared.jellyfinVersion(version: found.server.version))
                    .font(.subheadline)
                    .foregroundColor(Palette.textMuted)
                Text(found.server.address)
                    .font(.subheadline)
                    .foregroundColor(Palette.text)
                if !found.server.otherAddresses.isEmpty {
                    Text(Strings.shared.alsoReachableAt(addresses: found.server.otherAddresses))
                        .font(.footnote)
                        .foregroundColor(Palette.textMuted)
                }
                if found.alreadySaved {
                    Text(Strings.shared.alreadySaved(addressIsNew: found.addressIsNew))
                        .font(.footnote)
                        .foregroundColor(Palette.textMuted)
                        .padding(.top, 8)
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
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(Palette.error)
                Text(message)
                    .font(.subheadline)
                    .foregroundColor(Palette.text)
            }
        }
    }
}
