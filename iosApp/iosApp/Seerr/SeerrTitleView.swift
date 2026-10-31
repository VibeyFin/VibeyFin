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
    @Environment(\.seerrMenu) private var seerrMenu

    init(type: SeerrMediaType, tmdbId: Int32, onOpenItem: @escaping (String) -> Void, onOpenSeerr: @escaping (SeerrTitle) -> Void) {
        _model = StateObject(wrappedValue: SeerrTitleObservable(type: type, tmdbId: tmdbId))
        self.onOpenItem = onOpenItem
        self.onOpenSeerr = onOpenSeerr
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            if let state = model.state, let title = state.title {
                if sizeClass == .regular {
                    wideLayout(state, title)
                } else {
                    phoneLayout(state, title)
                }
            } else if let message = model.state?.message {
                LoadErrorView(message: message, onRetry: { model.retry() }).screenMargin().padding(.top, 96)
                    .frame(maxHeight: .infinity, alignment: .top)
            } else {
                ProgressView().accessibilityLabel(Strings.shared.loadingPage)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbarRole(.editor)
        .onAppear { model.start() }
        .onChange(of: model.state?.openItem) { itemId in
            guard let itemId else { return }
            model.itemOpened()
            onOpenItem(itemId)
        }
        // On this page "View" would lead here, so only Try again is offered.
        .onChange(of: model.state?.notice) { shown in
            guard let shown else { return }
            notice = shown
            model.noticeShown()
        }
        .seerrNoticeBanner(
            notice,
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
                            .padding(.horizontal, Space.s6)
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
                .foregroundStyle(.primary)
                .multilineTextAlignment(centered ? .center : .leading)
                .lineLimit(3)
            // Where it stands, as a tag at the start of the facts: a word or two with its symbol, never aqua.
            HStack(spacing: Space.s3) {
                SeerrStateTag(text: state.tag, state: state.state)
                if !state.facts.isEmpty {
                    Text(Strings.shared.factsLine(facts: state.facts)).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            .accessibilityElement(children: .combine)
            if !state.genres.isEmpty {
                Text(Strings.shared.factsLine(facts: state.genres)).font(.subheadline).foregroundStyle(.secondary)
            }
            if let overview = state.overview {
                Text(overview).font(.subheadline).foregroundStyle(.primary).lineLimit(4)
                    .multilineTextAlignment(centered ? .center : .leading)
            }
            // Who asked and what is next, once someone did.
            if let detail = state.statusDetail { Text(detail).font(.footnote).foregroundStyle(.secondary) }
            if let status4k = state.status4k { SeerrStatusLine(text: status4k, state: state.state) }
            if let primary = state.primary {
                // The one aqua button, with the trailer beside it as Play has Start over. With nothing to do it stays, quiet and
                // disabled, so the page doesn't jump.
                HStack(spacing: Space.s3) {
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
                    if let trailer = state.trailerUrl, let url = URL(string: trailer) {
                        Button { openURL(url) } label: { Image(systemName: "play.rectangle").font(.title3) }
                            .buttonStyle(.bordered)
                            .buttonBorderShape(.circle)
                            .controlSize(.large)
                            .tint(.primary)
                            .accessibilityLabel(Strings.shared.trailer)
                    }
                }
            }
            HStack(spacing: Space.s4) {
                if state.offer4k {
                    SeerrActionButton(title: Strings.shared.seerrRequest4k, systemImage: "plus") { model.request4k() }
                }
                if state.cancelRequestId != nil {
                    SeerrActionButton(title: Strings.shared.seerrCancelRequest, systemImage: "xmark") { model.cancelRequest() }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
    }

    @ViewBuilder
    private func sections(_ state: SeerrTitleUiState) -> some View {
        VStack(alignment: .leading, spacing: Space.s5) {
            if !state.seasons.isEmpty { SeerrSeasonsView(state: state, model: model) }
            if !state.cast.isEmpty {
                VStack(alignment: .leading, spacing: Space.s2) {
                    Text(Strings.shared.cast.titleCased()).sectionTitle().accessibilityAddTraits(.isHeader)
                    // Not tappable: these people have no page in the library.
                    ScrollView(.horizontal, showsIndicators: false) {
                        // The library page's sizes: bigger circles at a regular width.
                        let tile: CGFloat = sizeClass == .regular ? 112 : 88
                        HStack(alignment: .top, spacing: Space.s3) {
                            ForEach(state.cast, id: \.id) { person in
                                VStack(spacing: Space.s1) {
                                    PortraitView(name: person.name, imageUrl: person.photoUrl, size: tile, initialsFont: .headline)
                                    // Names take two lines and parts one, always, so the row keeps one height.
                                    Text(person.name)
                                        .font(.caption.weight(.medium))
                                        .multilineTextAlignment(.center)
                                        .lineLimit(2, reservesSpace: true)
                                    Text(person.character ?? "")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                        .multilineTextAlignment(.center)
                                        .lineLimit(1, reservesSpace: true)
                                }
                                .frame(width: tile)
                                .accessibilityElement(children: .combine)
                            }
                        }
                    }
                }
            }
            if !state.moreLikeThis.isEmpty {
                VStack(alignment: .leading, spacing: Space.s2) {
                    Text(Strings.shared.seerrMoreLikeThis.titleCased()).sectionTitle().accessibilityAddTraits(.isHeader)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(alignment: .top, spacing: Space.s3) {
                            ForEach(state.moreLikeThis, id: \.self) { title in
                                // A title the library has opens the library's page.
                                Button { onOpenSeerr(title) } label: { SeerrPosterTile(title: title) }
                                    .buttonStyle(PressableButtonStyle())
                                    .frame(width: sizeClass == .regular ? 168 : 112)
                                    .simultaneousGesture(LongPressGesture(minimumDuration: 0.45).onEnded { _ in seerrMenu?.open(title) })
                                    .accessibilityAction(named: Strings.shared.showOptions) { seerrMenu?.open(title) }
                            }
                        }
                    }
                }
            }
        }
        .screenMargin()
        .padding(.vertical, Space.s5)
    }
}

/// Where a title stands, as a symbol and words in the primary colour (a failure in red); never aqua.
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

/// One of the actions under the primary button: a symbol over a short label, as on the detail page.
private struct SeerrActionButton: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: Space.s1) {
                Image(systemName: systemImage).font(.title2)
                Text(title.titleCased()).font(.caption.weight(.medium)).lineLimit(1)
            }
            .foregroundStyle(.primary)
            .frame(minWidth: 64, minHeight: 64)
        }
        .buttonStyle(PressableButtonStyle())
    }
}

