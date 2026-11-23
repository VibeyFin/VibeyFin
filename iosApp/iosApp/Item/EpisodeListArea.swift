import SwiftUI
import Shared

/// The chosen season's episodes, as the library's page and Seerr's draw them: grey rows or cards while they load, why they didn't,
/// "no episodes", then the list: down the page on a phone, sideways as cards on a regular width. Choosing another season swaps the
/// content in place: the area keeps the height of the tallest list it has shown, and the old list stays up, a little dimmed, until the
/// new one arrives.
struct EpisodeListArea<Episode, ID: Hashable, Row: View, Card: View>: View {
    let episodes: [Episode]
    let id: KeyPath<Episode, ID>
    let loading: Bool
    let message: String?
    let wide: Bool
    let margin: CGFloat
    /// The episode a wide list opens at; the first when nil or not in the list.
    var focusId: ID?
    let onRetry: () -> Void
    @ViewBuilder let row: (Episode) -> Row
    @ViewBuilder let card: (Episode, CGFloat) -> Card

    @State private var tallest: CGFloat = 0

    /// Episode cards: 300 wide on a phone, 380 on a regular width.
    private var cardWidth: CGFloat { wide ? 380 : 300 }

    var body: some View {
        content
            .frame(minHeight: tallest, alignment: .top)
            .background(GeometryReader { proxy in
                Color.clear.preference(key: EpisodeHeightKey.self, value: proxy.size.height)
            })
            .onPreferenceChange(EpisodeHeightKey.self) { height in
                if !loading && height > tallest { tallest = height }
            }
    }

    private func scrollToFocus(_ proxy: ScrollViewProxy) {
        let target = episodes.first(where: { $0[keyPath: id] == focusId }) ?? episodes.first
        if let target { proxy.scrollTo(target[keyPath: id], anchor: .leading) }
    }

    @ViewBuilder
    private var content: some View {
        if let message {
            LoadErrorView(message: message, onRetry: onRetry)
        } else if episodes.isEmpty && loading && !wide {
            VStack(spacing: Space.s4) {
                ForEach(0..<3, id: \.self) { _ in EpisodeRowSkeleton() }
            }
        } else if episodes.isEmpty && loading {
            HorizontalRow(lifts: false) {
                HStack(spacing: Space.s4) {
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock().frame(width: cardWidth).aspectRatio(1.6, contentMode: .fit)
                    }
                }
            }
            .padding(.horizontal, -margin)
            .scrollDisabled(true)
        } else if episodes.isEmpty {
            Text(Strings.shared.noEpisodes)
                .font(.body)
                .foregroundStyle(.secondary)
        } else if !wide {
            VStack(spacing: Space.s4) {
                ForEach(episodes, id: id) { row($0) }
            }
            .opacity(loading ? 0.5 : 1)
        } else {
            ScrollViewReader { proxy in
                HorizontalRow {
                    LazyHStack(alignment: .top, spacing: Space.s4) {
                        ForEach(episodes, id: id) { episode in
                            card(episode, cardWidth).id(episode[keyPath: id])
                        }
                    }
                }
                .padding(.horizontal, -margin)
                // A season opens at the episode Play will start, so the one being watched is in view; another season at its first.
                .onAppear { scrollToFocus(proxy) }
                .onChange(of: episodes.first.map { $0[keyPath: id] }) { scrollToFocus(proxy) }
            }
            .opacity(loading ? 0.5 : 1)
        }
    }
}

private struct EpisodeHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

private struct EpisodeRowSkeleton: View {
    var body: some View {
        HStack(alignment: .top, spacing: Space.s3) {
            SkeletonBlock().frame(width: 148).aspectRatio(1.6, contentMode: .fit)
            VStack(alignment: .leading, spacing: Space.s2) {
                SkeletonLine(font: .headline, width: 140)
                SkeletonLine(font: .caption, width: 90)
                SkeletonLine(font: .subheadline, width: nil)
                SkeletonLine(font: .subheadline, width: 120)
            }
        }
    }
}
