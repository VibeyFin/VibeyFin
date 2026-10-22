import SwiftUI
import Shared

/// The phone's hero: the backdrop full-bleed with the title, the facts, the overview and Play laid over it, centred. The text sits
/// on the scrim: a band at the top for the bar, clear through the middle so the picture reads, then darker towards the bottom
/// where the text is, ending in the page's own colour. (A regular width lays the page out differently: see `WideItemBackdrop`.)
struct ItemHeroView: View {
    let item: MediaItem
    let state: ItemUiState
    let onPlay: (Bool) -> Void
    let onOpenSeries: (String) -> Void
    let onToggleFavorite: () -> Void
    let onPlayTrailer: (String) -> Void
    let onTogglePlayed: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            if let url = item.backdropUrl ?? item.posterUrl {
                Artwork(url: url, title: item.title, aspectRatio: 2.0 / 3.0, blurHash: item.backdropUrl != nil ? item.backdropBlurHash : item.posterBlurHash)
            } else {
                // Nothing to show behind the title: a plain tile, shorter than a picture would be, without the title written twice.
                Color(.tertiarySystemBackground)
                    .aspectRatio(1.1, contentMode: .fit)
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
            HeroDetailsView(
                item: item,
                state: state,
                centered: true,
                onPlay: onPlay,
                onOpenSeries: onOpenSeries,
                onToggleFavorite: onToggleFavorite,
                onPlayTrailer: onPlayTrailer,
                onTogglePlayed: onTogglePlayed
            )
            .padding(.horizontal, Space.s4)
            .padding(.bottom, Space.s4)
        }
        .frame(maxWidth: .infinity)
        .clipped()
    }
}

/// A regular width's backdrop: the picture fills the whole screen and stays put while the page scrolls over it. It darkens as the
/// page scrolls (`progress` from 0 at the top to 1 once the first screen is gone), so what scrolls up reads on it. A scrim from the
/// left sits behind the first screen's column of text, and a band at the top behind the back button.
struct WideItemBackdrop: View {
    let item: MediaItem
    let progress: CGFloat

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                if let url = item.backdropUrl ?? item.posterUrl {
                    Artwork(
                        url: url,
                        title: item.title,
                        aspectRatio: proxy.size.width / max(proxy.size.height, 1),
                        blurHash: item.backdropUrl != nil ? item.backdropBlurHash : item.posterBlurHash
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
                Color(.systemBackground).opacity(0.94 * Double(min(max(progress, 0), 1)))
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

    @State private var overviewExpanded = false
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
                Text(item.genres.joined(separator: "  ·  "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(centered ? .center : .leading)
            }
            if let overview = item.overview {
                overviewText(overview)
            }
            if state.canPlay {
                playButtons
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
                Text(state.facts.joined(separator: "  ·  "))
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

    /// Three lines of the overview and "More" when it is long; "Less" folds it back.
    private func overviewText(_ overview: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(overview)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .lineLimit(overviewExpanded ? nil : 3)
                .frame(maxWidth: .infinity, alignment: .leading)
            // A rough test for "has more than three lines"; the text itself is cut by the line limit either way.
            if overview.count > 140 {
                Button((overviewExpanded ? Strings.shared.less : Strings.shared.more).titleCased()) {
                    overviewExpanded.toggle()
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
        }
    }

    /// The one filled action on the page: the aqua capsule, "Play", "Resume · 38m left" or for a show "Play S2 E4", with a round
    /// "start over" arrow beside it only when there is something to resume.
    private var playButtons: some View {
        HStack(spacing: Space.s3) {
            PrimaryButton(action: { onPlay(false) }) {
                Label(state.playLabel.titleCased(), systemImage: "play.fill")
            }
            .frame(maxWidth: centered ? .infinity : 260)
            if state.canStartOver {
                IconSecondaryButton(systemImage: "arrow.counterclockwise", label: Strings.shared.startOver) {
                    onPlay(true)
                }
            }
        }
    }

    /// The actions under Play, four equal columns, each an icon over a label: My List (the server's favourites), Trailer (only when
    /// there is one), Watched (a tick) and Download (it does nothing yet). A trailer the server holds plays in the app; one on the
    /// web opens in the system's viewer.
    private var actions: some View {
        HStack(spacing: 0) {
            actionButton(
                label: Strings.shared.myList,
                description: Strings.shared.myListAction(onList: state.isFavorite),
                systemImage: state.isFavorite ? "checkmark" : "plus",
                action: onToggleFavorite
            )
            if let trailer = state.trailer {
                actionButton(label: Strings.shared.trailer, description: Strings.shared.trailer, systemImage: "film") {
                    if let local = trailer as? TrailerLocal {
                        onPlayTrailer(local.itemId)
                    } else if let remote = trailer as? TrailerRemote, let url = URL(string: remote.url) {
                        openURL(url)
                    }
                }
            } else {
                Color.clear.frame(maxWidth: .infinity, minHeight: 1)
            }
            if state.canMark {
                actionButton(
                    label: Strings.shared.watchedAction,
                    description: state.markLabel,
                    systemImage: state.watched ? "checkmark.circle.fill" : "checkmark.circle",
                    action: onTogglePlayed
                )
            } else {
                Color.clear.frame(maxWidth: .infinity, minHeight: 1)
            }
            // Offline is a later phase: the button is here, and does nothing yet.
            actionButton(label: Strings.shared.download, description: Strings.shared.download, systemImage: "arrow.down.to.line") {}
        }
        .frame(maxWidth: .infinity)
    }

    private func actionButton(label: String, description: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: Space.s1) {
                Image(systemName: systemImage).font(.title2)
                Text(label.titleCased()).font(.caption)
            }
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
        .accessibilityLabel(description)
    }
}
