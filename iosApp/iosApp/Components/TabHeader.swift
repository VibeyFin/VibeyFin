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

private struct ProfileButtonFrameKey: EnvironmentKey {
    static let defaultValue: (CGRect) -> Void = { _ in }
}

private struct ProfileButtonHiddenKey: EnvironmentKey {
    static let defaultValue = false
}

private struct ProfileButtonContextKey: EnvironmentKey {
    static let defaultValue = ProfileButtonContext()
}

extension EnvironmentValues {
    /// Told where the profile button sits on screen, for the sign-in zoom to land on.
    var profileButtonFrame: (CGRect) -> Void {
        get { self[ProfileButtonFrameKey.self] }
        set { self[ProfileButtonFrameKey.self] = newValue }
    }

    /// True while the sign-in zoom's picture is on its way to the profile button, so there aren't two.
    var profileButtonHidden: Bool {
        get { self[ProfileButtonHiddenKey.self] }
        set { self[ProfileButtonHiddenKey.self] = newValue }
    }

    var profileButtonContext: ProfileButtonContext {
        get { self[ProfileButtonContextKey.self] }
        set { self[ProfileButtonContextKey.self] = newValue }
    }
}

struct TabHeader<Trailing: View>: View {
    let title: String
    var horizontalMargin = true
    /// A tab root: the title and the profile are in the system bar instead, so there is no row.
    private let tabRoot: Bool
    private let trailing: Trailing

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
        if tabRoot {
            // The title and the profile are in the system navigation bar (`tabRootBar`), so there is no row of ours.
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
    @Environment(\.profileButtonFrame) private var reportFrame
    @Environment(\.profileButtonHidden) private var hidden

    var body: some View {
        HeaderMenuButton(
            letter: String((model.state?.userName ?? "").prefix(1)).uppercased(),
            name: model.state?.userName ?? "",
            items: items,
            background: model.state?.userName.map { Brand.avatars[Int(AvatarTint.shared.of(name: $0))] }
        )
        .frame(width: 44, height: 44)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { reportFrame($0) }
        .opacity(hidden ? 0 : 1)
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

/// The four tab roots keep the system navigation bar and put the profile in its trailing slot, so the system moves it to the side
/// with the other bar buttons when the bars lay out vertically (iPhone Duo) and keeps the content clear of it. At regular width the tab bar
/// names the page; at compact width the page's title is the bar's leading item. `enabled` is false for a screen that is only sometimes a root.
extension View {
    func tabRootBar(title: String, enabled: Bool = true) -> some View { modifier(TabRootBar(title: title, enabled: enabled)) }
}

private struct TabRootBar: ViewModifier {
    let title: String
    let enabled: Bool
    @Environment(\.horizontalSizeClass) private var sizeClass

    /// The avatar is already a circle: on iOS 26 the system's glass capsule behind a bar button would be a second, different shape.
    @ToolbarContentBuilder private var profileItem: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) { ProfileButton() }
            .verticalOnFoldables()
            .withoutGlassBackground()
    }

    @ToolbarContentBuilder private var titleItem: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) { pageTitle }
            .withoutGlassBackground()
    }

    private var pageTitle: some View {
        Text(title.titleCased())
            .font(.title2.bold())
            .foregroundStyle(.primary)
            .lineLimit(1)
            .fixedSize()
            .accessibilityAddTraits(.isHeader)
    }

    func body(content: Content) -> some View {
        if !enabled {
            content.toolbar(.automatic, for: .navigationBar)
        } else if sizeClass == .regular {
            content
                .navigationBarTitleDisplayMode(.inline)
                // The editor role keeps the bar to its compact height; the default one reserved a band above the content.
                .toolbarRole(.editor)
                .toolbarBackground(.hidden, for: .navigationBar)
                .topEdgeEffectHidden()
                .toolbar { profileItem }
        } else {
            content
                .navigationBarTitleDisplayMode(.inline)
                .toolbarBackground(.hidden, for: .navigationBar)
                .topEdgeEffectHidden()
                .toolbar {
                    titleItem
                    profileItem
                }
        }
    }
}

extension ToolbarContent {
    @ToolbarContentBuilder func withoutGlassBackground() -> some ToolbarContent {
        if #available(iOS 26.0, *) {
            sharedBackgroundVisibility(.hidden)
        } else {
            self
        }
    }

    @ToolbarContentBuilder func verticalOnFoldables() -> some ToolbarContent {
        if #available(iOS 27.1, *) {
            axisBehavior(.verticalPreferred)
        } else {
            self
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
