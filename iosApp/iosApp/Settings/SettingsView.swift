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
    @Environment(\.dismiss) private var dismiss

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
                // Only before the first state arrives: the shared state has every page from the start.
                ZStack {
                    Color(.systemGroupedBackground).ignoresSafeArea()
                    ProgressView().accessibilityLabel(Strings.shared.loadingPage)
                }
            }
        }
        // A page with a search (the language lists) gets the system search field; the shared code filters the rows.
        .modifier(SettingsSearch(pageId: pageId, model: model))
        .onAppear {
            model.start()
            model.pageOpened(pageId)
        }
        // A language was picked from this list: back to the page before.
        .onChange(of: model.state?.closePage) { page in
            guard page == pageId else { return }
            model.pageClosed()
            dismiss()
        }
        // The page the question comes from presents it, so the sheet is never on a view covered by a pushed page: Switch profile from the
        // Profile page (its action is there, and on iPad the sidebar root has none), the rest (change, remove, set, kids mode off) from Kids and PIN. The PIN to open Settings belongs to the entry view.
        .settingsPinSheet(model) { reason in
            switch pageId {
            case .profile: return reason == .switchprofile
            case .kidsandpin: return reason == .changepin || reason == .removepin || reason == .kidsoff || reason == .choosepin
            default: return false
            }
        }
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
                // The sign-out runs on the shared application scope, so leaving can't cancel the token revoke. The way out is
                // taken once the state says Disconnected (see SettingsEntryView).
                model.confirmSignOut()
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
        // Switch profile may first ask for the PIN, so nothing leaves here: SettingsEntryView leaves once the state is Disconnected.
        model.perform(item.key)
    }
}

extension View {
    /// The PIN sheet for the prompts of the reasons `presents` accepts. Each reason is accepted by exactly one view, so two pages of the
    /// stack never bind the same sheet.
    func settingsPinSheet(_ model: SettingsObservable, _ presents: @escaping (PinReason) -> Bool) -> some View {
        sheet(isPresented: Binding(
            get: { model.state?.pinPrompt.map { presents($0.reason) } ?? false },
            set: { if !$0 { model.dismissPin() } }
        )) {
            if let prompt = model.state?.pinPrompt {
                PinSheet(
                    prompt: prompt,
                    onSubmitPin: { model.submitPin($0) },
                    onForgotPin: { model.forgotPin() },
                    onSubmitPassword: { model.submitPassword($0) },
                    onDismiss: { model.dismissPin() }
                )
            }
        }
    }
}

/// `.searchable` for a page whose shared state has a search, bound to it; any other page is left as it is.
private struct SettingsSearch: ViewModifier {
    let pageId: SettingsPageId
    @ObservedObject var model: SettingsObservable

    func body(content: Content) -> some View {
        if let page = model.page(pageId), page.search != nil {
            // Always shown, not tucked under the title until the list is pulled down, so it stays on top while the list scrolls.
            content.searchable(
                text: Binding(get: { model.page(pageId)?.search ?? "" }, set: { model.search(pageId, $0) }),
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: page.searchPrompt ?? ""
            )
        } else {
            content
        }
    }
}
