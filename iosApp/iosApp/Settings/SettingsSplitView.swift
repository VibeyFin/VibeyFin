import SwiftUI
import Shared

/// Settings as the route opens it: two panes on a regular width (iPad), the phone's push flow otherwise (iPhone, Split View).
struct SettingsEntryView: View {
    @ObservedObject var model: SettingsObservable
    let onExit: (HomeExit) -> Void
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        if sizeClass == .regular {
            SettingsSplitView(model: model, onExit: onExit)
        } else {
            SettingsPageView(pageId: SettingsPageId.root, model: model, onExit: onExit)
        }
    }
}

/// The root as an inset grouped sidebar, the chosen page in the detail column with its own title and no back button. The detail is capped
/// at a readable width and centred. The selected row is kept per scene (reopening Settings or rotating restores it, like iPadOS Settings).
struct SettingsSplitView: View {
    @ObservedObject var model: SettingsObservable
    let onExit: (HomeExit) -> Void
    @Environment(\.dismiss) private var dismiss

    /// The name of the selected row's `SettingKey`; empty until one is chosen.
    @SceneStorage("settings.selectedRow") private var storedRow = ""

    private static let detailWidth: CGFloat = 640

    var body: some View {
        NavigationSplitView {
            SettingsPageView(pageId: SettingsPageId.root, model: model, onExit: onExit, selection: selection)
                .tint(Brand.accent)
                // Settings was pushed over Home, whose bar is hidden, so the sidebar carries the way back.
                .toolbar {
                    ToolbarItem(placement: .navigation) {
                        Button { dismiss() } label: { Image(systemName: "chevron.left") }
                            .accessibilityLabel(Strings.shared.back)
                            .accessibilityIdentifier("settings.back")
                    }
                }
        } detail: {
            ZStack {
                Color(.systemGroupedBackground).ignoresSafeArea()
                if let link = selectedLink {
                    // A link inside a page pushes over the detail.
                    NavigationStack {
                        SettingsPageView(pageId: link.target, model: model, onExit: onExit)
                            .navigationDestination(for: TabRoute.self) { route in
                                if case .settingsPage(let id) = route {
                                    SettingsPageView(pageId: id, model: model, onExit: onExit)
                                }
                            }
                    }
                    .id(link.key)
                    .frame(maxWidth: Self.detailWidth)
                }
            }
        }
        .navigationSplitViewStyle(.balanced)
        .onAppear { model.start() }
    }

    /// The stored row while the sidebar still draws it, else the row for the first settable page.
    private var effectiveKey: SettingKey? {
        let links = model.rootLinks()
        if let stored = links.first(where: { $0.key.name == storedRow }) { return stored.key }
        return model.defaultLinkKey()
    }

    private var selectedLink: LinkItem? {
        guard let key = effectiveKey else { return nil }
        return model.rootLinks().first { $0.key == key }
    }

    private var selection: Binding<SettingKey?> {
        Binding(get: { effectiveKey }, set: { if let key = $0 { storedRow = key.name } })
    }
}
