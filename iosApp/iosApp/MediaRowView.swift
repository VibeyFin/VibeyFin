import SwiftUI
import Shared

/// A row of posters under a header with "See all": loading is grey posters, a failure says why with Try again. The row
/// runs to the screen edge; its first tile sits at the margin and the next always peeks.
struct MediaRowView: View {
    let row: MediaRow
    let onOpen: (MediaItem) -> Void
    let onSeeAll: (SeeAll) -> Void
    let onRetry: () -> Void

    private let tileWidth: CGFloat = 112
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private enum Phase { case loading, failed, ready }

    private var phase: Phase {
        row.loading ? .loading : (row.message != nil ? .failed : .ready)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            HStack {
                Text(row.title)
                    .sectionTitle()
                    // One line, so a long library name never makes the header taller.
                    .lineLimit(1)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                if let seeAll = row.seeAll, !row.items.isEmpty {
                    Button(Strings.shared.seeAll.titleCased()) { onSeeAll(seeAll) }
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(Strings.shared.seeAllIn(row: row.title))
                }
            }
            .screenMargin()
            // The grey posters give way to the real ones with a short fade.
            Group {
                if row.loading {
                    PosterSkeletonRow(tileWidth: tileWidth)
                } else if let message = row.message {
                    LoadErrorView(message: message, onRetry: onRetry)
                        .screenMargin()
                } else {
                    tiles(row.items, width: tileWidth)
                }
            }
            .transition(.opacity)
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.25), value: phase)
    }

    private func tiles(_ items: [MediaItem], width: CGFloat) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: Space.s3) {
                ForEach(items, id: \.id) { item in
                    Button { onOpen(item) } label: { ArtworkTile(item: item, captionLines: 2).frame(width: width) }
                        .buttonStyle(PressableButtonStyle())
                }
            }
            .screenMargin()
        }
    }
}

/// The user's libraries as 16:9 tiles in one row at the end of Home; a tile opens that library.
struct MyMediaView: View {
    let state: HomeUiState
    let onOpen: (MediaItem) -> Void
    let onRetry: () -> Void

    private let tileWidth: CGFloat = 200

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Strings.shared.libraries.titleCased())
                .sectionTitle()
                .accessibilityAddTraits(.isHeader)
                .screenMargin()
            if state.loadingMyMedia {
                // Last to come in: grey tiles until every row above has finished.
                PosterSkeletonRow(count: 3, tileWidth: tileWidth, aspectRatio: 16.0 / 9.0, captionLines: 1)
            } else if let message = state.librariesMessage {
                LoadErrorView(message: message, onRetry: onRetry)
                    .screenMargin()
            } else if state.libraries.isEmpty {
                Text(Strings.shared.noLibraries)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .screenMargin()
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: Space.s3) {
                        ForEach(state.libraries, id: \.id) { library in
                            Button { onOpen(library) } label: { ArtworkTile(item: library, captionLines: 1).frame(width: tileWidth) }
                                .buttonStyle(PressableButtonStyle())
                        }
                    }
                    .screenMargin()
                }
            }
        }
    }
}

/// A row's header and posters in grey, for the moment before the rows are known.
struct RowPlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            SkeletonLine(font: .title3.bold(), width: 220)
                .screenMargin()
            PosterSkeletonRow()
        }
    }
}
