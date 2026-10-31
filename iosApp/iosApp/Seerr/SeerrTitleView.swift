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
            if !state.facts.isEmpty {
                Text(Strings.shared.factsLine(facts: state.facts)).font(.subheadline).foregroundStyle(.secondary)
            }
            if !state.genres.isEmpty {
                Text(Strings.shared.factsLine(facts: state.genres)).font(.subheadline).foregroundStyle(.secondary)
            }
            if let overview = state.overview {
                Text(overview).font(.subheadline).foregroundStyle(.primary).lineLimit(4)
                    .multilineTextAlignment(centered ? .center : .leading)
            }
            if let status = state.status { SeerrStatusLine(text: status, state: state.state) }
            if let status4k = state.status4k { SeerrStatusLine(text: status4k, state: state.state) }
            if let primary = state.primary {
                // The one aqua button. With nothing to do it stays, quiet and disabled, so the page doesn't jump.
                PrimaryButton(action: { model.request() }) {
                    HStack(spacing: Space.s3) {
                        if state.sending && state.picker == nil { PrimaryButtonSpinner() }
                        Text(primary.label.titleCased())
                    }
                }
                .disabled(!primary.enabled)
            }
            HStack(spacing: Space.s4) {
                if let trailer = state.trailerUrl, let url = URL(string: trailer) {
                    SeerrActionButton(title: Strings.shared.trailer, systemImage: "play.rectangle") { openURL(url) }
                }
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
            if !state.seasons.isEmpty {
                VStack(alignment: .leading, spacing: Space.s2) {
                    Text(Strings.shared.seerrSeasons.titleCased()).sectionTitle().accessibilityAddTraits(.isHeader)
                    ForEach(state.seasons, id: \.number) { season in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(season.name).font(.subheadline.weight(.semibold))
                                Text([season.episodes, season.year.map { "\($0)" }].compactMap { $0 }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if let label = season.stateLabel { SeerrStatusLine(text: label, state: season.state) }
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
            }
            if !state.cast.isEmpty {
                VStack(alignment: .leading, spacing: Space.s2) {
                    Text(Strings.shared.cast.titleCased()).sectionTitle().accessibilityAddTraits(.isHeader)
                    // Not tappable: these people have no page in the library.
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(alignment: .top, spacing: Space.s3) {
                            ForEach(state.cast, id: \.id) { person in
                                VStack(spacing: Space.s1) {
                                    ZStack {
                                        Circle().fill(Color(.tertiarySystemBackground))
                                        Text(Strings.shared.initials(name: person.name)).font(.headline).foregroundStyle(.secondary)
                                        if let url = person.photoUrl.flatMap(URL.init(string:)) {
                                            AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                                        }
                                    }
                                    .frame(width: 72, height: 72)
                                    .clipShape(Circle())
                                    Text(person.name).font(.caption.weight(.semibold)).lineLimit(2).multilineTextAlignment(.center)
                                    if let character = person.character {
                                        Text(character).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }
                                .frame(width: 80)
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
                                    .frame(width: 112)
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
            Image(systemName: state == .notrequested ? "plus" : SeerrMarker.symbol(state))
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
