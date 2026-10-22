import SwiftUI
import Shared

/// The page a category button or the Categories list opens, titled with its name ("Shows", "New", "Action"). It is
/// drawn like a folder's list, with Sort and Genre buttons under the title that open a list to pick from.
struct FilteredListView: View {
    @StateObject private var model: FilteredListObservable

    let onOpen: (MediaItem) -> Void
    /// Called when the session ended and the screen must be left.
    let onExit: (HomeExit) -> Void

    /// The Library tab's root page: a larger title with how many titles it holds, and no back button.
    var isRoot = false
    @State private var peopleQuery = ""

    init(filter: HomeFilter, isRoot: Bool = false, onOpen: @escaping (MediaItem) -> Void, onExit: @escaping (HomeExit) -> Void) {
        self.isRoot = isRoot
        _model = StateObject(wrappedValue: FilteredListObservable(filter: filter))
        self.onOpen = onOpen
        self.onExit = onExit
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: Space.s3) {
                // The title and the buttons stay put while the list scrolls.
                VStack(alignment: .leading, spacing: 2) {
                    Text(model.title)
                        .screenTitle()
                        .accessibilityAddTraits(.isHeader)
                    if isRoot {
                        // The count keeps its line (and its last value) while a filter reloads, so the page below never moves.
                        Text(model.state?.list.total.map { Strings.shared.libraryCount(count: $0.int32Value) } ?? " ")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
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
                    // The old list stays, dimmed, until the new one is in: nothing jumps.
                    .opacity(model.state?.list.refreshing == true ? 0.45 : 1)
                }
            }
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .navigationBarBackButtonHidden(isRoot)
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
                            detail: option.count.map { String($0.int32Value) }
                        )
                    },
                    loading: sheet.loading,
                    message: sheet.message,
                    emptyText: sheet.searchable && sheet.options.isEmpty && !sheet.loading && sheet.message == nil ? sheet.emptyText : nil,
                    onPick: { model.pick($0) },
                    onRetry: { model.retrySheet() },
                    search: sheet.searchable ? Binding(get: { peopleQuery }, set: { peopleQuery = $0; model.sheetQuery($0) }) : nil,
                    searchHint: sheet.hint
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
        } else if state.isEmpty && !state.refreshing {
            Text(state.emptyText)
                .font(.body)
                .foregroundStyle(.secondary)
        } else {
            ArtworkGrid(
                items: state.items,
                minimumWidth: isRoot ? 104 : 130,
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
