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

/// The header of the four tabs on a phone, or an iPad in a narrow window: the page name at the left and the profile at the right, one line, the same size and in the same
/// place on every tab, so it stays still when the tab changes. `horizontalMargin` is false inside a screen that already has the margin.
struct TabHeader: View {
    let title: String
    var horizontalMargin = true
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        // On an iPad with the tab bar at the top, the page is named by the selected tab and the profile sits in that row (TabShell).
        if UIDevice.current.userInterfaceIdiom == .pad && sizeClass == .regular {
            // The profile is in the navigation bar next to the tab bar (`tabRootBar`), so there is no row of ours.
            EmptyView()
        } else {
            row
        }
    }

    private var row: some View {
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

    func makeCoordinator() -> Coordinator { Coordinator() }

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
        let coordinator = context.coordinator
        // The actions always run the latest closures, but the menu itself is only rebuilt when its rows change: Home refreshes often
        // (the hero moves on, the lists load), and a new menu put in while this one is open blinks it and loses the finger's selection.
        coordinator.actions = items.map { $0.1 }
        let titles = items.map { $0.0 }
        guard titles != coordinator.titles else { return }
        coordinator.titles = titles
        button.menu = UIMenu(children: titles.enumerated().map { index, title in
            UIAction(title: title) { [weak coordinator] _ in coordinator?.actions[index]() }
        })
    }

    final class Coordinator {
        var titles: [String] = []
        var actions: [() -> Void] = []
    }
}

/// An iPad with the tab bar at the top (regular width): the system puts that bar in the navigation bar's own row, so the four tab
/// roots keep the navigation bar, empty, and the profile takes its trailing slot: one row, and the system keeps the content clear of
/// it. (Hiding the bar and laying a row of our own over it left the content under the tab bar after a page had been opened and closed.)
/// Everywhere else the bar is hidden and `TabHeader` draws the row. `enabled` is false for a screen that is only sometimes a root.
extension View {
    func tabRootBar(enabled: Bool = true) -> some View { modifier(TabRootBar(enabled: enabled)) }
}

private struct TabRootBar: ViewModifier {
    let enabled: Bool
    @Environment(\.horizontalSizeClass) private var sizeClass

    /// The avatar is already a circle: on iOS 26 the system's glass capsule behind a bar button would be a second, different shape.
    @ToolbarContentBuilder private var profileItem: some ToolbarContent {
        if #available(iOS 26.0, *) {
            ToolbarItem(placement: .topBarTrailing) { ProfileButton() }
                .sharedBackgroundVisibility(.hidden)
        } else {
            ToolbarItem(placement: .topBarTrailing) { ProfileButton() }
        }
    }

    func body(content: Content) -> some View {
        if !enabled {
            content.toolbar(.automatic, for: .navigationBar)
        } else if UIDevice.current.userInterfaceIdiom == .pad && sizeClass == .regular {
            content
                .navigationBarTitleDisplayMode(.inline)
                // The editor role keeps the bar to its compact height; the default one reserved a band above the content.
                .toolbarRole(.editor)
                .toolbarBackground(.hidden, for: .navigationBar)
                .topEdgeEffectHidden()
                .toolbar { profileItem }
        } else {
            content.toolbar(.hidden, for: .navigationBar)
        }
    }
}

extension View {
    /// No scroll-edge effect behind the top bar. A screen with a scroll view at its top (Home's and Library's chips) got a grey band
    /// there on iOS 26 while Search and Downloads did not; the tab roots are drawn on black with no bar background.
    @ViewBuilder func topEdgeEffectHidden() -> some View {
        if #available(iOS 26.0, *) {
            scrollEdgeEffectHidden(true, for: .top)
        } else {
            self
        }
    }
}
