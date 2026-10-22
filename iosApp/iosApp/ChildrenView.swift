import SwiftUI
import Shared

/// What is inside a library, folder, series or season. `title` shows while it loads.
struct ChildrenView: View {
    @StateObject private var model: ChildrenObservable

    let title: String
    let onOpen: (MediaItem) -> Void
    /// Called when the session ended and the screen must be left.
    let onExit: (HomeExit) -> Void

    init(
        parentId: String,
        title: String,
        onOpen: @escaping (MediaItem) -> Void,
        onExit: @escaping (HomeExit) -> Void
    ) {
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
                    Text(title).screenTitle()
                    if let state = model.state {
                        content(state)
                    } else {
                        PosterSkeletonGrid(minimumWidth: 130)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .screenMargin()
                .padding(.vertical, Space.s5)
            }
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    @ViewBuilder
    private func content(_ state: ChildrenUiState) -> some View {
        if state.loading {
            PosterSkeletonGrid(minimumWidth: 130)
        } else if let message = state.message {
            LoadErrorView(message: message, onRetry: { model.retry() })
        } else if state.isEmpty {
            Text(Strings.shared.emptyFolder)
                .font(.body)
                .foregroundStyle(.secondary)
        } else {
            ArtworkGrid(
                items: state.items,
                minimumWidth: 130,
                onOpen: onOpen,
                onNearEnd: { if state.hasMore { model.loadMore() } },
                headings: state.headings
            )
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
