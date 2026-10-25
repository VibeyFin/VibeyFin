import SwiftUI
import Shared

/// Plays one item from Home, full screen. A Continue watching card plays at once.
private struct PlayRequest: Identifiable {
    let id: String
}

struct HomeView: View {
    @StateObject private var model = HomeObservable()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var playing: PlayRequest?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
                // The logo, the page name and the profile are centred in the header, with the same space above and below.
                header
                    .screenMargin()
                    // On iPad the tab pill above already gives the space, so only the bottom gets padding.
                    .padding(.top, sizeClass == .regular ? 0 : Space.s3)
                    .padding(.bottom, Space.s3)
                // No side margin on the scroll content: the Continue watching row runs to the screen
                // edge, so it and the libraries apply the margin themselves.
                if let state = model.state, state.serverId != nil {
                    ScrollView {
                        VStack(alignment: .leading, spacing: Space.s5) {
                            // The category buttons scroll with the content and run to the screen edge. Home itself is
                            // never filtered: a button opens a page of its own.
                            CategoryButtonRow(buttons: state.buttons) { model.category($0.kind) }
                            // While it is being picked a grey card of the same size stands in, so the hero does not just appear.
                            if state.hero == nil && state.heroLoading {
                                HomeHeroSkeleton().screenMargin().transition(.opacity)
                            }
                            // The featured item, when there is one: full width, under the buttons and above what is being watched.
                            if let hero = state.hero {
                                HomeHeroView(
                                    item: hero,
                                    onPlay: { model.heroPlay() },
                                    onOpen: onDetails,
                                    onMyList: { model.heroMyList() }
                                )
                                .screenMargin()
                            }
                            ContinueRowView(
                                row: state.continueRow,
                                onPlay: { playing = PlayRequest(id: $0) },
                                onDetails: onDetails,
                                onMarkWatched: { model.markWatched($0) },
                                onRetry: { model.retryContinue() }
                            )
                            // Until the libraries are known two grey rows hold the place, so the rows that replace
                            // them don't push My media down from under the user's eyes.
                            if state.loadingLibraries && state.rows.isEmpty {
                                RowPlaceholder()
                                RowPlaceholder()
                            }
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
                                        } else if seeAll is SeeAllMyList {
                                            onOpenList(HomeFilterMyList.shared)
                                        }
                                    },
                                    onRetry: { model.retryRow(row.id) }
                                )
                                .transition(.opacity)
                            }
                            // The libraries close Home, as 16:9 tiles in one row.
                            MyMediaView(state: state, onOpen: onOpen, onRetry: { model.retryLibraries() })
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.bottom, Space.s5)
                        // Rows that arrive, leave or change height slide and fade into place instead of snapping.
                        .animation(reduceMotion ? nil : .easeOut(duration: 0.25), value: state.rows.map { $0.id })
                        .animation(reduceMotion ? nil : .easeOut(duration: 0.25), value: state.continueRow.isVisible)
                        // The grey stand-ins for the hero and the rows leave the same way, so when nothing replaces one the
                        // page closes up smoothly instead of jumping.
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: state.heroLoading)
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: state.hero?.id)
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: state.continueRow.loading)
                        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: state.loadingLibraries)
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
        // The hero's Play found what to play (an episode for a show): the player opens on it, or the page when none was found.
        .onChange(of: model.state?.heroNav != nil) { has in
            guard has, let nav = model.state?.heroNav else { return }
            if let play = nav as? HeroNavPlay { playing = PlayRequest(id: play.itemId) }
            else if let details = nav as? HeroNavDetails { onDetails(details.itemId) }
            model.heroNavHandled()
        }
        .onChange(of: model.state?.openList != nil) { opened in
            if opened, let filter = model.state?.openList {
                onOpenList(filter)
                model.listOpened()
            }
        }
        // The PIN for Switch profile and Switch server. On a clear background view so it doesn't share a modifier slot with the picker sheet.
        .background {
            Color.clear.sheet(isPresented: Binding(
                get: { model.state?.pinPrompt != nil },
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
            // A kids profile can't sign out.
            if model.state?.kidsProfile != true {
                Button(Strings.shared.signOut.titleCased()) { model.signOut() }
            }
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
