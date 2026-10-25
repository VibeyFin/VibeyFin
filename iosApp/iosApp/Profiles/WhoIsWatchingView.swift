import SwiftUI
import Shared

struct WhoIsWatchingView: View {
    @StateObject private var model: WhoIsWatchingObservable

    let onSignedIn: () -> Void

    init(serverId: String, pinFor: ProfileRef? = nil, openAfterPin: Bool = false, onSignedIn: @escaping () -> Void) {
        _model = StateObject(wrappedValue: WhoIsWatchingObservable(serverId: serverId, pinFor: pinFor, openAfterPin: openAfterPin))
        self.onSignedIn = onSignedIn
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            if let state = model.state {
                content(state)
            }
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.signedIn) { signedIn in
            if signedIn == true { onSignedIn() }
        }
        // One sheet: the PIN question replaces a sign-in sheet that is open under it and gives it back once answered.
        .sheet(isPresented: sheetBinding) {
            if let state = model.state, let prompt = state.pinPrompt {
                PinSheet(
                    prompt: prompt,
                    onSubmitPin: { model.submitPin($0) },
                    onForgotPin: { model.forgotPin() },
                    onSubmitPassword: { model.submitPassword($0) },
                    onDismiss: { model.dismissPin() }
                )
            } else if let state = model.state, let sheet = state.sheet {
                SignInSheetView(
                    sheet: sheet,
                    error: state.error,
                    signingIn: state.signingIn,
                    onSubmitPassword: { model.submitPassword($0) },
                    onSubmitOtherUser: { model.submitOtherUser(username: $0, password: $1) },
                    onQuickConnectRetry: { model.quickConnectTapped() },
                    onDismiss: { model.dismissSheet() }
                )
            }
        }
    }

    private var sheetBinding: Binding<Bool> {
        Binding(
            get: { model.state?.pinPrompt != nil || model.state?.sheet != nil },
            set: { shown in
                guard !shown else { return }
                if model.state?.pinPrompt != nil { model.dismissPin() } else { model.dismissSheet() }
            }
        )
    }

    private func content(_ state: WhoIsWatchingUiState) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.s4) {
                VStack(alignment: .leading, spacing: 2) {
                    if let serverName = state.serverName {
                        Text(serverName)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    Text(Strings.shared.whoIsWatching.titleCased()).screenTitle()
                }
                if !state.loading && !state.serverReachable {
                    UnreachableBanner(onRetry: { model.refresh() })
                }
                if state.sheet == nil, let error = state.error {
                    Text(error)
                        .font(.subheadline)
                        .foregroundStyle(Color(.systemRed))
                }
                if state.loading {
                    ProfileSkeletonGrid(avatarSize: avatarSize)
                } else {
                    grid(state)
                }
            }
            .screenMargin()
            .padding(.top, Space.s2)
            .padding(.bottom, Space.s5)
        }
    }

    private func grid(_ state: WhoIsWatchingUiState) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: Space.s4, alignment: .top)], spacing: Space.s5) {
            ForEach(state.profiles, id: \.userId) { profile in
                Button { model.profileTapped(profile) } label: {
                    ProfileTile(profile: profile)
                }
                .buttonStyle(PressableButtonStyle())
                .disabled(state.signingIn)
            }
            if state.quickConnectAvailable {
                Button { model.quickConnectTapped() } label: {
                    ActionTile(label: Strings.shared.quickConnect, symbol: "#")
                }
                .buttonStyle(PressableButtonStyle())
                .disabled(state.signingIn)
            }
            Button { model.otherUserTapped() } label: {
                ActionTile(label: Strings.shared.otherUser, symbol: "+")
            }
            .buttonStyle(PressableButtonStyle())
            .disabled(state.signingIn)
        }
    }
}

private struct UnreachableBanner: View {
    let onRetry: () -> Void

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: Space.s1) {
                Text(Strings.shared.cantReachServerTitle)
                    .font(.subheadline.bold())
                    .foregroundStyle(Color(.systemRed))
                Text(Strings.shared.cantReachServerHelp)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                GhostButton(title: Strings.shared.tryAgain, action: onRetry)
                    .padding(.top, Space.s1)
            }
        }
    }
}

/// The side of a profile's square, and of the "Quick Connect" and "Other user" tiles beside it.
private let avatarSize: CGFloat = 104

private struct ProfileTile: View {
    let profile: Profile

    var body: some View {
        VStack(spacing: Space.s2) {
            ZStack(alignment: .bottomTrailing) {
                Avatar(profile: profile)
                if profile.locked || profile.pinRequiredToOpen {
                    Image(systemName: "lock.fill")
                        .font(.caption.bold())
                        .foregroundStyle(.primary)
                        .frame(width: 26, height: 26)
                        .background(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).fill(Brand.scrim))
                        .padding(6)
                        .accessibilityLabel(profile.locked ? Strings.shared.needsPassword : Strings.shared.needsPin)
                }
            }
            Text(profile.name)
                .font(.headline)
                .foregroundStyle(.primary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
}

/// The profile's picture, or its first letter on a square tinted from the name while there is none
/// or it loads.
private struct Avatar: View {
    let profile: Profile

    var body: some View {
        ZStack {
            Rectangle().fill(tint)
            if let urlString = profile.avatarUrl, let url = URL(string: urlString) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        initial
                    }
                }
            } else {
                initial
            }
        }
        .frame(width: avatarSize, height: avatarSize)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    private var initial: some View {
        Text(String(profile.name.prefix(1)).uppercased())
            .font(.largeTitle.weight(.heavy))
            .foregroundStyle(.primary)
    }

