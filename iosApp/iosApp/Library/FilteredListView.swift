import SwiftUI
import Shared

/// The page a category button or the Categories list opens, titled with its name ("Shows", "New", "Action"). It is
/// drawn like a folder's list, with the filter buttons under the title; Filters, Sort, Genre and the like open a list to pick from.
struct FilteredListView: View {
    @StateObject private var model: FilteredListObservable

    let onOpen: (MediaItem) -> Void
    /// Called when the session ended and the screen must be left.
    let onExit: (HomeExit) -> Void

    /// The Library tab's root page: the tabs' header with the profile, and no back button.
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
                // The title and the buttons stay put while the list scrolls. The tab's root has the same header as the other tabs.
                if isRoot {
                    TabHeader(title: model.title)
                } else {
                    Text(model.title)
                        .screenTitle()
                        .accessibilityAddTraits(.isHeader)
                        .screenMargin()
                        .padding(.top, Space.s5)
                }
                if let state = model.state {
                    ListChipRow(chips: state.chips) { model.chip($0.kind) }
                } else {
                    // The chips' height is held until they arrive, so the grid below doesn't drop when they do.
                    Color.clear.frame(height: 44)
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: Space.s4) {
                        if let state = model.state {
                            content(state.list)
                        } else {
                            PosterSkeletonGrid(minimumWidth: isRoot ? 104 : 130)
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
        .backMenuTitle(model.title)
        // A tab root draws its own title; the system bar above it would only be empty space. A pushed page needs it for back.
        .tabRootBar(enabled: isRoot)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .navigationBarBackButtonHidden(isRoot)
        .onChange(of: model.state?.list.exit != nil) { _, hasExit in
            if hasExit, let exit = model.state?.list.exit { onExit(exit) }
        }
        .sheet(isPresented: Binding(
            get: { model.state?.sheet != nil },
            set: { if !$0 { model.dismissSheet() } }
        )) {
            if let sheet = model.state?.sheet {
                PickerSheetView(
                    title: sheet.title,
                    rows: sheet.rows.map { row in
                        PickerRow(id: row.id, label: row.label, selected: row.selected, detail: row.detail)
                    },
                    loading: sheet.loading,
                    message: sheet.message,
                    emptyText: sheet.searchable && sheet.rows.isEmpty && !sheet.loading && sheet.message == nil ? sheet.emptyText : nil,
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
            PosterSkeletonGrid(minimumWidth: isRoot ? 104 : 130)
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
