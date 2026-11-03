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

    func quickConnect() { model.onQuickConnect() }

    func quickConnectOpened() { model.onQuickConnectOpened() }

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

/// The header of the four tabs on a phone, or an iPad in a narrow window, and of the sign-in pages: the page name at the left and
/// `trailing` at the right (the profile on a tab, the server on "Who's watching?"), one line, the same size and in the same place on
/// every page, so it stays still when the page changes. `horizontalMargin` is false inside a screen that already has the margin.
struct TabHeader<Trailing: View>: View {
    let title: String
    var horizontalMargin = true
    /// A tab root: on an iPad with the tab bar at the top the profile is in the system bar instead, so there is no row.
    private let tabRoot: Bool
    private let trailing: Trailing
    @Environment(\.horizontalSizeClass) private var sizeClass

    init(title: String, horizontalMargin: Bool = true, @ViewBuilder trailing: () -> Trailing) {
        self.init(title: title, horizontalMargin: horizontalMargin, tabRoot: false, trailing: trailing())
    }

    fileprivate init(title: String, horizontalMargin: Bool, tabRoot: Bool, trailing: Trailing) {
        self.title = title
        self.horizontalMargin = horizontalMargin
        self.tabRoot = tabRoot
        self.trailing = trailing
    }

    var body: some View {
        // On an iPad with the tab bar at the top, the page is named by the selected tab and the profile sits in that row (TabShell).
        if tabRoot && UIDevice.current.userInterfaceIdiom == .pad && sizeClass == .regular {
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
            trailing.frame(minWidth: 44, minHeight: 44)
        }
        .padding(.vertical, Space.s2)
        .modifier(OptionalScreenMargin(enabled: horizontalMargin))
    }
}

extension TabHeader where Trailing == ProfileButton {
    init(title: String, horizontalMargin: Bool = true) {
        self.init(title: title, horizontalMargin: horizontalMargin, tabRoot: true, trailing: ProfileButton())
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
        HeaderMenuButton(
            letter: String((model.state?.userName ?? "").prefix(1)).uppercased(),
            name: model.state?.userName ?? "",
            items: items,
            // The profile's own tint, as on its tile in "Who's watching?".
            background: model.state?.userName.map { Brand.avatars[Int(AvatarTint.shared.of(name: $0))] }
        )
        .frame(width: 44, height: 44)
        // The Jellyfin picture over the letter, which shows while it loads or when there is none. Taps go through to the button.
        .overlay {
            if let urlString = model.state?.avatarUrl, let url = URL(string: urlString) {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase { image.resizable().scaledToFill() }
                }
                .frame(width: 44, height: 44)
                .clipShape(Circle())
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
        }
    }

    private var items: [HeaderMenuItem] {
        var items: [HeaderMenuItem] = []
        if !context.offline {
            items.append(HeaderMenuItem(title: Strings.shared.switchProfile) { model.switchProfile() })
            items.append(HeaderMenuItem(title: Strings.shared.switchServer) { model.switchServer() })
        }
        if !context.offline && model.state?.kidsProfile != true {
            items.append(HeaderMenuItem(title: Strings.shared.quickConnect) { model.quickConnect() })
        }
        items.append(HeaderMenuItem(title: Strings.shared.settings, action: context.onSettings))
        // A kids profile cannot sign out; the view model ignores it too.
        if !context.offline && model.state?.kidsProfile != true {
            items.append(HeaderMenuItem(title: Strings.shared.signOut) { model.signOut() })
        }
        return items
    }
}

/// One row of a `HeaderMenuButton`'s menu.
struct HeaderMenuItem {
    let title: String
    var systemImage: String?
    let action: () -> Void
}

/// The round button at the right of the header, with its menu as the primary action: the profile's letter, or a symbol such as
/// the server's. UIKit's button, because the menu opens as the finger touches down. SwiftUI's `Menu` with a custom label waits for
/// the finger to lift and then animates in, which felt slow on a button pressed this often.
struct HeaderMenuButton: UIViewRepresentable {
    var letter = ""
    var systemImage: String?
    /// Read by VoiceOver: the letter alone would only be read as "A".
    let name: String
    let items: [HeaderMenuItem]
    /// The fill; the system grey unless given, as the profile's own tint is.
    var background: Color?

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
        button.configuration?.baseBackgroundColor = background.map(UIColor.init) ?? .systemGray4
        if let systemImage {
            button.configuration?.image = UIImage(systemName: systemImage, withConfiguration: UIImage.SymbolConfiguration(textStyle: .body))
        } else {
            var title = AttributedString(letter)
            title.font = UIFont.preferredFont(forTextStyle: .headline)
            button.configuration?.attributedTitle = title
        }
        button.accessibilityLabel = name
        let coordinator = context.coordinator
        // The actions always run the latest closures, but the menu itself is only rebuilt when its rows change: Home refreshes often
        // (the hero moves on, the lists load), and a new menu put in while this one is open blinks it and loses the finger's selection.
        coordinator.actions = items.map { $0.action }
        let titles = items.map { $0.title }
        guard titles != coordinator.titles else { return }
        coordinator.titles = titles
        button.menu = UIMenu(children: items.enumerated().map { index, item in
            UIAction(title: item.title.titleCased(), image: item.systemImage.flatMap { UIImage(systemName: $0) }) { [weak coordinator] _ in
                coordinator?.actions[index]()
            }
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
