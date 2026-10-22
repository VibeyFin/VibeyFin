import SwiftUI
import Shared

/// One item's page: a hero (the backdrop with the title, facts, overview and Play over it), then for a show its seasons and
/// episodes, the cast, what is like it, and the file's details. On a regular width (iPad, a large iPhone in landscape) the
/// hero is the backdrop at 16:9 with the details in a left column.
struct ItemView: View {
    @StateObject private var model: ItemObservable
    @State private var playing: PlayRequest?
    @State private var scrollOffset: CGFloat = 0
    @Environment(\.horizontalSizeClass) private var sizeClass

    private let itemId: String

    /// Opens another item from "More like this".
    let onOpen: (MediaItem) -> Void
    /// An episode's or a season's page leads back to its show.
    let onOpenSeries: (String) -> Void
    let onOpenPerson: (String) -> Void
    /// Called when the session ended and the screen must be left.
    let onExit: (HomeExit) -> Void

    private struct PlayRequest: Identifiable {
        let id: String
        let startOver: Bool
    }

    init(itemId: String, onOpen: @escaping (MediaItem) -> Void, onOpenSeries: @escaping (String) -> Void, onOpenPerson: @escaping (String) -> Void, onExit: @escaping (HomeExit) -> Void) {
        _model = StateObject(wrappedValue: ItemObservable(itemId: itemId))
        self.itemId = itemId
        self.onOpen = onOpen
        self.onOpenSeries = onOpenSeries
        self.onOpenPerson = onOpenPerson
        self.onExit = onExit
    }

    private var wide: Bool { sizeClass == .regular }