    /// A muted, stable colour per name (the `avatar-1` to `avatar-6` tokens). Swift's own hashing
    /// changes on every launch, so this adds up the characters instead.
    private var tint: Color {
        let tints = Brand.avatars
        let sum = profile.name.lowercased().unicodeScalars.reduce(0) { ($0 &* 31) &+ Int($1.value) }
        return tints[abs(sum) % tints.count]
    }
}

/// A tile that isn't a person: "Other user", "Quick Connect".
private struct ActionTile: View {
    let label: String
    let symbol: String

    var body: some View {
        VStack(spacing: Space.s2) {
            ZStack {
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous).fill(Color(.systemGray4))
                Text(symbol)
                    .font(.largeTitle.weight(.heavy))
                    .foregroundStyle(.secondary)
            }
            .frame(width: avatarSize, height: avatarSize)
            Text(label.titleCased())
                .font(.headline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Asks for a password, or for a username and password. What was typed stays when a sign-in fails.
private struct SignInSheetView: View {
    let sheet: SignInSheet
    let error: String?
    let signingIn: Bool
    let onSubmitPassword: (String) -> Void
    let onSubmitOtherUser: (String, String) -> Void
    let onQuickConnectRetry: () -> Void
    let onDismiss: () -> Void

    @State private var username = ""
    @State private var password = ""

    private var passwordSheet: SignInSheetPassword? { sheet as? SignInSheetPassword }
    private var isOtherUser: Bool { sheet is SignInSheetOtherUser }
    private var canSubmit: Bool {
        !signingIn && (!isOtherUser || !username.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    var body: some View {
        if let quickConnect = sheet as? SignInSheetQuickConnect {
            QuickConnectSheetView(sheet: quickConnect, onRetry: onQuickConnectRetry, onDismiss: onDismiss)
        } else {
            passwordBody
        }
    }

    private var passwordBody: some View {
        ZStack {
            Color(.secondarySystemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: Space.s4) {
                Text(title.titleCased()).sheetTitle()
                if isOtherUser {
                    FormField(label: Strings.shared.username, isError: error != nil) {
                        TextField(Strings.shared.username, text: $username)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .submitLabel(.next)
                    }
                }
                FormField(label: Strings.shared.password, isError: error != nil) {
                    SecureField(Strings.shared.password, text: $password)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.go)
                        .onSubmit { submit() }
                }
                if let error = error {
                    Text(error)
                        .font(.subheadline)
                        .foregroundStyle(Color(.systemRed))
                }
                PrimaryButton(action: { submit() }) {
                    HStack(spacing: Space.s3) {
                        if signingIn { PrimaryButtonSpinner() }
                        Text((signingIn ? Strings.shared.signingIn : Strings.shared.signIn).titleCased())
                    }
                }
                // Stays aqua while signing in; submit() ignores a second tap.
                .disabled(!canSubmit && !signingIn)
                GhostButton(title: Strings.shared.cancel, action: onDismiss)
                    .frame(maxWidth: .infinity)
            }
            .padding(Space.s5)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private var title: String {
        if let passwordSheet = passwordSheet {
            return Strings.shared.signInAs(name: passwordSheet.profile.name)
        }
        return Strings.shared.signInAsAnotherUser
    }

    private func submit() {
        guard canSubmit else { return }
        if passwordSheet != nil {
            onSubmitPassword(password)
        } else {
            onSubmitOtherUser(username, password)
        }
    }
}

/// Shows the code while another device approves the sign-in, or why it stopped.
private struct QuickConnectSheetView: View {
    let sheet: SignInSheetQuickConnect
    let onRetry: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color(.secondarySystemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: Space.s4) {
                Text(Strings.shared.quickConnect.titleCased()).sheetTitle()
                if let message = sheet.message {
                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(Color(.systemRed))
                    SecondaryButton(title: Strings.shared.tryAgain, action: onRetry)
                } else if let code = sheet.code {
                    Text(Strings.shared.quickConnectHelp)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text(code)
                        .font(.largeTitle.weight(.heavy))
                        .monospacedDigit()
                        .tracking(2)
                        .foregroundStyle(.primary)
                    waiting(Strings.shared.quickConnectWaiting)
                } else {
                    waiting(Strings.shared.quickConnectGettingCode)
                }
                GhostButton(title: Strings.shared.cancel, action: onDismiss)
                    .frame(maxWidth: .infinity)
                Spacer()
            }
            .padding(Space.s5)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func waiting(_ text: String) -> some View {
        HStack(spacing: Space.s2) {
            ProgressView()
                .progressViewStyle(.circular)
                .scaleEffect(0.8)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}
