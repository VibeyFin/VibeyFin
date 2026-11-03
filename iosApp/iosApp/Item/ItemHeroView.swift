import SwiftUI
import Shared

/// The phone's hero: the title, the facts, the overview and Play, centred at the bottom of a first screen as tall as the picture
/// behind it (`PinnedItemBackdrop`), or taller when the text needs the room so nothing is cut off. The page scrolls this text up over
/// the picture. (A regular width lays the page out differently: see `WideItemBackdrop`.)
struct ItemHeroView: View {
    let item: MediaItem
    let state: ItemUiState
    /// At least the picture's height.
    let minHeight: CGFloat
    let onPlay: (Bool) -> Void
    let onOpenSeries: (String) -> Void
    let onToggleFavorite: () -> Void
    let onPlayTrailer: (String) -> Void
    let onTogglePlayed: () -> Void
    let downloads: DownloadActions

    var body: some View {
        HeroDetailsView(
            item: item,
            state: state,
            centered: true,
            onPlay: onPlay,
            onOpenSeries: onOpenSeries,
            onToggleFavorite: onToggleFavorite,
            onPlayTrailer: onPlayTrailer,
            onTogglePlayed: onTogglePlayed,
            downloads: downloads
        )
        .padding(.horizontal, Space.s4)
        .padding(.bottom, Space.s4)
        .frame(maxWidth: .infinity, minHeight: minHeight, alignment: .bottom)
    }
}

enum ScrollDarkening {
    static let darkenOver: CGFloat = 0.55
    static let maxDarken = 0.94

    static func opacity(progress: CGFloat) -> Double { maxDarken * Double(min(max(progress, 0), 1)) }

    static func opacity(scrolled: CGFloat, height: CGFloat) -> Double {
        height > 0 ? opacity(progress: scrolled / (height * darkenOver)) : 0
    }
}

/// A phone's picture: pinned at the top, `height` tall (2:3), and staying put while the page scrolls: the text goes up over it, and it
/// darkens (`progress`, 0 at the top to 1 once about half of it has scrolled by) so what scrolls over it reads. The scrim is a band at
/// the top for the bar, clear through the middle so the picture reads, then darker towards the text, ending in the page's own colour.
struct PinnedItemBackdrop: View {
    let title: String
    let url: String?
    let blurHash: String?
    let height: CGFloat
    let progress: CGFloat

    init(item: MediaItem, height: CGFloat, progress: CGFloat) {
        self.init(title: item.title, url: item.backdropUrl ?? item.posterUrl, blurHash: item.backdropUrl != nil ? item.backdropBlurHash : item.posterBlurHash, height: height, progress: progress)
    }

    /// For a picture that isn't a library item's, such as a Seerr title's.
    init(title: String, url: String?, blurHash: String?, height: CGFloat, progress: CGFloat) {
        self.title = title
        self.url = url
        self.blurHash = blurHash
        self.height = height
        self.progress = progress
    }

