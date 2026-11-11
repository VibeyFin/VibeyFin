import SwiftUI
import Shared

/// Settings as the route opens it: two panes on a regular width (iPad), the phone's push flow otherwise (iPhone, Split View).
struct SettingsEntryView: View {
    @ObservedObject var model: SettingsObservable
    let onExit: (HomeExit) -> Void
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.dismiss) private var dismiss
    /// Leaving is done once, however many times the state reports Disconnected.
    @State private var exited = false

    var body: some View {
        Group {
            // Until the profile's PIN is right (or it needs none) no page is built: nothing of Settings is drawn, reachable or opened.
            // A spinner meanwhile, so the wait never reads as an empty page.
            if model.state?.locked ?? true {
                ZStack {
                    Color(.systemGroupedBackground).ignoresSafeArea()
                    ProgressView().accessibilityLabel(Strings.shared.loadingPage)
                }
            } else if sizeClass == .regular {
                SettingsSplitView(model: model, onExit: onExit)
            } else {
                SettingsPageView(pageId: SettingsPageId.root, model: model, onExit: onExit)
            }
        }
        // On a regular width the split view has its own bars and its own way back (the chevron in its sidebar), so the stack's bar is hidden
        // from the start: it would be an empty header with a second back button.
        .toolbar(sizeClass == .regular ? .hidden : .automatic, for: .navigationBar)
        .onAppear { model.start() }
        // The session ended (Switch profile once its PIN was right, a confirmed Sign out): "Who's watching?" on the server we were on, the
        // same way back as Home's. One observer here, not on every page of the stack, and only for Disconnected: an expired token takes
        // the global route to "Who's watching?" instead.
        .onChange(of: model.state?.disconnected ?? false) { _, disconnected in
            guard disconnected, !exited else { return }
            exited = true
            if model.lastServerId.isEmpty {
                onExit(HomeExitServers.shared)
            } else {
                onExit(HomeExitWhoIsWatching(serverId: model.lastServerId))
            }
        }
        // The PIN to open Settings was dismissed: back to where Settings was opened from.
        .onChange(of: model.state?.leave) { _, leave in
            if leave == true { dismiss() }
        }
        // Only the PIN to open Settings: while it is asked the root is the page on top. The other questions come from pages that are pushed
        // over this view, so each of those presents its own (see `settingsPinSheet`).
        .settingsPinSheet(model) { $0 == .opensettings }
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
                        Group {
                            if link.target == .seerr {
                                SeerrSettingsView(onLeave: { storedRow = "" })
                            } else if link.target == .quickconnect {
                                ApproveQuickConnectView()
                            } else {
                                SettingsPageView(pageId: link.target, model: model, onExit: onExit)
                            }
                        }
                            .navigationDestination(for: TabRoute.self) { route in
                                if case .settingsPage(let id) = route {
                                    if id == .quickconnect {
                                        ApproveQuickConnectView()
                                    } else {
                                        SettingsPageView(pageId: id, model: model, onExit: onExit)
                                    }
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
