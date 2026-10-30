import SwiftUI
import Shared

/// A show's seasons as a row of capsule buttons (the selected one filled `ink` with `bg` text), then the selected season's
/// episodes. The row opens scrolled to the season Play starts in; another season swaps the list in place and the old list stays
/// up, a little dimmed, until the new one arrives.
struct SeasonsSectionView: View {
    let seasons: SeasonsState
    /// A regular width scrolls the episodes sideways, as bigger cards; a compact one lists them down the page.
    let wide: Bool
    /// The page's side margin: the rows run to the screen's edges and start in line with it.
    let margin: CGFloat
    let onSelect: (String) -> Void
    let onPlay: (String) -> Void
    let onToggleWatched: (String) -> Void
    let onViewEpisode: (MediaItem) -> Void
    let onRetrySeasons: () -> Void
    let onRetryEpisodes: () -> Void
    /// The profile's downloads by item id, whether the server lets the user download, and the ways in to downloads.
    var downloads: [String: Download] = [:]
    var canDownload = false
    var downloadActions = DownloadActions()

    /// The episode area keeps the height of the tallest list it has shown, so choosing a season swaps its content in place and
    /// nothing below it moves.
    @State private var tallest: CGFloat = 0

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            if seasons.loading {
                HStack(spacing: Space.s2) {
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock(cornerRadius: 18).frame(width: 96, height: 36)
                    }
                }
            } else if let message = seasons.message {
                LoadErrorView(message: message, onRetry: onRetrySeasons)
            } else if seasons.seasons.isEmpty {
                Text(Strings.shared.noSeasons)
                    .font(.body)
                    .foregroundStyle(.secondary)
            } else {
                seasonButtons
                episodes
                    .frame(minHeight: tallest, alignment: .top)
                    .background(GeometryReader { proxy in
                        Color.clear.preference(key: EpisodeHeightKey.self, value: proxy.size.height)
                    })
                    .onPreferenceChange(EpisodeHeightKey.self) { height in
                        if !seasons.episodes.loading && height > tallest { tallest = height }
                    }
            }
        }
        .animation(.easeOut(duration: 0.25), value: seasons.loading)
    }

    private var seasonButtons: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s2) {
                    ForEach(seasons.seasons, id: \.id) { season in
                        let selected = season.id == seasons.selectedId
                        Button { onSelect(season.id) } label: {
                            Text(season.seasonLabel)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(selected ? Color(.systemBackground) : Color.primary)
                                .padding(.horizontal, Space.s4)
                                .frame(height: 36)
                                .background(Capsule().fill(selected ? Color.primary : Color(.tertiarySystemBackground)))
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableButtonStyle())
                        .id(season.id)
                        .accessibilityLabel(Strings.shared.seasonDescription(name: season.seasonLabel, selected: selected))
                        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                    }
                }
                .padding(.horizontal, margin)
            }
            .padding(.horizontal, -margin)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Strings.shared.seasonsDescription(count: Int32(seasons.seasons.count)))
            // Opens with the season Play starts in in view.
            .onAppear {
                if let id = seasons.selectedId { proxy.scrollTo(id, anchor: .center) }
            }
        }
    }

    /// Episode cards: 300 wide on a phone, 380 on a regular width.
    private var cardWidth: CGFloat { wide ? 380 : 300 }

    private func scrollToCurrent(_ proxy: ScrollViewProxy, _ list: EpisodeList) {
        let target = list.items.first(where: { $0.id == seasons.startEpisode?.id }) ?? list.items.first
        if let id = target?.id { proxy.scrollTo(id, anchor: .leading) }
    }

    @ViewBuilder
    private var episodes: some View {
        let list = seasons.episodes
        if let message = list.message {
            LoadErrorView(message: message, onRetry: onRetryEpisodes)
        } else if list.items.isEmpty && list.loading && !wide {
            VStack(spacing: Space.s4) {
                ForEach(0..<3, id: \.self) { _ in EpisodeRowSkeleton() }
            }
        } else if list.items.isEmpty && list.loading {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s4) {
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock().frame(width: cardWidth).aspectRatio(1.6, contentMode: .fit)
                    }
                }
                .padding(.horizontal, margin)
            }
            .padding(.horizontal, -margin)
            .scrollDisabled(true)
        } else if list.items.isEmpty {
            Text(Strings.shared.noEpisodes)
                .font(.body)
                .foregroundStyle(.secondary)
        } else if !wide {
            // A phone lists the episodes down the page; the one Play starts is marked.
            VStack(spacing: Space.s4) {
                ForEach(list.items, id: \.id) { episode in
                    EpisodeRowView(
                        episode: episode,
                        current: episode.id == seasons.startEpisode?.id,
                        onPlay: { onPlay(episode.id) },
                        onToggleWatched: { onToggleWatched(episode.id) },
                        onView: { onViewEpisode(episode) },
                        download: downloads[episode.id],
                        canDownload: canDownload,
                        downloadActions: downloadActions
                    )
                }
            }
            .opacity(list.loading ? 0.5 : 1)
        } else {
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: Space.s4) {
                        ForEach(list.items, id: \.id) { episode in
                            EpisodeCardView(
                                episode: episode,
                                width: cardWidth,
                                current: episode.id == seasons.startEpisode?.id,
                                onPlay: { onPlay(episode.id) },
                                onToggleWatched: { onToggleWatched(episode.id) },
                                onView: { onViewEpisode(episode) },
                                download: downloads[episode.id],
                                canDownload: canDownload,
                                downloadActions: downloadActions
                            )
                            .id(episode.id)
                        }
                    }
                    .padding(.horizontal, margin)
                }
                .padding(.horizontal, -margin)
                // A season opens at the episode Play will start, so the one being watched is in view; another season at its first.
                .onAppear { scrollToCurrent(proxy, list) }
                .onChange(of: list.items.first?.id) { _ in scrollToCurrent(proxy, list) }
            }
            .opacity(list.loading ? 0.5 : 1)
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
