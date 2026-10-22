import SwiftUI
import Shared

/// The page a category button or the Categories list opens, titled with its name ("Shows", "New", "Action"). It is
/// drawn like a folder's list, with Sort and Genre buttons under the title that open a list to pick from.
struct FilteredListView: View {
    @StateObject private var model: FilteredListObservable

    let onOpen: (MediaItem) -> Void
    /// Called when the session ended and the screen must be left.
    let onExit: (HomeExit) -> Void

    init(filter: HomeFilter, onOpen: @escaping (MediaItem) -> Void, onExit: @escaping (HomeExit) -> Void) {
        _model = StateObject(wrappedValue: FilteredListObservable(filter: filter))
        self.onOpen = onOpen
        self.onExit = onExit
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: Space.s3) {
                // The title and the buttons stay put while the list scrolls.
                Text(model.title)
                    .screenTitle()
                    .accessibilityAddTraits(.isHeader)
                    .screenMargin()
                    .padding(.top, Space.s5)
                if let state = model.state {
                    ListChipRow(chips: state.chips) { model.chip($0.kind) }
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: Space.s4) {
                        if let state = model.state {
                            content(state.list)
                        } else {
                            PosterSkeletonGrid(minimumWidth: 130)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .screenMargin()
                    .padding(.bottom, Space.s5)
                }
            }
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.list.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.list.exit { onExit(exit) }
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
                            selected: option.id == sheet.selectedId,
                            detail: option.count.map { String(truncating: $0) }
                        )
                    },
                    loading: sheet.loading,
                    message: sheet.message,
                    emptyText: nil,
                    onPick: { model.pick($0) },
                    onRetry: { model.retrySheet() }
                )
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
            Text(Strings.shared.emptyFolder)
                .font(.body)
                .foregroundStyle(.secondary)
        } else {
            ArtworkGrid(
                items: state.items,
                minimumWidth: 130,
                onOpen: onOpen,
                onNearEnd: { if state.hasMore { model.loadMore() } }
            )
            if state.loadingMore {
                LoadingIndicator()
            }
            if state.loadMoreFailed {
                LoadErrorView(message: Strings.shared.loadMoreFailed, onRetry: { model.retry() })
            }
        }
    }
}
