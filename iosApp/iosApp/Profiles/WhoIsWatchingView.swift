import SwiftUI
import UIKit
import Shared

struct WhoIsWatchingView: View {
    @StateObject private var model: WhoIsWatchingObservable
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Where each profile's picture sits, for the zoom to start from.
    @State private var tileFrames: [String: CGRect] = [:]
    @State private var zoomingInto: Profile?
    /// 0 before, 1 grown in the middle, 2 in the header's button.
    @State private var zoomStep = 0
    /// The header's round button, where Home's profile button will be: the header row is the same on every page.
    @State private var buttonFrame: CGRect = .zero

    let onSwitchServer: () -> Void
    let onServerDetails: () -> Void
    let onSignedIn: () -> Void
    /// Signed in, before the zoom: Home can start loading under it.
    let onSigningIn: () -> Void

    init(
        serverId: String,
        pinFor: ProfileRef? = nil,
        onSwitchServer: @escaping () -> Void,
        onServerDetails: @escaping () -> Void,
        onSigningIn: @escaping () -> Void = {},
        onSignedIn: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: WhoIsWatchingObservable(serverId: serverId, pinFor: pinFor))
        self.onSwitchServer = onSwitchServer
        self.onServerDetails = onServerDetails
        self.onSignedIn = onSignedIn
        self.onSigningIn = onSigningIn
    }

    /// The side of a profile's square, and of the Quick Connect and Other user tiles beside it.
    private var tileSize: CGFloat { sizeClass == .regular ? 140 : 104 }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            if let state = model.state {
                content(state)
                    .opacity(zoomStep > 0 ? 0 : 1)
                    .allowsHitTesting(zoomingInto == nil)
            }
            zoomedAvatar
        }
        .coordinateSpace(name: zoomSpace)
        .onAppear { model.start() }
        .toolbar(.hidden, for: .navigationBar)
        // Back from approving the code in Safari on this phone: the approval is picked up at once.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.appResumed() }
        }
        .onChange(of: model.state?.signedIn) { _, signedIn in
            guard signedIn == true else { return }
            onSigningIn()
            zoomThenLeave()
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
            } else if let state = model.state, let quickConnect = state.sheet as? SignInSheetQuickConnect {
                QuickConnectSheetView(
                    sheet: quickConnect,
                    onQuickConnect: { model.quickConnectTapped() },
                    onUsePassword: { model.otherUserTapped() },
                    onDismiss: { model.dismissSheet() }
                )
            } else if let state = model.state, let sheet = state.sheet {
                SignInSheetView(
                    sheet: sheet,
                    state: state,
                    onSubmitPassword: { model.submitPassword($0) },
                    onSubmitOtherUser: { model.submitOtherUser(username: $0, password: $1) },
                    onQuickConnect: { model.quickConnectTapped() },
                    onDismiss: { model.dismissSheet() }
                )
            }
        }
    }

    /// The profile that opened grows into the middle as the rest fades, moves up into the header's button, then the tabs fade in
    /// with the same picture in that place. Straight on with Reduce Motion, or
    /// after Other user and Quick Connect, which have no tile.
    private func zoomThenLeave() {
        guard !reduceMotion,
              let id = model.state?.openedUserId,
              let profile = model.state?.profiles.first(where: { $0.userId == id }),
              tileFrames[id] != nil else {
            onSignedIn()
            return
        }
        zoomingInto = profile
        withAnimation(.easeInOut(duration: 0.52)) { zoomStep = 1 } completion: {
            guard buttonFrame != .zero else { return onSignedIn() }
            // Sets off gently and speeds up into the corner (M3's emphasized accelerate), as if pulled away.
            withAnimation(.timingCurve(0.3, 0, 0.8, 0.15, duration: 0.42)) { zoomStep = 2 } completion: { onSignedIn() }
        }
    }

    @ViewBuilder
    private var zoomedAvatar: some View {
        if let profile = zoomingInto, let frame = tileFrames[profile.userId] {
            GeometryReader { proxy in
                let middle = CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)
                // Grows in the middle, then shrinks into the button and rounds into its circle.
                Avatar(profile: profile, size: tileSize)
                    .clipShape(RoundedRectangle(cornerRadius: zoomStep == 2 ? tileSize / 2 : Radius.card, style: .continuous))
                    .scaleEffect(zoomStep == 2 ? buttonFrame.width / tileSize : zoomStep == 1 ? 1.8 : 1)
                    .position(
                        zoomStep == 2 ? CGPoint(x: buttonFrame.midX, y: buttonFrame.midY) : zoomStep == 1 ? middle : CGPoint(x: frame.midX, y: frame.midY)
                    )
            }
            .allowsHitTesting(false)
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
                VStack(alignment: .leading, spacing: 0) {
                    TabHeader(title: Strings.shared.whoIsWatching, horizontalMargin: false) {
                        HeaderMenuButton(
                            systemImage: "server.rack",
                            name: Strings.shared.serverMenu(name: state.serverName ?? ""),
                            items: [
                                HeaderMenuItem(title: Strings.shared.switchServer, systemImage: "server.rack", action: onSwitchServer),
                                HeaderMenuItem(title: Strings.shared.serverDetails, systemImage: "info.circle", action: onServerDetails),
                            ]
                        )
                        .frame(width: 44, height: 44)
                        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(zoomSpace)) } action: { buttonFrame = $0 }
                    }
                    if let serverName = state.serverName {
                        Text(serverName)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                if state.loading {
                    ProfileSkeletonGrid(avatarSize: tileSize)
                } else {
                    notes(state)
                    grid(state)
                }
            }
            .screenMargin()
            .padding(.bottom, Space.s5)
        }
    }

    @ViewBuilder
    private func notes(_ state: WhoIsWatchingUiState) -> some View {
        if let notice = state.notice {
            Note(text: notice)
        }
        if !state.serverReachable {
            UnreachableCard(state: state, onRetry: { model.refresh() })
        }
        if state.sheet == nil, let error = state.error {
            Text(error)
                .font(.subheadline)
                .foregroundStyle(Color(.systemRed))
        }
    }

    private func grid(_ state: WhoIsWatchingUiState) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: tileSize + Space.s2), spacing: Space.s4, alignment: .top)], spacing: Space.s5) {
            ForEach(state.profiles, id: \.userId) { profile in
                let opening = state.openingUserId == profile.userId
                Button { model.profileTapped(profile) } label: {
                    ProfileTile(profile: profile, size: tileSize, opening: opening) { tileFrames[profile.userId] = $0 }
                }
                .buttonStyle(PressableButtonStyle())
                .disabled(state.busy)
                .opacity(state.busy && !opening ? 0.38 : 1)
                .accessibilityLabel(
                    Strings.shared.profileTile(name: profile.name, needsPassword: profile.locked, needsPin: profile.pinRequiredToOpen, opening: opening)
                )
            }
            if state.quickConnectAvailable {
                actionTile(Strings.shared.quickConnect, systemImage: "laptopcomputer.and.iphone", busy: state.busy) { model.quickConnectTapped() }
                    .accessibilityLabel(Strings.shared.quickConnectTile)
            }
            actionTile(Strings.shared.otherUser, systemImage: "person.badge.plus", busy: state.busy) { model.otherUserTapped() }
        }
    }

    private func actionTile(_ label: String, systemImage: String, busy: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: Space.s2) {
                Image(systemName: systemImage)
                    .font(.system(size: tileSize * 0.32, weight: .light))
                    .foregroundStyle(.secondary)
                    .frame(width: tileSize, height: tileSize)
                    .background(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).fill(Color(.systemGray5)))
                Text(label.titleCased())
                    .font(.headline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(PressableButtonStyle())
        .disabled(busy)
        .opacity(busy ? 0.38 : 1)
    }
}

