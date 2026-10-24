import SwiftUI
import Shared

/// One page of the Settings tree, drawn from the shared `SettingsPage`: an inset grouped list, one `Section` per group. The root and every page
/// a link opens use this same view; what a page holds is decided in `shared/`.
struct SettingsPageView: View {
    let pageId: SettingsPageId
    @ObservedObject var model: SettingsObservable
    /// Called when the session ends from here (Switch profile, a confirmed Sign out) and the app should show "Who's watching?".
    let onExit: (HomeExit) -> Void
    /// Set in the iPad split view: a link row selects its page here instead of pushing it, and the list shows the selection.
    var selection: Binding<SettingKey?>?

    var body: some View {
        let page = model.page(pageId)
        Group {
            if let page {
                // Only the sidebar's list selects rows. A selection binding on a detail page makes every row selectable, so a tap
                // highlights it instead of reaching its toggle or picker.
                if let selection {
                    List(selection: selection) { rows(of: page) }
                        .listStyle(.insetGrouped)
                } else {
                    List { rows(of: page) }
                        .listStyle(.insetGrouped)
                }
            } else {
                Color(.systemGroupedBackground).ignoresSafeArea()
            }
        }
        .onAppear { model.start() }
        .navigationTitle((page?.title ?? "").titleCased())
        .navigationBarTitleDisplayMode(.inline)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        // The confirmation belongs to the page that holds Sign out, so pages above it on the stack don't bind the same state.
        .confirmationDialog(
            Strings.shared.signOutConfirmTitle,
            isPresented: Binding(
                get: { holdsSignOut && model.state?.confirmSignOut == true },
                set: { if !$0 { model.dismissSignOut() } }
            ),
            titleVisibility: .visible
        ) {
            Button(Strings.shared.signOut.titleCased(), role: .destructive) {
                // The sign-out runs on the shared application scope, so leaving at once can't cancel the token revoke.
                model.confirmSignOut()
                leave()
            }
        } message: {
            Text(Strings.shared.signOutConfirmMessage)
        }
    }

    @ViewBuilder
    private func rows(of page: SettingsPage) -> some View {
        // A section has no id of its own: page and position are stable for a page that never reorders.
        ForEach(page.sections.enumerated().map { (id: "\(page.id.name)-\($0.offset)", section: $0.element) }, id: \.id) { entry in
            sectionView(entry.section, in: page)
        }
    }

    private var holdsSignOut: Bool {
        guard let page = model.page(pageId) else { return false }
        return page.sections.contains { section in
            section.items.contains { $0.key == SettingKey.signout }
        }
    }

    @ViewBuilder
    private func sectionView(_ section: SettingsSection, in page: SettingsPage) -> some View {
        let items = model.items(of: section)
        if !items.isEmpty {
            Section {
                // Keyed by page and setting, so a setting that ever sits on two pages stays distinct.
                ForEach(items, id: \.key) { item in
                    SettingsItemRow(item: item, page: page, model: model, selection: selection, onAction: perform)
                }
            } header: {
                if let title = section.title { Text(title.titleCased()) }
            } footer: {
                if let footer = section.footer { Text(footer) }
            }
            .textCase(nil)
        }
    }

    private func perform(_ item: ActionItem) {
        model.perform(item.key)
        // Switch profile ends the session on the spot; Sign out waits for its confirmation.
        if item.key == SettingKey.switchprofile { leave() }
    }

    /// The state is Disconnected from here on, so the way back is the same as Home's: "Who's watching?" on the server we were on.
    private func leave() {
        if model.lastServerId.isEmpty {
            onExit(HomeExitServers.shared)
        } else {
            onExit(HomeExitWhoIsWatching(serverId: model.lastServerId))
        }
    }
}
