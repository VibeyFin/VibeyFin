import SwiftUI
import Shared

/// An episode on a phone: a row in the list down the page. The picture on the left plays it, with a check on it once watched, a
/// progress bar along its bottom edge when part watched, and a brighter outline on the one Play starts. On the right, the
/// "S1 E4" tag, the title, how long it is (or how much is left), its rating and air date, and two lines of synopsis; those open
/// its page. A long press on either opens the menu: View episode, and the watched mark.
struct EpisodeRowView: View {
    let episode: MediaItem
    let current: Bool
    let onPlay: () -> Void
    let onToggleWatched: () -> Void
    let onView: () -> Void
    var download: Download?
    var canDownload = false
    var downloadActions = DownloadActions()

    private let aspect: CGFloat = 1.6

    var body: some View {
        HStack(alignment: .top, spacing: Space.s3) {
            Button(action: onPlay) {
                Artwork(url: episode.landscapeUrl ?? episode.posterUrl, title: episode.title, aspectRatio: aspect, blurHash: episode.landscapeBlurHash)
                    .overlay(alignment: .topTrailing) {
                        if episode.played {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.headline)
                                .foregroundStyle(Brand.onMedia)
                                .shadow(radius: 2)
                                .padding(Space.s2)
                                .accessibilityLabel(Strings.shared.watched)
                        }
                    }
                    .overlay(alignment: .bottom) {
                        if let progress = episode.progress {
                            GeometryReader { proxy in
                                ZStack(alignment: .leading) {
                                    Rectangle().fill(Brand.progressTrack)
                                    Rectangle().fill(Brand.spark).frame(width: proxy.size.width * CGFloat(progress.floatValue))
                                }
                            }
                            .frame(height: 3)
                        }
                    }
                    .frame(width: 148)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).stroke(current ? Color.secondary : Color(.tertiarySystemBackground), lineWidth: 1))
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableButtonStyle())

            Button(action: onView) {
                VStack(alignment: .leading, spacing: Space.s1) {
                    if let tag = Strings.shared.episodeTag(season: episode.seasonNumber, episode: episode.episodeNumber) {
                        Text(tag).font(.caption.weight(.medium)).foregroundStyle(.secondary)
                    }
                    Text(episode.title)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    if !facts.isEmpty {
                        Text(Strings.shared.factsLine(facts: facts))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    // Two lines, always, so every row is the same height.
                    Text(episode.overview ?? "")
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .lineLimit(2, reservesSpace: true)
                        .multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(Strings.shared.viewEpisode)

            // Each episode has its own download control at the end of its row.
            EpisodeDownloadButton(
                title: episode.title,
                download: download,
                canDownload: canDownload,
                onStart: { downloadActions.downloadEpisode(episode.id) },
                onAction: { downloadActions.action(episode.id, $0) }
            )
        }
        .contextMenu {
            Button(action: onView) {
                Label(Strings.shared.viewEpisode.titleCased(), systemImage: "info.circle")
            }
            Button(action: onToggleWatched) {
                Label(
                    (episode.played ? Strings.shared.markUnwatched : Strings.shared.markWatched).titleCased(),
                    systemImage: episode.played ? "xmark.circle" : "checkmark.circle"
                )
            }
            EpisodeDownloadMenuItems(episodeId: episode.id, download: download, canDownload: canDownload, actions: downloadActions)
        }
    }

    /// How long it is (or how much is left), its rating and the day it aired.
    private var facts: [String] {
        var parts: [String] = []
        if episode.progress != nil, let runtime = episode.runtimeMs {
            parts.append(Strings.shared.timeLeftShort(remainingMs: runtime.int64Value - episode.resumePositionMs))
        } else if let minutes = episode.runtimeMinutes {
            parts.append(Strings.shared.minutesShort(minutes: minutes.int32Value))
        }
        if let rating = episode.rating?.doubleValue, rating > 0 { parts.append(Strings.shared.ratingTag(rating: rating)) }
        if let date = Strings.shared.releaseDate(iso: episode.premiereDate) { parts.append(date) }
        return parts
    }
}
