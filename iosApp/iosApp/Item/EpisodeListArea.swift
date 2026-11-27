import SwiftUI
import Shared

/// The chosen season's episodes, as the library's page and Seerr's draw them: grey rows or cards while they load, why they didn't,
/// "no episodes", then the list: down the page on a phone, sideways as cards on a regular width. Choosing another season swaps the
/// content in place: the area keeps the height of the list it showed until the new one has loaded, and the old list stays up, a little dimmed, until the
/// new one arrives.
struct EpisodeListArea<Episode, ID: Hashable, Row: View, Card: View>: View {
    let episodes: [Episode]
    let id: KeyPath<Episode, ID>
    let loading: Bool
    let message: String?
    let wide: Bool
    let margin: CGFloat
    /// A made-up episode the row and the card are drawn with, as a skeleton, while the list loads.
    let placeholder: Episode
    /// How many episodes the season has, once known, so the skeleton has as many.
    var knownCount: Int?
    /// The episode a wide list opens at; the first when nil or not in the list.
    var focusId: ID?
    let onRetry: () -> Void
    @ViewBuilder let row: (Episode) -> Row
    @ViewBuilder let card: (Episode, CGFloat) -> Card

    @State private var settledHeight: CGFloat = 0

    private static var phonePlaceholderCount: Int { 3 }

    /// Episode cards: 300 wide on a phone, 380 on a regular width.
    private var cardWidth: CGFloat { wide ? 380 : 300 }

    var body: some View {
        content
            .background(GeometryReader { proxy in
                Color.clear.preference(key: EpisodeHeightKey.self, value: proxy.size.height)
            })
            .frame(minHeight: settledHeight, alignment: .top)
            .onPreferenceChange(EpisodeHeightKey.self) { height in
                if !loading { settledHeight = height }
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
                ForEach(0..<(knownCount ?? Self.phonePlaceholderCount), id: \.self) { _ in row(placeholder) }
            }
            .skeleton()
        } else if episodes.isEmpty && loading {
            SkeletonRow(tileWidth: cardWidth, spacing: Space.s4, known: knownCount) { card(placeholder, cardWidth) }
                .padding(.horizontal, -margin)
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
