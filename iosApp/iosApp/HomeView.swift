import SwiftUI
import Shared

/// Plays one item from Home, full screen. A Continue watching card plays at once.
private struct PlayRequest: Identifiable {
    let id: String
}

struct HomeView: View {
    @StateObject private var model = HomeObservable()
    @Environment(\.scenePhase) private var scenePhase
    @State private var playing: PlayRequest?

    /// Called when the screen should be left: the user chose to, or the session ended.
    let onExit: (HomeExit) -> Void
    let onOpen: (MediaItem) -> Void
    /// Opens an item's page, from a Continue watching card's menu.
    let onDetails: (String) -> Void
    let onSettings: () -> Void
    /// A category button or a genre in the Categories list was chosen: its page opens over Home.
    let onOpenList: (HomeFilter) -> Void

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                header
                    .screenMargin()
                    .padding(.top, Space.s5)
                // No side margin on the scroll content: the Continue watching row runs to the screen
                // edge, so it and the libraries apply the margin themselves.
                if let state = model.state, state.serverId != nil {
                    ScrollView {
                        VStack(alignment: .leading, spacing: Space.s5) {
                            // The category buttons scroll with the content and run to the screen edge. Home itself is
                            // never filtered: a button opens a page of its own.
                            CategoryButtonRow(buttons: state.buttons) { model.category($0.kind) }
                            ContinueRowView(
                                row: state.continueRow,
                                onPlay: { playing = PlayRequest(id: $0) },
                                onDetails: onDetails,
                                onMarkWatched: { model.markWatched($0) },
                                onRetry: { model.retryContinue() }
                            )
                            // Recently added in each library, then a few genres: each loads and fails on its own, and an
                            // empty one is not here.
                            ForEach(state.rows, id: \.id) { row in
                                MediaRowView(
                                    row: row,
                                    onOpen: onOpen,
                                    onSeeAll: { seeAll in
                                        if let library = seeAll as? SeeAllLibrary {
                                            onOpen(library.library)
                                        } else if let genre = seeAll as? SeeAllGenre {
                                            onOpenList(genre.filter)
                                        }
                                    },
                                    onRetry: { model.retryRow(row.id) }
                                )
                            }
                            // The libraries close Home, as 16:9 tiles in one row.
                            MyMediaView(state: state, onOpen: onOpen, onRetry: { model.retryLibraries() })
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, Space.s5)
                    }
                    .refreshable { await model.refresh() }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .failureBanner(
            model.state?.actionMessage,
            onRetry: { model.retryAction() },
            onDismiss: { model.dismissMessage() }
        )
        .onAppear { model.start() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { model.foreground() }
        }
        .fullScreenCover(item: $playing) { request in
            PlayerView(itemId: request.id, onExit: onExit)
        }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
        .onChange(of: model.state?.openList != nil) { opened in
            if opened, let filter = model.state?.openList {
                onOpenList(filter)
                model.listOpened()
            }
        }
        .sheet(isPresented: Binding(
            get: { model.state?.sheet != nil },
            set: { if !$0 { model.dismissSheet() } }
        )) {
            if let sheet = model.state?.sheet {
                PickerSheetView(
                    title: sheet.title,
                    rows: sheet.options.map { option in
                        PickerRow(
                            id: option.id,
                            label: option.name,
                            detail: option.count.map { String(truncating: $0) },
                            description: option.count.map { Strings.shared.titleCount(name: option.name, count: $0.int32Value) }
                        )
                    },
                    loading: sheet.loading,
                    message: sheet.message,
                    emptyText: sheet.isEmpty ? sheet.emptyText : nil,
                    onPick: { model.pickOption($0) },
                    onRetry: { model.retrySheet() }
                )
            }
        }
    }

    private var header: some View {
        // The app's logo with the name of the page next to it, as in Netflix; the profile is at the right.
        HStack(alignment: .center, spacing: Space.s3) {
            AppLogo()
            Text(Strings.shared.home.titleCased())
                .screenTitle()
                .accessibilityAddTraits(.isHeader)
            Spacer()
            profileMenu
        }
    }

    private var profileMenu: some View {
        Menu {
            Button(Strings.shared.switchProfile.titleCased()) { model.switchProfile() }
            Button(Strings.shared.switchServer.titleCased()) { model.switchServer() }
            Button(Strings.shared.settings.titleCased()) { onSettings() }
            Button(Strings.shared.signOut.titleCased()) { model.signOut() }
        } label: {
            ZStack {
                Circle().fill(Color(.systemGray4))
                Text(String((model.state?.userName ?? "").prefix(1)).uppercased())
                    .font(.headline)
                    .foregroundStyle(.primary)
            }
            .frame(width: 44, height: 44)
            .accessibilityLabel(model.state?.userName ?? "")
        }
    }
}
