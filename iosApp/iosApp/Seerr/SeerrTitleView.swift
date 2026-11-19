import SwiftUI
import Shared

/// The page of a title that isn't in the library: the detail page's hero and layout, with Seerr's status and one action. A title the
/// library has opens the library's page instead (`onOpenItem`, which takes this page's place).
struct SeerrTitleView: View {
    @StateObject private var model: SeerrTitleObservable
    let onOpenItem: (String) -> Void
    let onOpenSeerr: (SeerrTitle) -> Void

    @State private var scrollOffset: CGFloat = 0
    @State private var notice: SeerrNotice?
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.openURL) private var openURL

    init(type: SeerrMediaType, tmdbId: Int32, onOpenItem: @escaping (String) -> Void, onOpenSeerr: @escaping (SeerrTitle) -> Void) {
        _model = StateObject(wrappedValue: SeerrTitleObservable(type: type, tmdbId: tmdbId))
        self.onOpenItem = onOpenItem
        self.onOpenSeerr = onOpenSeerr
    }

    private var wide: Bool { sizeClass == .regular }

    /// The page's side margin, as `ItemView` sets it.
    private var margin: CGFloat { wide ? Space.s6 : Space.s4 }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            if let state = model.state, let title = state.title {
                if wide {
                    wideLayout(state, title)
                } else {
                    phoneLayout(state, title)
                }
            } else if let message = model.state?.message {
                ScrollView {
                    LoadErrorView(message: message, onRetry: { model.retry() })
                        .screenMargin()
                        .padding(.vertical, Space.s5)
                }
            } else {
                // The grey page, in the shapes of the real one; it fades into it, as a library title does.
                ItemSkeleton(wide: wide, margin: margin)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.16), value: model.state?.title?.tmdbId)
        .environment(\.screenMarginOverride, wide ? Space.s6 : nil)
        .backMenuTitle(model.state?.title?.title ?? "")
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onAppear { model.start() }
        .onChange(of: model.state?.openItem) { _, itemId in
            guard let itemId else { return }
            model.itemOpened()
            onOpenItem(itemId)
        }
        .onChange(of: model.state?.notice) { _, shown in
            guard let shown else { return }
            notice = shown
            model.noticeShown()
        }
        .seerrNoticeBanner(
            notice,
            // On this page "View" would lead here, so only Try again is offered.
            showsAction: notice?.action == .tryagain,
            onAction: { model.retryRequest() },
            onDismiss: { notice = nil }
        )
        .sheet(isPresented: Binding(get: { model.state?.picker != nil }, set: { if !$0 { model.pickerDismiss() } })) {
            if let picker = model.state?.picker {
                SeerrSeasonPickerView(picker: picker, sending: model.state?.sending == true, model: model)
            }
        }
    }

    private func phoneLayout(_ state: SeerrTitleUiState, _ title: SeerrTitle) -> some View {
        GeometryReader { proxy in
            let heroHeight = proxy.size.width * 1.5
            ZStack(alignment: .top) {
                PinnedItemBackdrop(title: title.title, url: title.backdropUrl ?? title.posterUrl, blurHash: nil, height: heroHeight, progress: scrollOffset / max(heroHeight * 0.55, 1))
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ScrollOffsetReader { scrollOffset = max($0, 0) }.frame(height: 0)
                        hero(state, title, centered: true)
                            .padding(.horizontal, Space.s4)
                            .padding(.bottom, Space.s4)
                            .frame(maxWidth: .infinity, minHeight: heroHeight, alignment: .bottom)
                        sections(state)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.systemBackground))
                    }
                }
            }
        }
        .ignoresSafeArea(edges: .top)
    }

    private func wideLayout(_ state: SeerrTitleUiState, _ title: SeerrTitle) -> some View {
        GeometryReader { proxy in
            ZStack {
                WideItemBackdrop(title: title.title, url: title.backdropUrl ?? title.posterUrl, blurHash: nil, progress: scrollOffset / max(proxy.size.height * 0.7, 1))
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ScrollOffsetReader { scrollOffset = max($0, 0) }.frame(height: 0)
                        Color.clear.frame(height: proxy.size.height * 0.3)
                        hero(state, title, centered: false)
                            .frame(maxWidth: min(proxy.size.width * 0.46, 520), alignment: .leading)
                            .padding(.horizontal, margin)
                        sections(state)
                    }
                }
            }
        }
    }

    /// The title (Seerr has no Logo image, so the brand face), the facts, the overview, the status, the one primary button and the
    /// actions. Centred on a phone, at the start on a regular width.
    private func hero(_ state: SeerrTitleUiState, _ title: SeerrTitle, centered: Bool) -> some View {
        VStack(alignment: centered ? .center : .leading, spacing: Space.s3) {
            Text(title.title)
                .font(.brandDisplay)
                .tracking(-0.4)
                .foregroundStyle(.primary)
                .multilineTextAlignment(centered ? .center : .leading)
                .lineLimit(3)
            HStack(spacing: Space.s3) {
                SeerrStateTag(text: state.tag, state: state.state)
                if !state.facts.isEmpty {
                    Text(Strings.shared.factsLine(facts: state.facts)).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            .accessibilityElement(children: .combine)
            if !state.genres.isEmpty {
                Text(Strings.shared.factsLine(facts: state.genres))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(centered ? .center : .leading)
            }
            if let overview = state.overview { ExpandableOverview(text: overview) }
            // Who asked and what is next, once someone did.
            if let detail = state.statusDetail { Text(detail).font(.footnote).foregroundStyle(.secondary) }
            if let status4k = state.status4k { SeerrStatusLine(text: status4k, state: state.state) }
            if let primary = state.primary { primaryButton(state, primary, centered: centered) }
            actions(state)
        }
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
    }

    /// With nothing to do the button stays, disabled, so the page doesn't jump.
    private func primaryButton(_ state: SeerrTitleUiState, _ primary: SeerrPrimary, centered: Bool) -> some View {
        PrimaryButton(action: { model.request() }) {
            // A plus and the words; with nothing to do, the state's own symbol and its name.
            if state.sending && state.picker == nil {
                PrimaryButtonSpinner()
            } else {
                HStack(spacing: Space.s2) {
                    if primary.enabled { Image(systemName: "plus") } else { SeerrStateSymbol(state: state.state) }
                    Text(primary.label.titleCased()).lineLimit(1)
                }
            }
        }
        .disabled(!primary.enabled)
        .frame(maxWidth: centered ? .infinity : 260)
    }

    /// The actions under the primary button, in the library page's row: Trailer (only when there is one), Request in 4K and Cancel
    /// request, each an icon over a label, in the same places.
    private func actions(_ state: SeerrTitleUiState) -> some View {
        ActionRowLayout {
            if let trailer = state.trailerUrl, let url = URL(string: trailer) {
                HeroActionButton(label: Strings.shared.trailer, description: Strings.shared.trailer, systemImage: "film") { openURL(url) }
            }
            if state.offer4k {
                HeroActionButton(label: Strings.shared.seerrRequest4k, description: Strings.shared.seerrRequest4k, systemImage: "plus") { model.request4k() }
            }
            if state.cancelRequestId != nil {
                HeroActionButton(label: Strings.shared.seerrCancelRequest, description: Strings.shared.seerrCancelRequest, systemImage: "xmark") { model.cancelRequest() }
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func sections(_ state: SeerrTitleUiState) -> some View {
        VStack(alignment: .leading, spacing: Space.s5) {
            if !state.seasons.isEmpty { SeerrSeasonsView(state: state, wide: wide, margin: margin, model: model) }
            if !state.cast.isEmpty {
                // Not tappable: these people have no page in the library.
                CastSectionView(
                    cast: state.cast.map { CastPerson(id: String($0.id), name: $0.name, role: $0.character, imageUrl: $0.photoUrl) },
                    tile: wide ? 112 : 88,
                    margin: margin
                )
            }
            if !state.moreLikeThis.isEmpty {
                VStack(alignment: .leading, spacing: Space.s3) {
                    Text(Strings.shared.seerrMoreLikeThis.titleCased()).sectionTitle().lineLimit(1).accessibilityAddTraits(.isHeader).screenMargin()
                    HorizontalRow {
                        LazyHStack(alignment: .top, spacing: Space.s3) {
                            ForEach(state.moreLikeThis, id: \.self) { title in
                                // A title the library has opens the library's page.
                                Button { onOpenSeerr(title) } label: { SeerrPosterTile(title: title) }
                                    .seerrTileButton(title)
                                    .frame(width: wide ? 168 : 112)
                            }
                        }
                    }
                }
                .padding(.horizontal, -margin)
            }
        }
        .screenMargin()
        .padding(.vertical, Space.s5)
    }
}

/// Where a title stands, as a symbol and words in the primary colour (a failure in red).
struct SeerrStatusLine: View {
    let text: String
    let state: RequestState

    var body: some View {
        Label {
            Text(text).font(.subheadline)
        } icon: {
            SeerrStateSymbol(state: state)
        }
        .foregroundStyle(state == .failed ? Color(.systemRed) : Color.primary)
    }
}

/// The state as a tag: its symbol alone on a quiet round chip, in the primary colour (a failure in red). `text` is what
/// VoiceOver says.
struct SeerrStateTag: View {
    let text: String
    let state: RequestState

    var body: some View {
        SeerrStateSymbol(state: state)
            .font(.caption.weight(.semibold))
            .foregroundStyle(state == .failed ? Color(.systemRed) : Color.primary)
            .frame(width: 28, height: 28)
            .background(Color(.tertiarySystemFill), in: Circle())
            .accessibilityLabel(text)
    }
}

/// As on a library show's page: season buttons, then the chosen season's episodes, down the page on a phone and as cards in a row
/// on a regular width. Seerr's episodes don't play: they show what you'd be asking for.
private struct SeerrSeasonsView: View {
    let state: SeerrTitleUiState
    let wide: Bool
    let margin: CGFloat
    @ObservedObject var model: SeerrTitleObservable

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            SeasonButtonsRow(
                seasons: state.seasons.map { SeasonButton(id: $0.number, label: $0.name) },
                selectedId: state.episodes?.season,
                margin: margin,
                onSelect: { model.selectSeason($0) }
            )
            episodes
        }
    }

    @ViewBuilder
    private var episodes: some View {
        let list = state.episodes
        EpisodeListArea(
            episodes: list?.episodes ?? [],
            id: \.number,
            loading: list?.loading ?? true,
            message: list?.message,
            wide: wide,
            margin: margin,
            onRetry: { model.retryEpisodes() },
            row: { SeerrEpisodeRowView(episode: $0) },
            card: { SeerrEpisodeCardView(episode: $0, width: $1) }
        )
    }
}

/// A state's symbol. Not in your library is the Library tab's symbol as an outline with one diagonal line through it (SF Symbols
/// has no such symbol); the others are `SeerrMarker`'s. A gap is cut either side of the line, as SF Symbols' slashed symbols do,
/// so it reads at small sizes.
struct SeerrStateSymbol: View {
    let state: RequestState

    var body: some View {
        if state == .notrequested {
            Image(systemName: "play.rectangle.on.rectangle")
                .symbolVariant(.none)
                .mask {
                    GeometryReader { proxy in
                        Rectangle()
                            .overlay {
                                Self.slash(in: proxy.size)
                                    .stroke(style: StrokeStyle(lineWidth: Self.lineWidth(proxy.size) * 2.5, lineCap: .round))
                                    .blendMode(.destinationOut)
                            }
                            .compositingGroup()
                    }
                }
                .overlay {
                    GeometryReader { proxy in
                        Self.slash(in: proxy.size)
                            .stroke(style: StrokeStyle(lineWidth: Self.lineWidth(proxy.size), lineCap: .round))
                    }
                }
        } else {
            Image(systemName: SeerrMarker.symbol(state))
        }
    }

    private static func lineWidth(_ size: CGSize) -> CGFloat {
        max(1.5, size.width / 12)
    }

    private static func slash(in size: CGSize) -> Path {
        Path { path in
            path.move(to: CGPoint(x: size.width * 0.08, y: size.height * 0.08))
            path.addLine(to: CGPoint(x: size.width * 0.92, y: size.height * 0.92))
        }
    }
}
