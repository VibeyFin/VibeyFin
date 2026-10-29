import SwiftUI
import UIKit
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

/// The profile's letter at the top right of every tab. Offline there is no server to sign in to, so the menu has Settings only.
struct ProfileButton: View {
    @EnvironmentObject private var model: ProfileMenuObservable
    @Environment(\.profileButtonContext) private var context

    var body: some View {
        ProfileMenuButton(
            letter: String((model.state?.userName ?? "").prefix(1)).uppercased(),
            name: model.state?.userName ?? "",
            items: items
        )
        .frame(width: 44, height: 44)
    }

    private var items: [(String, () -> Void)] {
        var items: [(String, () -> Void)] = []
        if !context.offline {
            items.append((Strings.shared.switchProfile.titleCased(), { model.switchProfile() }))
            items.append((Strings.shared.switchServer.titleCased(), { model.switchServer() }))
        }
        items.append((Strings.shared.settings.titleCased(), context.onSettings))
        // A kids profile cannot sign out; the view model ignores it too.
        if !context.offline && model.state?.kidsProfile != true {
            items.append((Strings.shared.signOut.titleCased(), { model.signOut() }))
        }
        return items
    }
}

/// UIKit's button with its menu as the primary action: the menu opens as the finger touches down. SwiftUI's `Menu` with a custom
/// label waits for the finger to lift and then animates in, which felt slow on a button pressed this often.
private struct ProfileMenuButton: UIViewRepresentable {
    let letter: String
    /// Read by VoiceOver: the letter alone would only be read as "A".
    let name: String
    let items: [(String, () -> Void)]

    func makeUIView(context: Context) -> UIButton {
        var configuration = UIButton.Configuration.filled()
        configuration.baseBackgroundColor = .systemGray4
        configuration.baseForegroundColor = .label
        configuration.cornerStyle = .capsule
        configuration.contentInsets = .zero
        let button = UIButton(configuration: configuration)
        button.showsMenuAsPrimaryAction = true
        button.setContentHuggingPriority(.defaultLow, for: .horizontal)
        button.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return button
    }

    func updateUIView(_ button: UIButton, context: Context) {
        var title = AttributedString(letter)
        title.font = UIFont.preferredFont(forTextStyle: .headline)
        button.configuration?.attributedTitle = title
        button.accessibilityLabel = name
        button.menu = UIMenu(children: items.map { item in UIAction(title: item.0) { _ in item.1() } })
    }
}