/// A note, not an error, such as whose session ended.
private struct Note: View {
    let text: String

    var body: some View {
        Label(text, systemImage: "info.circle")
            .font(.subheadline)
            .foregroundStyle(.secondary)
    }
}

private struct UnreachableCard: View {
    let state: WhoIsWatchingUiState
    let onRetry: () -> Void

    private var help: String {
        let tried = state.triedAddresses.isEmpty ? nil : Strings.shared.triedAddresses(addresses: state.triedAddresses)
        return [tried, Strings.shared.cantReachServerHelp].compactMap { $0 }.joined(separator: " ")
    }

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: Space.s1) {
                Text(Strings.shared.cantReachServer(name: state.serverName ?? ""))
                    .font(.subheadline.bold())
                    .foregroundStyle(Color(.systemRed))
                Text(help)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                GhostButton(title: Strings.shared.tryAgain, action: onRetry)
                    .padding(.top, Space.s1)
            }
        }
        .accessibilityElement(children: .contain)
    }
}

private let zoomSpace = "whoIsWatching"

private struct ProfileTile: View {
    let profile: Profile
    let size: CGFloat
    let opening: Bool
    let onFrame: (CGRect) -> Void

    var body: some View {
        VStack(spacing: Space.s2) {
            ZStack(alignment: .bottomTrailing) {
                Avatar(profile: profile, size: size)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(zoomSpace)) } action: { onFrame($0) }
                if profile.locked || profile.pinRequiredToOpen {
                    Image(systemName: "lock.fill")
                        .font(.caption.bold())
                        .foregroundStyle(.primary)
                        .frame(width: 26, height: 26)
                        .background(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).fill(Brand.scrim))
                        .padding(6)
                }
                if opening {
                    ZStack {
                        RoundedRectangle(cornerRadius: Radius.card, style: .continuous).fill(Brand.scrim)
                        ProgressView().controlSize(.large).tint(Color.primary)
                    }
                    .frame(width: size, height: size)
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

/// The profile's picture, or its first letter on a square tinted from the name while there is none or it loads.
private struct Avatar: View {
    let profile: Profile
    let size: CGFloat

    private var small: Bool { size < 64 }

    var body: some View {
        ZStack {
            Rectangle().fill(tint)
            initial
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: small ? Radius.artwork : Radius.card, style: .continuous))
        .accessibilityHidden(true)
    }

    private var initial: some View {
        Text(String(profile.name.prefix(1)).uppercased())
            .font(small ? .title3.bold() : .largeTitle.weight(.heavy))
            .foregroundStyle(.primary)
    }

    /// A muted, stable colour per name (the `avatar-1` to `avatar-6` tokens), picked in shared code so Android agrees.
    private var tint: Color { Brand.avatars[Int(AvatarTint.shared.of(name: profile.name))] }
}

/// Asks for a password, or for a username and password. What was typed stays when a sign-in fails.
private struct SignInSheetView: View {
    let sheet: SignInSheet
    let state: WhoIsWatchingUiState
    let onSubmitPassword: (String) -> Void
    let onSubmitOtherUser: (String, String) -> Void
    let onQuickConnect: () -> Void
    let onDismiss: () -> Void

