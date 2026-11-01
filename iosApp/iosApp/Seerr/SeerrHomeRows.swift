import SwiftUI
import Shared

/// Lets SwiftUI watch Seerr's rows on Home. `SeerrHomeModel` calls back on the main thread.
final class SeerrHomeObservable: ObservableObject {
    @Published private(set) var state: SeerrHomeUiState?
    private let model = SeerrHomeModel()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] in self?.state = $0 }
    }

    func refresh() { model.onRefresh() }
    func foreground() { model.onForeground() }

    deinit { model.close() }
}

/// A Seerr poster that opens on a tap and offers the Seerr menu on a long press.
struct SeerrTileButton: View {
    let title: SeerrTitle
    let onOpen: (SeerrTitle) -> Void
    var meta: String?
    var plainInLibrary = false

    var body: some View {
        Button { onOpen(title) } label: { SeerrPosterTile(title: title, meta: meta, plainInLibrary: plainInLibrary) }
            .seerrTileButton(title)
    }
}

/// A Seerr row on Home: My requests, or a discover list. Grey posters while it loads; not drawn when empty, and hidden when Seerr
/// fails. In the discover rows a title the library has looks like the library's own tile.
struct SeerrRowView: View {
    let row: SeerrRow
    let onOpen: (SeerrTitle) -> Void
    let onSeeAll: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            HStack {
                Text(row.title.titleCased()).sectionTitle().lineLimit(1).accessibilityAddTraits(.isHeader)
                Spacer()
                if !row.titles.isEmpty {
                    Button(Strings.shared.seeAll.titleCased(), action: onSeeAll)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(Strings.shared.seeAllIn(row: row.title))
                }
            }
            .screenMargin()
            if row.loading {
                // Laid out like the real tiles, so nothing under the row moves when they arrive.
                HStack(alignment: .top, spacing: Space.s3) {
                    ForEach(0..<4, id: \.self) { _ in SeerrPosterPlaceholder().frame(width: 112) }
                }
                .screenMargin()
                .frame(maxWidth: .infinity, alignment: .leading)
                .clipped()
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: Space.s3) {
                        ForEach(row.titles, id: \.self) { title in
                            SeerrTileButton(
                                title: title,
                                onOpen: onOpen,
                                meta: row.showsDates ? Strings.shared.seerrReleaseDay(date: title.releaseDate) : nil,
                                plainInLibrary: row.kind != nil
                            )
                            .frame(width: 112)
                        }
                    }
                    .screenMargin()
                    .liftRoomInside()
                }
                .liftRoom()
            }
        }
    }
}

/// Lets SwiftUI watch a See all page of a discover list.
final class SeerrListObservable: ObservableObject {
    @Published private(set) var state: SeerrListUiState?
    private let model: SeerrListModel
    private var started = false

    init(kind: SeerrListKind) { model = SeerrListModel(kind: kind) }

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] in self?.state = $0 }
    }

    func retry() { model.onRetry() }
    func loadMore() { model.onLoadMore() }
    func typeFilter(_ type: SeerrMediaType?) { model.onTypeFilter(type: type) }

    deinit { model.close() }
}

/// A discover list's See all: a poster grid titled as the row, Movies and Shows chips where it holds both, more as it scrolls.
struct SeerrListView: View {
    @StateObject private var model: SeerrListObservable
    /// The library's grid: bigger posters at a regular width.
    @Environment(\.horizontalSizeClass) private var sizeClass
    let onOpen: (SeerrTitle) -> Void

    init(kind: SeerrListKind, onOpen: @escaping (SeerrTitle) -> Void) {
        _model = StateObject(wrappedValue: SeerrListObservable(kind: kind))
        self.onOpen = onOpen
    }

