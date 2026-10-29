import SwiftUI
import Shared

/// Lets SwiftUI watch the profile button's state and send it events. `ProfileMenuModel` calls back on the main thread.
final class ProfileMenuObservable: ObservableObject {
    @Published private(set) var state: ProfileMenuUiState?

    private let model = ProfileMenuModel()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func switchProfile() { model.onSwitchProfile() }

    func switchServer() { model.onSwitchServer() }

    func signOut() { model.onSignOut() }

    func submitPin(_ pin: String) { model.onSubmitPin(pin: pin) }

    func forgotPin() { model.onForgotPin() }

    func submitPassword(_ password: String) { model.onSubmitPassword(password: password) }

    func dismissPin() { model.onDismissPin() }

    deinit {
        model.close()
    }
}

/// What the profile button needs from the shell: whether there is a server, and how to open Settings in the tab on screen.
struct ProfileButtonContext {
    var offline = false
    var onSettings: () -> Void = {}
}

private struct ProfileButtonContextKey: EnvironmentKey {
    static let defaultValue = ProfileButtonContext()
}

extension EnvironmentValues {
    var profileButtonContext: ProfileButtonContext {
        get { self[ProfileButtonContextKey.self] }
        set { self[ProfileButtonContextKey.self] = newValue }
    }
}

/// The header of the four tabs: the page name at the left and the profile at the right, one line, the same size and in the same
/// place on every tab, so it stays still when the tab changes. `horizontalMargin` is false inside a screen that already has the margin.
struct TabHeader: View {
    let title: String
    var horizontalMargin = true

    var body: some View {
        HStack(alignment: .center, spacing: Space.s2) {
            Text(title.titleCased())
                .font(.title2.bold())
                .foregroundStyle(.primary)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            ProfileButton()
        }
        .padding(.vertical, Space.s2)
        .modifier(OptionalScreenMargin(enabled: horizontalMargin))
    }
}

private struct OptionalScreenMargin: ViewModifier {
    let enabled: Bool

    func body(content: Content) -> some View {
        if enabled { content.screenMargin() } else { content }
    }
}

/// The profile's letter at the top right of every tab. A tap opens its options; a double tap goes straight to "Who's watching?", after
/// the PIN when the profile has one. VoiceOver's own double tap activates, so it gets Switch profile as an action instead. Offline
/// there is no server to sign in to: the options are Settings only and a double tap does nothing.
struct ProfileButton: View {
    @EnvironmentObject private var model: ProfileMenuObservable
    @Environment(\.profileButtonContext) private var context
    @State private var open = false

    var body: some View {
        ZStack {
            Circle().fill(Color(.systemGray4))
            Text(String((model.state?.userName ?? "").prefix(1)).uppercased())
                .font(.headline)
                .foregroundStyle(.primary)
        }
        .frame(width: 44, height: 44)
        .contentShape(Circle())
        // The double tap is checked first, so a single tap waits a moment to be sure it is one.
        .onTapGesture(count: 2) { if !context.offline { model.switchProfile() } }
        .onTapGesture { open = true }
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        // Named for the person; the letter on it would only be read as "A".
        .accessibilityLabel(model.state?.userName ?? "")
        .accessibilityHint(Strings.shared.showOptions)
        .accessibilityAction { open = true }
        .accessibilityActions {
            if !context.offline {
                Button(Strings.shared.switchProfile.titleCased()) { model.switchProfile() }
            }
        }
        // The system's own list of options: a sheet from the bottom on iPhone, a popover from the button on iPad.
        .confirmationDialog(model.state?.userName ?? "", isPresented: $open, titleVisibility: .hidden) {
            if !context.offline {
                Button(Strings.shared.switchProfile.titleCased()) { model.switchProfile() }
                Button(Strings.shared.switchServer.titleCased()) { model.switchServer() }
            }
            Button(Strings.shared.settings.titleCased()) { context.onSettings() }
            // A kids profile cannot sign out; the view model ignores it too.
            if !context.offline && model.state?.kidsProfile != true {
                Button(Strings.shared.signOut.titleCased(), role: .destructive) { model.signOut() }
            }
            Button(Strings.shared.cancel.titleCased(), role: .cancel) {}
        }
    }
}
