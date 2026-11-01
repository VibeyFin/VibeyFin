import SwiftUI
import Shared

struct HomeView: View {
    @StateObject private var model = HomeObservable()
    @StateObject private var seerr = SeerrHomeObservable()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.playerSession) private var player
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Called when the screen should be left: the user chose to, or the session ended.
    let onExit: (HomeExit) -> Void
    let onOpen: (MediaItem) -> Void
    /// Opens an item's page, from a Continue watching card's menu.
    let onDetails: (String) -> Void
    /// A category button or a genre in the Categories list was chosen: its page opens over Home.
    let onOpenList: (HomeFilter) -> Void
    /// A Seerr poster: its Seerr page, or the library's when the library has it.
    var onOpenSeerr: (SeerrTitle) -> Void = { _ in }
    /// A Seerr discover row's See all.
    var onSeerrSeeAll: (SeerrListKind) -> Void = { _ in }
    /// My requests' See all.
    var onMyRequests: () -> Void = {}

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                // The same header as the other tabs: the page name and the profile, no logo.
                TabHeader(title: Strings.shared.home)
                // No side margin on the scroll content: the Continue watching row runs to the screen
                // edge, so it and the rows under it apply the margin themselves.
                if let state = model.state, state.serverId != nil {
                    // The category buttons stay put under the header while the page scrolls, as the Library's do. Home itself is
                    // never filtered: a button opens a page of its own.
                    CategoryButtonRow(buttons: state.buttons) { model.category($0.kind) }
                        .padding(.bottom, Space.s3)
                    ScrollView {
                        VStack(alignment: .leading, spacing: Space.s5) {
                            // While it is being picked a grey card of the same size stands in, so the hero does not just appear.
                            if state.heroes.isEmpty && state.heroLoading {
                                HomeHeroSkeleton(dots: state.heroSlots >= 2).screenMargin().transition(.opacity)
                            }
                            // The featured items, when there are any: full width, under the buttons and above what is being watched.
                            if !state.heroes.isEmpty {
                                HomeHeroCarousel(
                                    items: state.heroes,
                                    onPlay: { model.heroPlay($0) },
                                    onOpen: onDetails,
                                    onMyList: { model.heroMyList($0) }
                                )
                                .screenMargin()
                            }
                            ContinueRowView(
                                row: state.continueRow,
                                onPlay: { player.play($0) },
                                onDetails: onDetails,
                                onMarkWatched: { model.markWatched($0) },
                                onRetry: { model.retryContinue() },
                                downloads: state.downloads,
                                canDownload: state.canDownload,
                                onDownload: { model.download($0) },
                                onDownloadAction: { model.act($0, $1) }
                            )
                            // The profile's own Seerr news sits with resume, above discovery.
                            if let requests = seerr.state?.requests {
                                SeerrRowView(row: requests, onOpen: onOpenSeerr, onSeeAll: onMyRequests).transition(.opacity)
                            }
                            // Until the libraries are known two grey rows hold the place, so the rows that replace
                            // them don't push the genre rows down from under the user's eyes.
                            if state.loadingLibraries && state.rows.isEmpty {
                                RowPlaceholder()
                                RowPlaceholder()
                            }
                            // Recently added in each library, then a few genres: each loads and fails on its own, and an
                            // empty one is not here.
                            // Seerr's discover rows go after the library's rows and before the genres: outside the ForEach, so they
                            // keep their own identity (and scroll position) when the genre rows change.
                            let split = min(max(Int(state.firstGenreRow), 0), state.rows.count)
                            ForEach(Array(state.rows.prefix(split)), id: \.id) { row in
                                mediaRow(row)
                            }
                            seerrDiscover
                            ForEach(Array(state.rows.dropFirst(split)), id: \.id) { row in
                                mediaRow(row)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.bottom, Space.s5)
                        // Rows that arrive, leave or change height slide and fade into place instead of snapping.
                        .animation(reduceMotion ? nil : .easeOut(duration: 0.25), value: state.rows.map { $0.id })
                        .animation(reduceMotion ? nil : .easeOut(duration: 0.25), value: state.continueRow.isVisible)
                        // The grey stand-ins for the hero and the rows leave the same way, so when nothing replaces one the
                        // page closes up smoothly instead of jumping.
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: state.heroLoading)
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: state.heroes.map { $0.id })
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: state.continueRow.loading)
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: state.loadingLibraries)
                    }
                    .refreshable {
                        seerr.refresh()
                        await model.refresh()
                    }
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
        .onAppear {
            model.start()
            seerr.start()
        }
        // The question before a Continue watching card's download is deleted.
        .downloadPrompts(
            prompt: nil,
            removal: model.state?.removePrompt,
            onToggleUnwatched: {},
            onConfirm: {},
            onDismiss: {},
            onConfirmRemove: { model.confirmRemove() },
            onDismissRemoval: { model.dismissRemoval() }
        )
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                model.foreground()
                seerr.foreground()
            }
        }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
        // The hero's Play found what to play (an episode for a show): the player opens on it, or the page when none was found.
        .onChange(of: model.state?.heroNav != nil) { has in
            guard has, let nav = model.state?.heroNav else { return }
            if let play = nav as? HeroNavPlay {
                player.play(play.itemId)
            } else if let details = nav as? HeroNavDetails {
                onDetails(details.itemId)
            }
            model.heroNavHandled()
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
                            detail: option.count.map { String($0.int32Value) },
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

    private func mediaRow(_ row: MediaRow) -> some View {
        MediaRowView(
            row: row,
            onOpen: onOpen,
            onSeeAll: { seeAll in
                if let library = seeAll as? SeeAllLibrary {
                    onOpen(library.library)
                } else if let genre = seeAll as? SeeAllGenre {
                    onOpenList(genre.filter)
                } else if seeAll is SeeAllMyList {
                    onOpenList(HomeFilterMyList.shared)
                }
            },
            onRetry: { model.retryRow(row.id) }
        )
        .transition(.opacity)
    }

    private var seerrDiscover: some View {
        ForEach(seerr.state?.discover ?? [], id: \.title) { row in
            SeerrRowView(row: row, onOpen: onOpenSeerr, onSeeAll: { if let kind = row.kind { onSeerrSeeAll(kind) } })
                .transition(.opacity)
        }
    }
}