    var body: some View {
        ZStack {
            if let url {
                Artwork(url: url, title: title, aspectRatio: 2.0 / 3.0, blurHash: blurHash)
            } else {
                // Nothing to show behind the title: a plain tile.
                Color(.tertiarySystemBackground)
            }
            LinearGradient(
                stops: [
                    .init(color: Brand.scrim, location: 0),
                    .init(color: .clear, location: 0.18),
                    .init(color: .clear, location: 0.40),
                    .init(color: Brand.scrim, location: 0.60),
                    .init(color: Brand.scrimStrong, location: 0.82),
                    .init(color: Color(.systemBackground), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            // The darkening: from the picture as it is to nearly the page's own colour.
            Color(.systemBackground).opacity(ScrollDarkening.opacity(progress: progress))
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipped()
    }
}

/// A regular width's backdrop: the picture fills the whole screen and stays put while the page scrolls over it. It darkens as the
/// page scrolls (`progress` from 0 at the top to 1 once the first screen is gone), so what scrolls up reads on it. A scrim from the
/// left sits behind the first screen's column of text, and a band at the top behind the back button.
struct WideItemBackdrop: View {
    let title: String
    let url: String?
    let blurHash: String?
    let progress: CGFloat

    init(item: MediaItem, progress: CGFloat) {
        self.init(title: item.title, url: item.backdropUrl ?? item.posterUrl, blurHash: item.backdropUrl != nil ? item.backdropBlurHash : item.posterBlurHash, progress: progress)
    }

    /// For a picture that isn't a library item's, such as a Seerr title's.
    init(title: String, url: String?, blurHash: String?, progress: CGFloat) {
        self.title = title
        self.url = url
        self.blurHash = blurHash
        self.progress = progress
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                if let url {
                    Artwork(
                        url: url,
                        title: title,
                        aspectRatio: proxy.size.width / max(proxy.size.height, 1),
                        blurHash: blurHash
                    )
                } else {
                    Color(.tertiarySystemBackground)
                }
                LinearGradient(
                    stops: [.init(color: Brand.scrimStrong, location: 0), .init(color: Brand.scrim, location: 0.3), .init(color: .clear, location: 0.65)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                LinearGradient(
                    stops: [.init(color: Brand.scrim, location: 0), .init(color: .clear, location: 0.16), .init(color: .clear, location: 0.6), .init(color: Brand.scrim, location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                // The darkening: from the picture as it is to nearly the page's own colour, as the page scrolls up over it.
                Color(.systemBackground).opacity(ScrollDarkening.opacity(progress: progress))
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .clipped()
        }
        .ignoresSafeArea()
    }
}

/// The hero's content: the show it belongs to, the title, the facts, the overview, Play and the actions. Centred on a phone, at the
/// start on a regular width.
struct HeroDetailsView: View {
    let item: MediaItem
    let state: ItemUiState
    let centered: Bool
    let onPlay: (Bool) -> Void
    let onOpenSeries: (String) -> Void
    let onToggleFavorite: () -> Void
    let onPlayTrailer: (String) -> Void
    let onTogglePlayed: () -> Void
    let downloads: DownloadActions

    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: centered ? .center : .leading, spacing: Space.s3) {
            // An episode or a season leads back to its show.
            if let seriesName = item.seriesName {
                if let seriesId = item.seriesId {
                    Button { onOpenSeries(seriesId) } label: {
                        HStack(spacing: Space.s1) {
                            Text(seriesName)
                            Image(systemName: "chevron.right").font(.caption.weight(.semibold))
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableButtonStyle())
                    .accessibilityLabel(Strings.shared.goToSeries(name: seriesName))
                } else {
                    Text(seriesName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
            titleArt
            facts
            if !item.genres.isEmpty {
                Text(Strings.shared.factsLine(facts: item.genres))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(centered ? .center : .leading)
            }
            if let overview = item.overview {
                ExpandableOverview(text: overview)
            }
            // A show's Play waits for its next episode: a grey button of the same size holds its place, so it does not pop in.
            if state.canPlay {
                playButtons
            } else if state.playLoading {
                SkeletonBlock().frame(maxWidth: .infinity).frame(height: 50).clipShape(Capsule())
            }
            actions
        }
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
    }

    /// The title as its `Logo` image, centred and large; the brand title when the item has none.
    @ViewBuilder
    private var titleArt: some View {
        if let logo = item.logoUrl, let url = URL(string: logo) {
            AsyncImage(url: url) { image in
                image.resizable().scaledToFit()
            } placeholder: {
                Color.clear
            }
            .frame(maxWidth: centered ? 260 : 360, maxHeight: 112)
            .accessibilityLabel(item.title)
        } else {
            Text(item.title)
                .font(.brandDisplay)
                .tracking(-0.8)
                .foregroundStyle(.primary)
                .multilineTextAlignment(centered ? .center : .leading)
                .lineLimit(3)
        }
    }

    @ViewBuilder
    private var facts: some View {
        if !state.facts.isEmpty || state.watched || state.badge != nil {
            HStack(spacing: Space.s2) {
                Text(Strings.shared.factsLine(facts: state.facts))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if let badge = state.badge {
                    FormatBadge(text: badge)
                }
                // Watched is an icon, in the secondary ink: aqua never signals status.
                if state.watched {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(Strings.shared.watched)
                }
            }
        }
    }

    /// The one filled action on the page: the aqua capsule, "Play", "Resume · 38m left" or for a show "Play S2 E4", with a round
    /// "start over" arrow beside it only when there is something to resume.
    /// The label in title case, but only up to the " · ": what follows is a time left ("1m left"), which stays as written.
    private var playLabel: String {
        let parts = state.playLabel.components(separatedBy: " \u{00B7} ")
        return ([parts[0].titleCased()] + parts.dropFirst()).joined(separator: " \u{00B7} ")
    }

    private var playButtons: some View {
        HStack(spacing: Space.s3) {
            PrimaryButton(action: { onPlay(false) }) {
                Label(playLabel, systemImage: "play.fill")
            }
            .frame(maxWidth: centered ? .infinity : 260)
            if state.canStartOver {
                IconSecondaryButton(systemImage: "arrow.counterclockwise", label: Strings.shared.startOver) {
                    onPlay(true)
                }
            }
        }
    }

    /// The actions under Play, each an icon over a label: My List (the server's favourites), Trailer (only when there is one) and
    /// Watched (a tick). Download joins them with offline playback. A trailer the server holds plays in the app; one on the web
    /// opens in the system's viewer. They sit in four places of one width (the widest label's), the first at Play's left edge and
    /// the fourth ending at its right, evenly spread; with fewer (an episode has no trailer) the last places stay empty.
    private var actions: some View {
        ActionRowLayout {
            HeroActionButton(
                label: Strings.shared.myList,
                description: Strings.shared.myListAction(onList: state.isFavorite),
                systemImage: state.isFavorite ? "heart.fill" : "heart",
                pop: state.isFavorite,
                action: onToggleFavorite
            )
            if let trailer = state.trailer {
                HeroActionButton(label: Strings.shared.trailer, description: Strings.shared.trailer, systemImage: "film") {
                    if let local = trailer as? TrailerLocal {
                        onPlayTrailer(local.itemId)
                    } else if let remote = trailer as? TrailerRemote, let url = URL(string: remote.url) {
                        openURL(url)
                    }
                }
            }
            if state.canMark {
                HeroActionButton(
                    label: Strings.shared.watchedAction,
                    description: state.markLabel,
                    systemImage: state.watched ? "checkmark.circle.fill" : "checkmark.circle",
                    pop: state.watched,
                    action: onTogglePlayed
                )
            }
            if state.offersDownload {
                downloadButton
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// Download: the arrow when there is nothing, the ring while it comes in, the check once it is here. A tap starts a movie or an
    /// episode with none; otherwise (and for a show) it opens a menu: pause, resume, remove, and for a show which season or all of it.
    @ViewBuilder
    private var downloadButton: some View {
        let isSeries = item.kind == ItemKind.series
        let face = VStack(spacing: Space.s1) {
            DownloadBadgeView(badge: state.downloadBadge, progress: state.downloadProgress?.floatValue)
                .font(.title2)
                .frame(height: 30)
            Text(state.downloadLabel.titleCased()).font(.caption.weight(.medium))
        }
        .foregroundStyle(.primary)
        .frame(minWidth: 64, maxWidth: .infinity, minHeight: 72)
        .contentShape(Rectangle())
        if !isSeries && state.download == nil {
            Button(action: downloads.download) { face }
                .buttonStyle(PressableButtonStyle())
                .accessibilityLabel(state.downloadDescription)
        } else {
            Menu {
                if isSeries {
                    if let season = state.seasons.seasons.first(where: { $0.id == state.seasons.selectedId }) {
                        Button { downloads.downloadSeason(season.id) } label: {
                            Label(Strings.shared.downloadSeasonNamed(season: season.seasonLabel), systemImage: "arrow.down.to.line")
                        }
                    }
                    Button { downloads.downloadSeries() } label: {
                        Label(Strings.shared.downloadAllEpisodes, systemImage: "arrow.down.to.line")
                    }
                    if !state.seriesDownloads.isEmpty {
                        Button(role: .destructive) { downloads.removeSeries() } label: {
                            Label(Strings.shared.removeDownloadsOf(count: Int32(state.seriesDownloads.count)), systemImage: "trash")
                        }
                    }
                } else if let download = state.download {
                    DownloadActionButtons(actions: DownloadBridge.shared.actions(download: download)) { downloads.action(item.id, $0) }
                }
            } label: { face }
            .accessibilityLabel(state.downloadDescription)
        }
    }
}
