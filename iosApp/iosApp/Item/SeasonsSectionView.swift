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
            }
        }
        .animation(.easeOut(duration: 0.25), value: seasons.loading)
    }

    private var seasonButtons: some View {
        SeasonButtonsRow(
            seasons: seasons.seasons.map { SeasonButton(id: $0.id, label: $0.seasonLabel) },
            selectedId: seasons.selectedId,
            margin: margin,
            onSelect: onSelect
        )
    }

    private var episodes: some View {
        let list = seasons.episodes
        return EpisodeListArea(
            episodes: list.items,
            id: \.id,
            loading: list.loading,
            message: list.message,
            wide: wide,
            margin: margin,
            focusId: seasons.startEpisode?.id,
            onRetry: onRetryEpisodes,
            row: { episode in
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
            },
            card: { episode, width in
                EpisodeCardView(
                    episode: episode,
                    width: width,
                    current: episode.id == seasons.startEpisode?.id,
                    onPlay: { onPlay(episode.id) },
                    onToggleWatched: { onToggleWatched(episode.id) },
                    onView: { onViewEpisode(episode) },
                    download: downloads[episode.id],
                    canDownload: canDownload,
                    downloadActions: downloadActions
                )
            }
        )
    }
}
