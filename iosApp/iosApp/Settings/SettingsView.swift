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
    /// The page open as a picker sheet over this one (a language list), if any.
    @State private var sheetPage: SettingsPageId?

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
        .onAppear {
            model.start()
            model.pageOpened(pageId)
        }
        // A language was picked from the sheet: it closes.
        .onChange(of: model.state?.closePage) { page in
            guard let page, page == sheetPage else { return }
            model.pageClosed()
            sheetPage = nil
        }
        .sheet(isPresented: Binding(get: { sheetPage != nil }, set: { if !$0 { sheetPage = nil } })) {
            if let id = sheetPage, let sheet = model.page(id) {
                SettingsPickerSheet(page: sheet, model: model)
            }
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
        .purgeDownloadsDialog(model, isHeld: holdsPurgeDownloads)
    }

    @ViewBuilder
    private func rows(of page: SettingsPage) -> some View {
        // A section has no id of its own: page and position are stable for a page that never reorders.
        ForEach(page.sections.enumerated().map { (id: "\(page.id.name)-\($0.offset)", section: $0.element) }, id: \.id) { entry in
            sectionView(entry.section, in: page)
        }
    }

    private var holdsPurgeDownloads: Bool {
        guard let page = model.page(pageId) else { return false }
        return page.sections.contains { section in
            section.items.contains { $0.key == SettingKey.purgedownloads }
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
                    SettingsItemRow(item: item, page: page, model: model, selection: selection, onAction: perform) { id in
                        // The list starts whole each time it opens.
                        model.pageOpened(id)
                        sheetPage = id
                    }
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
    /// "Delete downloaded files" asks first. Like Sign out's confirmation it belongs to the page that holds the row.
    func purgeDownloadsDialog(_ model: SettingsObservable, isHeld: Bool) -> some View {
        confirmationDialog(
            Strings.shared.purgeDownloadsConfirmTitle,
            isPresented: Binding(
                get: { isHeld && model.state?.confirmPurgeDownloads == true },
                set: { if !$0 { model.dismissPurgeDownloads() } }
            ),
            titleVisibility: .visible
        ) {
            Button(Strings.shared.deleteFiles.titleCased(), role: .destructive) {
                model.confirmPurgeDownloads()
            }
        } message: {
            Text(Strings.shared.purgeDownloadsConfirmMessage)
        }
    }

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

/// A page marked as a sheet (a language list), drawn as the picker sheet the filters use: its search field on top, one row per option.
/// Picking saves it; the shared model then asks for the sheet to close.
private struct SettingsPickerSheet: View {
    let page: SettingsPage
    @ObservedObject var model: SettingsObservable

    var body: some View {
        let items = page.visibleOnly().sections.flatMap { $0.items }
        let choice = items.compactMap { $0 as? ChoiceItem }.first
        let rows = (choice?.options ?? []).map { option in
            PickerRow(id: option.code ?? "", label: option.label, selected: option.value == choice?.value)
        }
        PickerSheetView(
            title: page.title,
            rows: rows,
            loading: false,
            message: nil,
            // Nothing matches what was typed: the page's own row says so.
            emptyText: rows.isEmpty ? items.compactMap { $0 as? InfoItem }.first?.title : nil,
            onPick: { id in
                if let choice { model.chooseLanguage(choice.key, id) }
            },
            onRetry: {},
            search: page.search.map { _ in Binding(get: { model.page(page.id)?.search ?? "" }, set: { model.search(page.id, $0) }) },
            searchHint: page.searchPrompt ?? ""
        )
    }
}
