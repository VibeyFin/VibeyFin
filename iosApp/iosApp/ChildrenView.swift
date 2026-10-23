import SwiftUI
import Shared

/// What is inside a library, folder, series or season. `title` shows while it loads.
struct ChildrenView: View {
    @StateObject private var model: ChildrenObservable
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var wide: Bool { sizeClass == .regular }

    let title: String
    let onOpen: (MediaItem) -> Void
    /// A season's list leads back to its show.
    var onOpenSeries: (String) -> Void = { _ in }
    /// Called when the session ended and the screen must be left.
    let onExit: (HomeExit) -> Void

    init(
        parentId: String,
        title: String,
        onOpen: @escaping (MediaItem) -> Void,
        onOpenSeries: @escaping (String) -> Void = { _ in },
        onExit: @escaping (HomeExit) -> Void
    ) {
        self.onOpenSeries = onOpenSeries
        _model = StateObject(wrappedValue: ChildrenObservable(parentId: parentId))
        self.title = title
        self.onOpen = onOpen
        self.onExit = onExit
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: Space.s4) {
                    // A collection opens on its own header instead of the plain title.
                    if let collection = model.state?.collection {
                        CollectionHeaderView(collection: collection, count: Int(model.state?.items.count ?? 0))
                            .padding(.horizontal, -Space.s4)
                    } else {
                        Text(title).screenTitle()
                    }
                    // A season's list leads back to its show.
                    if let series = model.state?.series {
                        Button { onOpenSeries(series.id) } label: {
                            HStack(spacing: Space.s1) {
                                Text(series.name)
                                Image(systemName: "chevron.right").font(.caption.weight(.semibold))
                            }
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableButtonStyle())
                        .accessibilityLabel(Strings.shared.goToSeries(name: series.name))
                    }
                    if let state = model.state {
                        content(state)
                    } else {
                        PosterSkeletonGrid(minimumWidth: 130)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .screenMargin()
                .padding(.vertical, Space.s5)
                // A collection's header and a season's link to its show arrive with the data: they ease in instead of snapping.
                .animation(.easeOut(duration: 0.25), value: model.state?.collection != nil)
                .animation(.easeOut(duration: 0.25), value: model.state?.series != nil)
            }
        }
        .onAppear { model.start() }
        .backMenuTitle(title)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    private func groups(_ state: ChildrenUiState) -> [(String, [MediaItem])] {
        let movies = state.items.filter { $0.kind == ItemKind.movie }
        let shows = state.items.filter { $0.kind == ItemKind.series }
        let other = state.items.filter { $0.kind != ItemKind.movie && $0.kind != ItemKind.series }
        return [
            (Strings.shared.collectionMovies, movies),
            (Strings.shared.collectionShows, shows),
            (Strings.shared.collectionOther, other),
        ].filter { !$0.1.isEmpty }
    }

    @ViewBuilder
    private func content(_ state: ChildrenUiState) -> some View {
        if state.loading {
            PosterSkeletonGrid(minimumWidth: 130)
        } else if let message = state.message {
            LoadErrorView(message: message, onRetry: { model.retry() })
        } else if state.isEmpty {
            Text(state.emptyText)
                .font(.body)
                .foregroundStyle(.secondary)
        } else {
            if state.collection != nil {
                // A collection's members under sub-headings: its movies, then its shows, then whatever else it holds.
                ForEach(groups(state), id: \.0) { group in
                    Text(group.0).font(.title3.weight(.semibold))
                    ArtworkGrid(items: group.1, minimumWidth: wide ? 168 : 130, onOpen: onOpen, onNearEnd: {})
                }
            } else {
                ArtworkGrid(
                    items: state.items,
                    minimumWidth: 130,
                    onOpen: onOpen,
                    onNearEnd: { if state.hasMore { model.loadMore() } }
                )
            }
            if state.loadingMore {
                LoadingIndicator()
            }
            if state.loadMoreFailed {
                LoadErrorView(
                    message: Strings.shared.loadMoreFailed,
                    onRetry: { model.retry() }
                )
            }
        }
    }
}
