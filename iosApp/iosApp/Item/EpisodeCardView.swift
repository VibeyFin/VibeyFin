import SwiftUI
import Shared

/// An episode on a tablet: a card in a row that scrolls sideways, all of it on the still. At the bottom, over a scrim: the "S1 E4" tag, the
/// title, two lines of synopsis, and a line with how long it is (or how much is left), its rating and the day it aired. A check
/// sits at the top once watched, a progress bar runs along the bottom edge when it is part watched, and the one Play starts has a
/// brighter outline. Tapping the picture plays it, tapping the words opens its page; a long press opens a menu: View episode, and the watched mark.
struct EpisodeCardView: View {
    let episode: MediaItem
    let width: CGFloat
    let current: Bool
    let onPlay: () -> Void
    let onToggleWatched: () -> Void
    let onView: () -> Void
    var download: Download?
    var canDownload = false
    var downloadActions = DownloadActions()

    private let aspect: CGFloat = 1.6

    var body: some View {
        Button(action: onPlay) {
            Artwork(url: episode.landscapeUrl ?? episode.posterUrl, title: episode.title, aspectRatio: aspect, blurHash: episode.landscapeBlurHash)
                .overlay {
                    LinearGradient(
                        // The picture stays clear well down the card; only the lower part darkens, just enough for the white text to read.
                        stops: [.init(color: .clear, location: 0), .init(color: .clear, location: 0.4), .init(color: Brand.scrim.opacity(0.7), location: 0.75), .init(color: Brand.scrim, location: 1)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .allowsHitTesting(false)
                }
                .overlay(alignment: .topTrailing) {
                    if episode.played {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(Brand.onMedia)
                            .padding(Space.s3)
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
                .frame(width: width)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).stroke(current ? Color.secondary : Color(.tertiarySystemBackground), lineWidth: 1))
                .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
        // The words open the episode's page; the picture above them plays.
        .overlay(alignment: .bottomLeading) {
            Button(action: onView) { caption }
                .buttonStyle(.plain)
                .accessibilityHint(Strings.shared.viewEpisode)
        }
        .overlay(alignment: .bottomTrailing) {
            EpisodeDownloadButton(
                title: episode.title,
                download: download,
                canDownload: canDownload,
                onStart: { downloadActions.downloadEpisode(episode.id) },
                onAction: { downloadActions.action(episode.id, $0) },
                overPicture: true
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

    private var caption: some View {
        VStack(alignment: .leading, spacing: Space.s1) {
            if let tag = Strings.shared.episodeTag(season: episode.seasonNumber, episode: episode.episodeNumber) {
                Text(tag)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(Brand.onMedia)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Brand.scrim))
            }
            Text(episode.title)
                .font(.headline.weight(.bold))
                .foregroundStyle(Brand.onMedia)
                .lineLimit(1)
            // Two lines, always, so every card in the row is the same.
            Text(episode.overview ?? "")
                .font(.subheadline)
                .foregroundStyle(Brand.onMedia)
                .lineLimit(2, reservesSpace: true)
                .multilineTextAlignment(.leading)
            HStack(spacing: Space.s3) {
                if let time = time { Text(time) }
                if let rating = episode.rating?.doubleValue, rating > 0 { Text(Strings.shared.ratingTag(rating: rating)) }
                Spacer(minLength: 0)
                if let date = Strings.shared.releaseDate(iso: episode.premiereDate) {
                    Text(date).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(Brand.onMedia)
            // The facts line stops short of the download button at the bottom-right.
            .padding(.trailing, download != nil || canDownload ? 36 : 0)
        }
        .padding(Space.s3)
        .padding(.bottom, episode.progress != nil ? 4 : 0)
        .frame(width: width, alignment: .leading)
    }

    /// How long it is, or how much is left of it.
    private var time: String? {
        if episode.progress != nil, let runtime = episode.runtimeMs {
            return Strings.shared.timeLeftShort(remainingMs: runtime.int64Value - episode.resumePositionMs)
        }
        return episode.runtimeMinutes.map { Strings.shared.minutesShort(minutes: $0.int32Value) }
    }
}