    /// The page's side margin: larger on a regular width, where the hero's column and everything under it line up on it.
    private var margin: CGFloat { wide ? Space.s6 : Space.s4 }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            if let state = model.state, !state.loading {
                if let item = state.item {
                    page(item, state: state)
                } else if let message = state.message {
                    ScrollView {
                        LoadErrorView(message: message, onRetry: { model.retry() })
                            .screenMargin()
                            .padding(.vertical, Space.s5)
                    }
                }
            } else {
                // The grey page, in the shapes of the real one; it fades into it, so opening a title is not a sudden cut.
                ItemSkeleton(wide: wide, margin: margin)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.16), value: model.state?.item?.id)
        .onAppear { model.start() }
        .fullScreenCover(item: $playing) { request in
            PlayerView(itemId: request.id, startOver: request.startOver, onExit: onExit)
        }
        .failureBanner(
            model.state?.actionMessage,
            onRetry: { model.retryAction() },
            onDismiss: { model.dismissMessage() }
        )
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    /// What Play opens the player on: the item, or for a show its next episode.
    private func play(startOver: Bool, state: ItemUiState) {
        playing = PlayRequest(id: state.playTarget?.id ?? itemId, startOver: startOver)
    }

    private func page(_ item: MediaItem, state: ItemUiState) -> some View {
        let sections = VStack(alignment: .leading, spacing: Space.s5) {
            if item.kind == ItemKind.series {
                SeasonsSectionView(
                    seasons: state.seasons,
                    wide: wide,
                    margin: margin,
                    onSelect: { model.selectSeason($0) },
                    onPlay: { playing = PlayRequest(id: $0, startOver: false) },
                    onToggleWatched: { model.toggleEpisodePlayed($0) },
                    onViewEpisode: onOpen,
                    onRetrySeasons: { model.retrySeasons() },
                    onRetryEpisodes: { model.retryEpisodes() }
                )
            }
            if !item.cast.isEmpty {
                CastSectionView(cast: item.cast, tile: wide ? 112 : 88, margin: margin, onOpenPerson: onOpenPerson)
            }
            // Behind the scenes and the like.
            if let extras = state.extras, extras.isVisible {
                MediaRowView(row: extras, onOpen: onOpen, onSeeAll: { _ in }, onRetry: { model.retryExtras() }, tileWidth: wide ? 260 : 200)
                    .padding(.horizontal, -margin)
            }
            // The collections it is part of: a poster each, and one opens the collection's members.
            if let collections = state.collections, collections.isVisible {
                MediaRowView(row: collections, onOpen: onOpen, onSeeAll: { _ in }, onRetry: { model.retryCollections() }, tileWidth: wide ? 168 : 112)
                    .padding(.horizontal, -margin)
            }
            if let similar = state.similar, similar.isVisible {
                MediaRowView(row: similar, onOpen: onOpen, onSeeAll: { _ in }, onRetry: { model.retrySimilar() }, tileWidth: wide ? 168 : 112)
                    .padding(.horizontal, -margin)
            }
            if !state.technical.isEmpty {
                TechnicalInfoView(lines: state.technical)
            }
        }
        .screenMargin()
        .padding(.vertical, Space.s5)
        let playHere: (Bool) -> Void = { play(startOver: $0, state: state) }
        let playTrailer: (String) -> Void = { playing = PlayRequest(id: $0, startOver: false) }
        return Group {
            if wide {
                wideLayout(item, state: state, sections: sections, onPlay: playHere, onPlayTrailer: playTrailer)
            } else {
                phoneLayout(item, state: state, sections: sections, onPlay: playHere, onPlayTrailer: playTrailer)
            }
        }
        .environment(\.screenMarginOverride, wide ? Space.s6 : nil)
    }

    /// The picture stays put behind the page and darkens; the text goes up over it, and the rest of the page covers it.
    private func phoneLayout<Sections: View>(
        _ item: MediaItem,
        state: ItemUiState,
        sections: Sections,
        onPlay: @escaping (Bool) -> Void,
        onPlayTrailer: @escaping (String) -> Void
    ) -> some View {
        GeometryReader { proxy in
            let heroHeight = proxy.size.width * Self.phoneHeroByWidth
            ZStack(alignment: .top) {
                PinnedItemBackdrop(item: item, height: heroHeight, progress: scrollOffset / max(heroHeight * Self.darkenOver, 1))
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        // Reports how far the page has scrolled.
                        ScrollOffsetReader { scrollOffset = max($0, 0) }
                            .frame(height: 0)
                        ItemHeroView(
                            item: item,
                            state: state,
                            minHeight: heroHeight,
                            onPlay: onPlay,
                            onOpenSeries: onOpenSeries,
                            onToggleFavorite: { model.toggleFavorite() },
                            onPlayTrailer: onPlayTrailer,
                            onTogglePlayed: { model.togglePlayed() }
                        )
                        sections
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.systemBackground))
                    }
                }
            }
        }
        .ignoresSafeArea(edges: .top)
    }

    /// A phone's picture is 2:3: this tall relative to the width.
    private static let phoneHeroByWidth: CGFloat = 1.5
    /// The share of the picture that has to scroll by for it to be fully dark.
    private static let darkenOver: CGFloat = 0.55

    /// The backdrop fills the screen and stays put; the first screen leaves it clear with the details in a column on the left, and
    /// the rest scrolls up over it while it darkens.
    private func wideLayout<Sections: View>(
        _ item: MediaItem,
        state: ItemUiState,
        sections: Sections,
        onPlay: @escaping (Bool) -> Void,
        onPlayTrailer: @escaping (String) -> Void
    ) -> some View {
        GeometryReader { proxy in
            ZStack {
                WideItemBackdrop(item: item, progress: scrollOffset / max(proxy.size.height * 0.7, 1))
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        // Reports how far the page has scrolled.
                        ScrollOffsetReader { scrollOffset = max($0, 0) }
                            .frame(height: 0)
                        // The top of the first screen is the picture, clear; the details sit at the bottom of it.
                        Color.clear.frame(height: proxy.size.height * 0.3)
                        HeroDetailsView(
                            item: item,
                            state: state,
                            centered: false,
                            onPlay: onPlay,
                            onOpenSeries: onOpenSeries,
                            onToggleFavorite: { model.toggleFavorite() },
                            onPlayTrailer: onPlayTrailer,
                            onTogglePlayed: { model.togglePlayed() }
                        )
                        .frame(maxWidth: min(proxy.size.width * 0.46, 520), alignment: .leading)
                        .padding(.horizontal, margin)
                        sections
                    }
                }
            }
        }
    }
}

/// How far the scroll view around it has scrolled, read from the `UIScrollView` itself: SwiftUI's own geometry preferences don't
/// report a scroll that is under way. Put inside a `ScrollView`'s content; 0 at the top, growing as the page scrolls up.
private struct ScrollOffsetReader: UIViewRepresentable {
    let onChange: (CGFloat) -> Void

    func makeUIView(context: Context) -> ReaderView { ReaderView() }

    func updateUIView(_ view: ReaderView, context: Context) { view.onChange = onChange }

    final class ReaderView: UIView {
        var onChange: ((CGFloat) -> Void)?
        private var observation: NSKeyValueObservation?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            observation = nil
            var ancestor = superview
            while let view = ancestor, !(view is UIScrollView) { ancestor = view.superview }
            guard let scrollView = ancestor as? UIScrollView else { return }
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scrollView, _ in
                self?.onChange?(scrollView.contentOffset.y + scrollView.adjustedContentInset.top)
            }
        }
    }
}
