import SwiftUI
import Shared

struct WhoIsWatchingView: View {
    @StateObject private var model: WhoIsWatchingObservable

    let onSignedIn: () -> Void

    init(serverId: String, onSignedIn: @escaping () -> Void) {
        _model = StateObject(wrappedValue: WhoIsWatchingObservable(serverId: serverId))
        self.onSignedIn = onSignedIn
    }

    var body: some View {
        ZStack {
            Palette.ink.ignoresSafeArea()
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
        .sheet(isPresented: sheetBinding) {
            if let state = model.state, let sheet = state.sheet {
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
            get: { model.state?.sheet != nil },
            set: { shown in
                if !shown { model.dismissSheet() }
            }
        )
    }

    private func content(_ state: WhoIsWatchingUiState) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    if let serverName = state.serverName {
                        Text(serverName)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(Palette.textMuted)
                    }
                    Text(Strings.shared.whoIsWatching).vibeTitle(size: 34)
                }
                if !state.loading && !state.serverReachable {
                    UnreachableBanner(onRetry: { model.refresh() })
                }
                if state.sheet == nil, let error = state.error {
                    Text(error)
                        .font(.subheadline)
                        .foregroundColor(Palette.error)
                }
                if state.loading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(Palette.textMuted)
                        .frame(maxWidth: .infinity, minHeight: 160)
                } else {
                    grid(state)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
    }

    private func grid(_ state: WhoIsWatchingUiState) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: 16, alignment: .top)], spacing: 24) {
            ForEach(state.profiles, id: \.userId) { profile in
                Button { model.profileTapped(profile) } label: {
                    ProfileTile(profile: profile)
                }
                .buttonStyle(.plain)
                .disabled(state.signingIn)
            }
            if state.quickConnectAvailable {
                Button { model.quickConnectTapped() } label: {
                    ActionTile(label: Strings.shared.quickConnect, symbol: "#")
                }
                .buttonStyle(.plain)
                .disabled(state.signingIn)
            }
            Button { model.otherUserTapped() } label: {
                ActionTile(label: Strings.shared.otherUser, symbol: "+")
            }
            .buttonStyle(.plain)
            .disabled(state.signingIn)
        }
    }
}

private struct UnreachableBanner: View {
    let onRetry: () -> Void

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 4) {
                Text(Strings.shared.cantReachServerTitle)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Palette.error)
                Text(Strings.shared.cantReachServerHelp)
                    .font(.subheadline)
                    .foregroundColor(Palette.text)
                Button(Strings.shared.tryAgain, action: onRetry)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Palette.text)
                    .padding(.top, 4)
            }
        }
    }
}

private struct ProfileTile: View {
    let profile: Profile

    var body: some View {
        VStack(spacing: 8) {
            ZStack(alignment: .bottomTrailing) {
                Avatar(profile: profile)
                if profile.locked {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Palette.text)
                        .frame(width: 26, height: 26)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Palette.ink.opacity(0.85)))
                        .padding(6)
                        .accessibilityLabel(Strings.shared.needsPassword)
                }
            }
            Text(profile.name)
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(Palette.text)
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
        .frame(width: 104, height: 104)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var initial: some View {
        Text(String(profile.name.prefix(1)).uppercased())
            .font(.system(size: 36, weight: .heavy))
            .foregroundColor(Palette.text)
    }

    /// A muted, stable colour per name. Swift's own hashing changes on every launch, so this adds
    /// up the characters instead.
    private var tint: Color {
        let tints: [UInt32] = [0x3A4A6B, 0x4B3F6B, 0x3F6B5E, 0x6B4B3F, 0x6B3F55, 0x55633F]
        let sum = profile.name.lowercased().unicodeScalars.reduce(0) { ($0 &* 31) &+ Int($1.value) }
        return Color(hex: tints[abs(sum) % tints.count])
    }
}

/// A tile that isn't a person: "Other user", "Quick Connect".
private struct ActionTile: View {
    let label: String
    let symbol: String

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 16).fill(Palette.surfaceHigh)
                Text(symbol)
                    .font(.system(size: 36, weight: .heavy))
                    .foregroundColor(Palette.textMuted)
            }
            .frame(width: 104, height: 104)
            Text(label)
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(Palette.textMuted)
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
            Palette.surface.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                Text(title).vibeTitle(size: 24)
                if isOtherUser {
                    field(Strings.shared.username) {
                        TextField(Strings.shared.username, text: $username)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .submitLabel(.next)
                    }
                }
                field(Strings.shared.password) {
                    SecureField(Strings.shared.password, text: $password)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.go)
                        .onSubmit { submit() }
                }
                if let error = error {
                    Text(error)
                        .font(.subheadline)
                        .foregroundColor(Palette.error)
                }
                Button { submit() } label: {
                    Text(signingIn ? Strings.shared.signingIn : Strings.shared.signIn)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(!canSubmit)
                Button(Strings.shared.cancel, action: onDismiss)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Palette.textMuted)
                    .frame(maxWidth: .infinity)
            }
            .padding(24)
        }
        .presentationDetents([.medium, .large])
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

    private func field<Content: View>(_ label: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption)
                .foregroundColor(error != nil ? Palette.error : Palette.textMuted)
            content()
                .foregroundColor(Palette.text)
                .padding(16)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(error != nil ? Palette.error : Palette.surfaceHigh, lineWidth: 1)
                )
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
            Palette.surface.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                Text(Strings.shared.quickConnect).vibeTitle(size: 24)
                if let message = sheet.message {
                    Text(message)
                        .font(.subheadline)
                        .foregroundColor(Palette.error)
                    Button(Strings.shared.tryAgain, action: onRetry)
                        .buttonStyle(OutlineButtonStyle())
                } else if let code = sheet.code {
                    Text(Strings.shared.quickConnectHelp)
                        .font(.subheadline)
                        .foregroundColor(Palette.textMuted)
                    Text(code)
                        .font(.system(size: 48, weight: .heavy, design: .rounded))
                        .tracking(2)
                        .foregroundColor(Palette.text)
                    waiting(Strings.shared.quickConnectWaiting)
                } else {
                    waiting(Strings.shared.quickConnectGettingCode)
                }
                Button(Strings.shared.cancel, action: onDismiss)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Palette.textMuted)
                    .frame(maxWidth: .infinity)
                Spacer()
            }
            .padding(24)
        }
        .presentationDetents([.medium, .large])
    }

    private func waiting(_ text: String) -> some View {
        HStack(spacing: 8) {
            ProgressView()
                .progressViewStyle(.circular)
                .tint(Palette.textMuted)
                .scaleEffect(0.8)
            Text(text)
                .font(.subheadline)
                .foregroundColor(Palette.textMuted)
        }
    }
}