    var body: some View {
        ScrollView {
            if let state = model.state {
                VStack(alignment: .leading, spacing: Space.s3) {
                    if state.offersTypeFilter {
                        HStack(spacing: Space.s2) {
                            FilterChipButton(label: Strings.shared.searchMovies.titleCased(), active: state.typeFilter == .movie) { model.typeFilter(.movie) }
                            FilterChipButton(label: Strings.shared.searchShows.titleCased(), active: state.typeFilter == .show) { model.typeFilter(.show) }
                        }
                    }
                    if let message = state.message {
                        LoadErrorView(message: message, onRetry: { model.retry() })
                    } else if state.loading {
                        SeerrGridSkeleton()
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: sizeClass == .regular ? 168 : 130), spacing: Space.s3, alignment: .top)], alignment: .leading, spacing: Space.s4) {
                            ForEach(Array(state.titles.enumerated()), id: \.element) { index, title in
                                SeerrTileButton(title: title, onOpen: onOpen, meta: state.showsDates ? Strings.shared.seerrReleaseDay(date: title.releaseDate) : nil, plainInLibrary: true)
                                    .onAppear { if index >= state.titles.count - 6 { model.loadMore() } }
                            }
                        }
                        if state.loadMoreFailed {
                            LoadErrorView(message: Strings.shared.loadMoreFailed, onRetry: { model.retry() })
                        } else if state.loadingMore {
                            ProgressView().frame(maxWidth: .infinity)
                        }
                    }
                }
                .screenMargin()
                .padding(.vertical, Space.s2)
            }
        }
        .navigationTitle((model.state?.title ?? "").titleCased())
        .navigationBarTitleDisplayMode(.large)
        .onAppear { model.start() }
    }
}

/// Lets SwiftUI watch the My requests page.
final class MyRequestsObservable: ObservableObject {
    @Published private(set) var state: MyRequestsUiState?
    private let model = MyRequestsModel()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] in self?.state = $0 }
    }

    func filter(_ filter: RequestFilter) { model.onFilter(filter: filter) }
    func loadMore() { model.onLoadMore() }
    func retry() { model.onRetry() }

    deinit { model.close() }
}

/// My requests: every request of this profile, newest change first, filtered by state, more as it scrolls.
struct MyRequestsView: View {
    @StateObject private var model = MyRequestsObservable()
    /// The library's grid: bigger posters at a regular width.
    @Environment(\.horizontalSizeClass) private var sizeClass
    let onOpen: (SeerrTitle) -> Void

    private static let filters: [(RequestFilter, String)] = [
        (.all, Strings.shared.searchAll),
        (.waiting, Strings.shared.seerrFilterWaiting),
        (.approved, Strings.shared.seerrFilterApproved),
        (.available, Strings.shared.seerrFilterAvailable),
        (.declined, Strings.shared.seerrFilterDeclined),
    ]

    var body: some View {
        ScrollView {
            if let state = model.state {
                VStack(alignment: .leading, spacing: Space.s3) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: Space.s2) {
                            ForEach(Self.filters, id: \.0) { filter, label in
                                FilterChipButton(label: label.titleCased(), active: state.filter == filter) { model.filter(filter) }
                            }
                        }
                    }
                    if let message = state.message {
                        LoadErrorView(message: message, onRetry: { model.retry() })
                    } else if state.loading {
                        SeerrGridSkeleton()
                    } else if state.empty {
                        Text(Strings.shared.seerrNoRequests).font(.body).foregroundStyle(.secondary).padding(.vertical, Space.s5)
                    } else {
                        // A poster grid like the other lists: the pill says where each stands, the line under it when that last changed.
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: sizeClass == .regular ? 168 : 130), spacing: Space.s3, alignment: .top)], alignment: .leading, spacing: Space.s4) {
                            ForEach(Array(state.items.enumerated()), id: \.element.request.id) { index, item in
                                SeerrTileButton(title: item.title, onOpen: onOpen, meta: item.changed)
                                    .onAppear { if index >= state.items.count - 6 { model.loadMore() } }
                            }
                        }
                        if state.loadMoreFailed {
                            LoadErrorView(message: Strings.shared.loadMoreFailed, onRetry: { model.retry() })
                        } else if state.loadingMore {
                            ProgressView().frame(maxWidth: .infinity)
                        }
                    }
                }
                .screenMargin()
                .padding(.vertical, Space.s2)
            }
        }
        .navigationTitle(Strings.shared.seerrMyRequests.titleCased())
        .navigationBarTitleDisplayMode(.large)
        .onAppear { model.start() }
    }
}

/// Grey posters while a Seerr page's first answer is on its way, sized as the library's grid.
private struct SeerrGridSkeleton: View {
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: sizeClass == .regular ? 168 : 130), spacing: Space.s3, alignment: .top)], alignment: .leading, spacing: Space.s4) {
            ForEach(0..<9, id: \.self) { _ in SeerrPosterPlaceholder() }
        }
    }
}
