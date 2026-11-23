import SwiftUI
import UIKit
import Shared

struct WhoIsWatchingView: View {
    @StateObject private var model: WhoIsWatchingObservable
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tileFrames: [String: CGRect] = [:]
    @State private var book: BookSplit?
    @Binding var flight: ProfileFlight?
    @Binding var buttonFrame: CGRect

    let onSwitchServer: () -> Void
    let onServerDetails: () -> Void
    let onSignedIn: () -> Void
    let onSigningIn: () -> Void

    init(
        serverId: String,
        pinFor: ProfileRef? = nil,
        flight: Binding<ProfileFlight?>,
        buttonFrame: Binding<CGRect>,
        onSwitchServer: @escaping () -> Void,
        onServerDetails: @escaping () -> Void,
        onSigningIn: @escaping () -> Void = {},
        onSignedIn: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: WhoIsWatchingObservable(serverId: serverId, pinFor: pinFor))
        _flight = flight
        _buttonFrame = buttonFrame
        self.onSwitchServer = onSwitchServer
        self.onServerDetails = onServerDetails
        self.onSignedIn = onSignedIn
        self.onSigningIn = onSigningIn
    }

    private var tileSize: CGFloat { sizeClass == .regular ? 140 : 104 }
    private var centred: Bool { sizeClass == .regular }
    private var serverInToolbar: Bool {
        if #available(iOS 27.1, *) { true } else { false }
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            if let state = model.state {
                content(state)
                    .padding(.leading, book?.creaseMaxX ?? 0)
                    .opacity(flight.map { $0.step > 0 } == true ? 0 : 1)
                    .allowsHitTesting(flight == nil)
            }
        }
        .onAppear { model.start() }
        .bookReader($book)
        .modifier(ServerToolbar { if let state = model.state { serverButton(state) } })
        .overlay(alignment: .topTrailing) {
            if centred, !serverInToolbar, let state = model.state { serverButton(state).padding(Space.s2) }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.appResumed() }
        }
        .onChange(of: model.state?.signedIn) { _, signedIn in
            guard signedIn == true else { return }
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

    private func zoomThenLeave() {
        guard !reduceMotion,
              let id = model.state?.openedUserId,
              let profile = model.state?.profiles.first(where: { $0.userId == id }),
              tileFrames[id] != nil else {
            onSigningIn()
            onSignedIn()
            return
        }
        flight = ProfileFlight(profile: profile, from: tileFrames[id] ?? .zero, size: tileSize)
        withAnimation(.spring(duration: 0.45, bounce: 0.4)) { flight?.step = 1 }
        // At the spring's peak, not once it has settled, so the picture doesn't hang before it leaves.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            withAnimation(.timingCurve(0.6, 0, 0.2, 1, duration: 0.45)) { flight?.step = 2 } completion: {
                onSignedIn()
            }
        }
        // Later, so making Home's model doesn't delay the first frames.
        DispatchQueue.main.async { onSigningIn() }
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

    private func serverButton(_ state: WhoIsWatchingUiState) -> some View {
        HeaderMenuButton(
            systemImage: "server.rack",
            name: Strings.shared.serverMenu(name: state.serverName ?? ""),
            items: [
                HeaderMenuItem(title: Strings.shared.switchServer, systemImage: "server.rack", action: onSwitchServer),
                HeaderMenuItem(title: Strings.shared.serverDetails, systemImage: "info.circle", action: onServerDetails),
            ]
        )
        .frame(width: 44, height: 44)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { buttonFrame = $0 }
    }

    @ViewBuilder
    private func header(_ state: WhoIsWatchingUiState) -> some View {
        VStack(alignment: centred ? .center : .leading, spacing: 0) {
            if serverInToolbar || centred {
                Text(Strings.shared.whoIsWatching.titleCased())
                    .font(.title2.bold())
                    .lineLimit(1)
                    .padding(.vertical, Space.s2)
                    .accessibilityAddTraits(.isHeader)
            } else {
                TabHeader(title: Strings.shared.whoIsWatching, horizontalMargin: false) { serverButton(state) }
            }
            if let serverName = state.serverName {
                Text(serverName)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func content(_ state: WhoIsWatchingUiState) -> some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(alignment: centred ? .center : .leading, spacing: Space.s4) {
                    header(state)
                    if state.loading {
                        ProfileSkeletonGrid(avatarSize: tileSize)
                            .frame(maxWidth: centred ? blockWidth(tiles: skeletonTileCount, availableWidth: proxy.size.width - 2 * Space.regularMargin) : .infinity)
                    } else {
                        notes(state)
                        grid(state, availableWidth: proxy.size.width - 2 * Space.regularMargin)
                    }
                }
                .screenMargin()
                .padding(.bottom, Space.s5)
                .frame(maxWidth: .infinity, minHeight: centred ? proxy.size.height : nil, alignment: centred ? .center : .topLeading)
            }
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

    private func grid(_ state: WhoIsWatchingUiState, availableWidth: CGFloat) -> some View {
        let tileCount: Int = state.profiles.count + (state.quickConnectAvailable ? 1 : 0) + 1
        return LazyVGrid(columns: [GridItem(.adaptive(minimum: tileSize + Space.s2), spacing: Space.s4, alignment: .top)], spacing: Space.s5) {
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
        .frame(maxWidth: centred ? blockWidth(tiles: tileCount, availableWidth: availableWidth) : .infinity)
    }

    private var skeletonTileCount: Int { 4 }

    private func blockWidth(tiles: Int, availableWidth: CGFloat) -> CGFloat {
        let columnWidth: CGFloat = tileSize + Space.s2
        let fitting: Int = max(1, Int((availableWidth + Space.s4) / (columnWidth + Space.s4)))
        let shown: Int = min(fitting, tiles)
        return CGFloat(shown) * columnWidth + CGFloat(shown - 1) * Space.s4
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

private struct ProfileTile: View {
    let profile: Profile
    let size: CGFloat
    let opening: Bool
    let onFrame: (CGRect) -> Void

    var body: some View {
        VStack(spacing: Space.s2) {
            ZStack(alignment: .bottomTrailing) {
                Avatar(profile: profile, size: size)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { onFrame($0) }
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

struct Avatar: View {
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

/// On iOS 27.1 the server button is a toolbar item, so the system puts it in the side strip on a foldable and at the top right elsewhere.
private struct ServerToolbar<Button: View>: ViewModifier {
    @ViewBuilder let button: () -> Button

    func body(content: Content) -> some View {
        if #available(iOS 27.1, *) {
            content
                .navigationBarTitleDisplayMode(.inline)
                .navigationBarBackButtonHidden(true)
                .toolbar(.visible, for: .navigationBar)
                .toolbarBackground(.hidden, for: .navigationBar)
                .topEdgeEffectHidden()
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) { button() }
                        .verticalOnFoldables()
                        .withoutGlassBackground()
                }
        } else {
            content.toolbar(.hidden, for: .navigationBar)
        }
    }
}