    @State private var username = ""
    @State private var password = ""

    private var profile: Profile? { (sheet as? SignInSheetPassword)?.profile }
    private var canSubmit: Bool {
        !state.signingIn && (profile != nil || !username.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    var body: some View {
        SheetScaffold(
            title: profile.map { Strings.shared.signInAs(name: $0.name) } ?? Strings.shared.signInAsAnotherUser,
            footnote: Strings.shared.passwordNotKept,
            onClose: onDismiss,
            leading: {
                if let profile { Avatar(profile: profile, size: 44) }
            },
            content: {
                if let notice = state.sheetNotice {
                    Note(text: notice)
                }
                FieldGroup(isError: state.error != nil) {
                    if profile == nil {
                        UsernameField(text: $username)
                        Divider().padding(.leading, Space.s4)
                    }
                    PasswordField(text: $password, onSubmit: submit)
                }
                .opacity(state.signingIn ? 0.6 : 1)
                if let error = state.error {
                    FieldError(message: error)
                }
                PrimaryButton(Strings.shared.signIn, busyTitle: Strings.shared.signingIn, busy: state.signingIn) { submit() }
                    .disabled(!canSubmit && !state.signingIn)
                if state.quickConnectAvailable {
                    GhostButton(title: Strings.shared.useQuickConnectInstead, action: onQuickConnect)
                        .frame(maxWidth: .infinity)
                }
            }
        )
    }

    private func submit() {
        guard canSubmit else { return }
        if profile != nil {
            onSubmitPassword(password)
        } else {
            onSubmitOtherUser(username, password)
        }
    }
}

/// Sign in with a code approved on another device: the steps, the code and the time left, or why it stopped.
private struct QuickConnectSheetView: View {
    let sheet: SignInSheetQuickConnect
    let onQuickConnect: () -> Void
    let onUsePassword: () -> Void
    let onDismiss: () -> Void

    @State private var copied: String?

    private var expired: Bool { sheet.stop == QuickConnectStop.expired }

    var body: some View {
        SheetScaffold(title: Strings.shared.quickConnect, detents: [.large], onClose: sheet.approved ? nil : onDismiss) {
            if sheet.approved {
                approved
            } else if sheet.stop == QuickConnectStop.off {
                Text(sheet.message ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                PrimaryButton(Strings.shared.signInWithPassword, action: onUsePassword)
            } else if sheet.stop != nil {
                if expired {
                    lead
                    codePanel
                }
                FieldError(message: sheet.message ?? "")
                PrimaryButton(expired ? Strings.shared.quickConnectNewCode : Strings.shared.tryAgain, action: onQuickConnect)
                passwordButton
            } else {
                lead
                steps
                codePanel
                Waiting(text: sheet.status ?? Strings.shared.quickConnectGettingCode)
                passwordButton
            }
        }
        .toast(copied) { copied = nil }
    }

    private var lead: some View {
        Text(Strings.shared.quickConnectLead)
            .font(.subheadline)
            .foregroundStyle(.secondary)
    }

    private var passwordButton: some View {
        GhostButton(title: Strings.shared.signInWithPassword, action: onUsePassword)
            .frame(maxWidth: .infinity)
    }

    private var steps: some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            ForEach(Array(Strings.shared.quickConnectSteps.enumerated()), id: \.offset) { index, step in
                HStack(alignment: .firstTextBaseline, spacing: Space.s3) {
                    Text("\(index + 1)")
                        .font(.caption.bold())
                        .frame(width: 22, height: 22)
                        .background(Circle().fill(Color(.tertiarySystemFill)))
                    Text(step)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                }
            }
        }
    }

    private var codePanel: some View {
        HStack(spacing: 0) {
            if let shown = sheet.shownCode {
                Text(shown)
                    .font(.system(size: 40, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .tracking(4)
                    .strikethrough(expired)
                    .foregroundStyle(expired ? HierarchicalShapeStyle.tertiary : HierarchicalShapeStyle.primary)
                    .frame(maxWidth: .infinity)
                    .padding(.leading, expired ? 0 : 44)
                    .accessibilityLabel(sheet.spokenCode ?? "")
                if !expired {
                    Button {
                        UIPasteboard.general.string = sheet.code
                        copied = Strings.shared.codeCopied.titleCased()
                    } label: {
                        Image(systemName: "doc.on.doc")
                            .foregroundStyle(.secondary)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(Strings.shared.copyCode)
                }
            } else {
                HStack(spacing: Space.s2) {
                    ForEach(0..<6, id: \.self) { index in
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(Color(.tertiarySystemFill))
                            .frame(width: 24, height: 36)
                            .padding(.leading, index == 3 ? Space.s2 : 0)
                    }
                }
                .frame(maxWidth: .infinity)
                .accessibilityHidden(true)
            }
        }
        .frame(height: 80)
        .padding(.horizontal, Space.s2)
        .background(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).fill(Color(.tertiarySystemBackground)))
    }

    private var approved: some View {
        VStack(spacing: Space.s2) {
            Image(systemName: "checkmark")
                .font(.title2.bold())
                .frame(width: 48, height: 48)
                .background(Circle().fill(Color(.tertiarySystemFill)))
            Text(Strings.shared.quickConnectApproved.titleCased())
                .font(.title3.bold())
                .accessibilityAddTraits(.isHeader)
            Waiting(text: Strings.shared.quickConnectSigningIn)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Space.s4)
    }
}

private struct Waiting: View {
    let text: String

    var body: some View {
        HStack(spacing: Space.s2) {
            ProgressView().controlSize(.small)
            Text(text)
                .font(.subheadline)
                .monospacedDigit()
                .foregroundStyle(.secondary)
        }
    }
}
