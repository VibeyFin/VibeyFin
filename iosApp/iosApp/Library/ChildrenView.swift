import SwiftUI
import Shared

/// What is inside a library, folder, series or season. `title` shows while it loads.
struct ChildrenView: View {
    @StateObject private var model: ChildrenObservable
    @State private var scrollOffset: CGFloat = 0
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
                        CollectionHeaderView(collection: collection, count: Int(model.state?.items.count ?? 0), scrollOffset: scrollOffset)
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
                .background(alignment: .top) { ScrollOffsetReader { scrollOffset = max($0, 0) }.frame(height: 0) }
                // A collection's header and a season's link to its show arrive with the data: they ease in instead of snapping.
                .animation(.easeOut(duration: 0.25), value: model.state?.collection != nil)
                .animation(.easeOut(duration: 0.25), value: model.state?.series != nil)
            }
        }
        .onAppear { model.start() }
        .failureBanner(
            model.state?.downloadMessage,
            onRetry: { model.retryDownload() },
            onDismiss: { model.dismissDownloadMessage() }
        )
        .downloadPrompts(
            prompt: model.state?.downloadPrompt,
            removal: model.state?.removePrompt,
            onToggleUnwatched: { model.toggleUnwatchedOnly() },
            onConfirm: { model.confirmDownload() },
            onDismiss: { model.dismissDownloadPrompt() },
            onConfirmRemove: { model.confirmRemove() },
            onDismissRemoval: { model.dismissRemoval() }
        )
        .backMenuTitle(title)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.exit != nil) { _, hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    private func seasonDownloadRow(_ state: ChildrenUiState) -> some View {
        HStack(spacing: Space.s3) {
            Button { model.downloadSeason() } label: {
                Label(Strings.shared.downloadSeasonButton.titleCased(), systemImage: "arrow.down.to.line")
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, Space.s4)
                    .frame(minHeight: 44)
                    .background(Capsule().fill(Color(.tertiarySystemBackground)))
            }
            .buttonStyle(PressableButtonStyle())
            if !state.seasonDownloads.isEmpty {
                Text(Strings.shared.seasonDownloadedCount(done: state.seasonDownloaded, total: Int32(state.items.count)))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
                Button { model.removeSeasonDownloads() } label: {
                    Image(systemName: "trash")
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressableButtonStyle())
                .foregroundStyle(.secondary)
                .accessibilityLabel(Strings.shared.removeDownloadsOf(count: Int32(state.seasonDownloads.count)))
            }
        }
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
            // A season's list can be downloaded as a whole, and says how much of it already is.
            if state.offersSeasonDownload {
                seasonDownloadRow(state)
            }
            if state.collection != nil {
                // A collection's members under sub-headings: its movies, then its shows, then whatever else it holds.
                ForEach(state.collectionGroups, id: \.title) { group in
                    Text(group.title).font(.title3.weight(.semibold))
                    ArtworkGrid(items: group.items, minimumWidth: wide ? 168 : 130, onOpen: onOpen, onNearEnd: {})
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
