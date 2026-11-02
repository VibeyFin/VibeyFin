import SwiftUI
import Shared

/// Settings › Seerr: the server's Seerr address, this profile's sign-in, its quota and Disconnect. Its own view, not a shared
/// `SettingsPage`, because it has a text field. What it shows is decided by the shared `SeerrSettingsViewModel`.
struct SeerrSettingsView: View {
    @StateObject private var model = SeerrSettingsObservable()
    @Environment(\.dismiss) private var dismiss
    /// Called instead of going back when Seerr goes away for the profile (kids mode turned on); the iPad detail has nothing to pop.
    var onLeave: (() -> Void)?
    /// Opens the My requests page; nil where there is no stack to push it on (the iPad split view's detail).
    var onMyRequests: (() -> Void)?
    /// The address field; it starts from the server's address and sends every change to the shared state.
    @State private var address = ""

    var body: some View {
        Group {
            if let state = model.state {
                List { sections(state) }
                    .listStyle(.insetGrouped)
            } else {
                ZStack {
                    Color(.systemGroupedBackground).ignoresSafeArea()
                    ProgressView().accessibilityLabel(Strings.shared.loadingPage)
                }
            }
        }
        .navigationTitle(Strings.shared.seerr.titleCased())
        .navigationBarTitleDisplayMode(.inline)
        .toolbarRole(.editor)
        .onAppear {
            model.start()
            prefill()
        }
        .onChange(of: model.state?.layout) { prefill() }
        .onChange(of: model.state?.leave == true) { _, leave in
            guard leave else { return }
            if let onLeave { onLeave() } else { dismiss() }
        }
        .sheet(isPresented: Binding(get: { model.state?.passwordSheet != nil }, set: { if !$0 { model.dismissPassword() } })) {
            if let sheet = model.state?.passwordSheet {
                SeerrPasswordSheetView(sheet: sheet, onSubmit: model.submitPassword, onDismiss: model.dismissPassword)
            }
        }
        .confirmationDialog(
            Strings.shared.seerrDisconnectConfirmTitle,
            isPresented: Binding(get: { model.state?.confirmDisconnect == true }, set: { if !$0 { model.dismissDisconnect() } }),
            titleVisibility: .visible
        ) {
            Button(Strings.shared.seerrDisconnectConfirm.titleCased(), role: .destructive) { model.confirmDisconnect() }
        } message: {
            Text(Strings.shared.seerrDisconnectConfirmMessage)
        }
    }

    @ViewBuilder
    private func sections(_ state: SeerrSettingsUiState) -> some View {
        switch state.layout {
        case .setup:
            setup(state)
        case .signingin:
            serverSection(state)
            Section(Strings.shared.seerrThisProfile) {
                HStack(spacing: Space.s3) {
                    ProgressView()
                    Text(Strings.shared.seerrSigningIn)
                }
            }
        case .notsignedin:
            serverSection(state)
            Section(Strings.shared.seerrThisProfile) {
                SeerrRowText(title: Strings.shared.seerrNotSignedIn, help: state.problem)
                if state.fix == SeerrFix.signinwithpassword {
                    Button(Strings.shared.seerrSignInWithPassword.titleCased()) { model.signInWithPassword() }
                } else if state.fix == SeerrFix.tryagain {
                    Button(Strings.shared.tryAgain.titleCased()) { model.retry() }
                }
                Button(Strings.shared.seerrChangeAddress.titleCased()) { model.changeAddress() }
            }
            disconnectSection
        case .connected:
            serverSection(state)
            connectedSections(state)
            disconnectSection
        default:
            EmptyView()
        }
    }