/// The state as a tag: its symbol alone on a quiet round chip, in the primary colour (a failure in red); never aqua. `text` is what
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

/// As on a library show's page: season buttons, the chosen season's state, then its episodes as cards with their text on the
/// still. Seerr's episodes don't play: they show what you'd be asking for.
private struct SeerrSeasonsView: View {
    let state: SeerrTitleUiState
    @ObservedObject var model: SeerrTitleObservable

    var body: some View {
        let chosen = state.episodes?.season
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Strings.shared.seerrSeasons.titleCased()).sectionTitle().accessibilityAddTraits(.isHeader)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s2) {
                    ForEach(state.seasons, id: \.number) { season in
                        let selected = season.number == chosen
                        Button { model.selectSeason(season.number) } label: {
                            Text(season.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                                .padding(.horizontal, Space.s4).padding(.vertical, Space.s2)
                                .foregroundStyle(selected ? Brand.onAccentSoft : Color.secondary)
                                .background(selected ? Brand.accentSoft : Color(.secondarySystemBackground), in: Capsule())
                        }
                        .buttonStyle(PressableButtonStyle())
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
            }
            episodes
        }
    }

    @ViewBuilder
    private var episodes: some View {
        if let episodes = state.episodes, let message = episodes.message {
            LoadErrorView(message: message, onRetry: { model.retryEpisodes() })
        } else if let episodes = state.episodes, !episodes.loading || !episodes.episodes.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: Space.s3) {
                    ForEach(episodes.episodes, id: \.number) { episode in SeerrEpisodeCard(episode: episode) }
                }
            }
        } else {
            HStack(spacing: Space.s3) {
                ForEach(0..<2, id: \.self) { _ in SkeletonBlock().frame(width: 300, height: 300 / 1.6) }
            }
        }
    }
}

private struct SeerrEpisodeCard: View {
    let episode: SeerrEpisode

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Artwork(url: episode.stillUrl, title: episode.title, aspectRatio: 1.6, blurHash: nil)
            LinearGradient(
                stops: [.init(color: .clear, location: 0.4), .init(color: .black.opacity(0.55), location: 0.75), .init(color: .black.opacity(0.78), location: 1)],
                startPoint: .top,
                endPoint: .bottom
            )
            VStack(alignment: .leading, spacing: Space.s1) {
                if let tag = Strings.shared.episodeTag(season: KotlinInt(int: episode.season), episode: KotlinInt(int: episode.number)) {
                    Text(tag).font(.caption2.weight(.semibold)).padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Brand.scrim, in: RoundedRectangle(cornerRadius: 6))
                }
                Text(episode.title).font(.headline).lineLimit(1)
                // Two lines, always, so every card in the row is the same.
                Text(episode.overview ?? "").font(.subheadline).lineLimit(2, reservesSpace: true)
                HStack {
                    if let rating = episode.rating { Text(Strings.shared.ratingTag(rating: rating.doubleValue)).font(.caption.weight(.semibold)) }
                    Spacer()
                    if let date = Strings.shared.releaseDate(iso: episode.airDate) { Text(date).font(.caption).foregroundStyle(.white.opacity(0.75)) }
                }
            }
            .foregroundStyle(.white)
            .padding(Space.s3)
        }
        .frame(width: 300, height: 300 / 1.6)
        .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// A state's symbol. Not in your library is the Library tab's symbol with one diagonal line through it (SF Symbols has no
/// such symbol); the others are `SeerrMarker`'s.
struct SeerrStateSymbol: View {
    let state: RequestState

    var body: some View {
        if state == .notrequested {
            Image(systemName: "play.rectangle.on.rectangle")
                .overlay {
                    GeometryReader { proxy in
                        Path { path in
                            path.move(to: CGPoint(x: proxy.size.width * 0.08, y: proxy.size.height * 0.08))
                            path.addLine(to: CGPoint(x: proxy.size.width * 0.92, y: proxy.size.height * 0.92))
                        }
                        .stroke(style: StrokeStyle(lineWidth: max(1.5, proxy.size.width / 12), lineCap: .round))
                    }
                }
        } else {
            Image(systemName: SeerrMarker.symbol(state))
        }
    }
}

