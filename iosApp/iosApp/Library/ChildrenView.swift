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
            if let collection = model.state?.collection {
                GeometryReader { proxy in
                    if wide { collectionWide(collection, size: proxy.size) } else { collectionPhone(collection, width: proxy.size.width) }
                }
                .ignoresSafeArea(edges: .top)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: Space.s4) {
                        Text(title).screenTitle()
                        listBody
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .screenMargin()
                    .padding(.vertical, Space.s5)
                    .animation(.easeOut(duration: 0.25), value: model.state?.series != nil)
                }
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

    /// A season's link to its show, then the list or its placeholder.
    private var listBody: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
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
    }

    /// The picture stays put behind the page and darkens; the title goes up over it and the list covers it, as on an item's page.
    private func collectionPhone(_ collection: MediaItem, width: CGFloat) -> some View {
        let backdropHeight: CGFloat = width * 1.5
        return ZStack(alignment: .top) {
            PinnedItemBackdrop(item: collection, height: backdropHeight, progress: scrollOffset / max(backdropHeight * ScrollDarkening.darkenOver, 1))
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ScrollOffsetReader { scrollOffset = max($0, 0) }.frame(height: 0)
                    CollectionHeaderView(collection: collection, count: Int(model.state?.items.count ?? 0), centered: true)
                        .screenMargin()
                        .padding(.bottom, Space.s4)
                        .frame(minHeight: width * 1.0, alignment: .bottom)
                    listBody
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .screenMargin()
                        .padding(.bottom, Space.s5)
                        .background(Color(.systemBackground))
                }
            }
        }
    }

    /// The picture fills the screen; the title sits in a column on the left, and the list scrolls up over it while it darkens.
    private func collectionWide(_ collection: MediaItem, size: CGSize) -> some View {
        ZStack {
            WideItemBackdrop(item: collection, progress: scrollOffset / max(size.height * 0.7, 1))
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ScrollOffsetReader { scrollOffset = max($0, 0) }.frame(height: 0)
                    Color.clear.frame(height: size.height * 0.3)
                    CollectionHeaderView(collection: collection, count: Int(model.state?.items.count ?? 0), centered: false)
                        .frame(maxWidth: min(size.width * 0.46, 520), alignment: .leading)
                        .screenMargin()
                        .padding(.bottom, Space.s4)
                    listBody
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .screenMargin()
                        .padding(.bottom, Space.s5)
                }
            }
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