    /// Signed in: who as, how, the requests left, and the way to My requests.
    @ViewBuilder
    private func connectedSections(_ state: SeerrSettingsUiState) -> some View {
        Section(Strings.shared.seerrThisProfile) {
            SeerrRowText(
                title: Strings.shared.seerrSignedInAs(name: state.userName),
                help: state.signedInWithPassword ? Strings.shared.seerrSignedInWithPassword : Strings.shared.seerrSignedInAutomatically
            )
            if let left = state.quotaLeft {
                LabeledContent {
                    Text(left)
                } label: {
                    SeerrRowText(title: Strings.shared.seerrRequestsLeft, help: nil)
                }
            }
        }
        if let onMyRequests {
            Section {
                Button(action: onMyRequests) {
                    HStack {
                        Text(Strings.shared.seerrMyRequests.titleCased()).foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: "chevron.right").font(.footnote).foregroundStyle(.tertiary)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func setup(_ state: SeerrSettingsUiState) -> some View {
        Section {
            TextField(Strings.shared.seerrAddressPlaceholder, text: $address)
                .keyboardType(.URL)
                .textContentType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .onSubmit { model.connect() }
                .onChange(of: address) { _, new in model.setAddress(new) }
                .disabled(state.connecting)
                .accessibilityLabel(Strings.shared.seerrAddress)
                .accessibilityHint(state.addressError ?? "")
        } header: {
            VStack(alignment: .leading, spacing: Space.s2) {
                Text(Strings.shared.seerrLead)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .textCase(nil)
                    .padding(.bottom, Space.s2)
                Text(Strings.shared.seerrAddress)
            }
        } footer: {
            if let error = state.addressError {
                // The reason sits under the field it is about.
                Label(error, systemImage: "exclamationmark.circle")
                    .foregroundStyle(Color(.systemRed))
            } else {
                Text(state.addressShared ? Strings.shared.seerrAddressSharedHelp : Strings.shared.seerrAddressHelp)
            }
        }
        Section {
            // The page's one primary action. It stays aqua while busy; the ViewModel ignores a second tap.
            PrimaryButton(action: { model.connect() }) {
                HStack(spacing: Space.s3) {
                    if state.connecting { PrimaryButtonSpinner() }
                    Text((state.connecting ? Strings.shared.seerrConnecting : Strings.shared.connect).titleCased())
                }
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
    }

    private func serverSection(_ state: SeerrSettingsUiState) -> some View {
        Section(Strings.shared.seerrServerSection) {
            LabeledContent(Strings.shared.seerrAddressRow, value: state.savedAddress)
            if let version = state.version {
                LabeledContent(Strings.shared.seerrVersion, value: version)
            }
        }
    }

    private var disconnectSection: some View {
        Section {
            Button(Strings.shared.seerrDisconnect.titleCased(), role: .destructive) { model.disconnect() }
        } footer: {
            Text(Strings.shared.seerrDisconnectHelp)
        }
    }

    /// The field starts from the address the page offers (the server's, when it has one) each time the field comes back.
    private func prefill() {
        guard let state = model.state, state.layout == .setup else { return }
        if address != state.address { address = state.address }
    }
}

/// A row's title with a quieter line under it.
private struct SeerrRowText: View {
    let title: String
    let help: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            if let help {
                Text(help)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// Asks for the Jellyfin password once, when Quick Connect is off. The password goes to Seerr and is never kept.
struct SeerrPasswordSheetView: View {
    let sheet: SeerrPasswordSheet
    let onSubmit: (String) -> Void
    let onDismiss: () -> Void
    @State private var password = ""

    var body: some View {
        ZStack {
            Color(.secondarySystemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: Space.s4) {
                Text(Strings.shared.seerrPasswordTitle.titleCased()).sheetTitle()
                Text("\(Strings.shared.seerrPasswordReason) \(Strings.shared.passwordNotKept)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                FormField(label: Strings.shared.seerrUser) {
                    Text(sheet.userName)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                FormField(label: Strings.shared.password, isError: sheet.error != nil) {
                    SecureField(Strings.shared.password, text: $password)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.go)
                        .onSubmit { submit() }
                }
                if let error = sheet.error {
                    Text(error)
                        .font(.subheadline)
                        .foregroundStyle(Color(.systemRed))
                }
                PrimaryButton(action: { submit() }) {
                    HStack(spacing: Space.s3) {
                        if sheet.submitting { PrimaryButtonSpinner() }
                        Text((sheet.submitting ? Strings.shared.signingIn : Strings.shared.signIn).titleCased())
                    }
                }
                GhostButton(title: Strings.shared.cancel, action: onDismiss)
                    .frame(maxWidth: .infinity)
            }
            .padding(Space.s5)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func submit() {
        guard !sheet.submitting else { return }
        onSubmit(password)
    }
}
