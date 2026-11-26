import SwiftUI
import Shared

extension ItemUiState {
    /// The item page's state with `Placeholders.item` in it, for the hero details to draw as a skeleton.
    static var placeholder: ItemUiState {
        let item: MediaItem = Placeholders.shared.item

        return ItemUiState(
            item: item,
            facts: item.genres,
            loading: false,
            message: nil,
            actionMessage: nil,
            trailer: nil,
            similar: nil,
            similarTiles: [],
            collections: nil,
            extras: nil,
            seasons: SeasonsState(seasons: [], loading: false, message: nil, selectedId: nil, episodes: EpisodeList(items: [], loading: false, message: nil), startEpisode: nil, playSettled: true),
            downloads: [:],
            canDownload: true,
            downloadPrompt: nil,
            removePrompt: nil,
            playout: nil,
            exit: nil,
            canSearchSubtitles: false,
            canDeleteSubtitles: false,
            subtitleDeletePrompt: nil,
            deletingSubtitle: nil,
            notice: nil
        )
    }
}

/// The item page while it loads, laid out like `ItemView`: the picture is a plain block (2:3 pinned at the top of a phone, the whole
/// screen on a regular width) and the hero's details are the real `HeroDetailsView` drawn with placeholder data, with a row of posters
/// under them.
struct ItemSkeleton<Details: View>: View {
    let wide: Bool
    /// The page's side margin, as `ItemView` sets it.
    let margin: CGFloat
    /// The hero's details, centred or not, drawn from placeholder data.
    @ViewBuilder let details: (Bool) -> Details

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .top) {
                picture(width: proxy.size.width, height: proxy.size.height)
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if wide {
                            Color.clear.frame(height: proxy.size.height * 0.3)
                            details(false).skeleton()
                                .frame(maxWidth: min(proxy.size.width * 0.46, 520), alignment: .leading)
                                .padding(.horizontal, margin)
                        } else {
                            details(true).skeleton()
                                .padding(.horizontal, Space.s4)
                                .padding(.bottom, Space.s4)
                                .frame(maxWidth: .infinity, minHeight: phoneHeroHeight(width: proxy.size.width), alignment: .bottom)
                        }
                        VStack(alignment: .leading, spacing: Space.s3) {
                            Text(Placeholders.shared.mediaRow(count: 0).title)
                                .sectionTitle()
                                .lineLimit(1)
                                .padding(.horizontal, margin)
                                .skeleton()
                            SkeletonRow(tileWidth: wide ? 168 : 112) { PosterSkeletonTile() }
                        }
                        .padding(.vertical, Space.s5)
                    }
                }
                .scrollDisabled(true)
            }
            .frame(height: wide ? proxy.size.height : min(phoneHeroHeight(width: proxy.size.width), proxy.size.height))
        }
        .environment(\.screenMarginOverride, wide ? Space.s6 : nil)
        .ignoresSafeArea(edges: wide ? .all : .top)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.loadingPage)
    }

    private func phoneHeroHeight(width: CGFloat) -> CGFloat {
        width / CGFloat(MediaAspect.shared.POSTER)
    }

    /// The backdrop's stand-in, fading into the page's colour where the text starts.
    private func picture(width: CGFloat, height: CGFloat) -> some View {
        ZStack {
            Color(.tertiarySystemBackground).skeletonShimmer()
            LinearGradient(
                stops: wide
                    ? [.init(color: .clear, location: 0.6), .init(color: Color(.systemBackground), location: 1)]
                    : [.init(color: .clear, location: 0.4), .init(color: Color(.systemBackground), location: 1)],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .frame(width: width, height: wide ? height : phoneHeroHeight(width: width))
    }
}

/// A library title's hero details: the real `HeroDetailsView` with `Placeholders.item`.
struct ItemHeroPlaceholder: View {
    let centered: Bool

    var body: some View {
        HeroDetailsView(
            item: Placeholders.shared.item,
            state: .placeholder,
            centered: centered,
            onPlay: { _ in },
            onOpenSeries: { _ in },
            onToggleFavorite: {},
            onPlayTrailer: { _ in },
            onTogglePlayed: {},
            onToggleSeasonPlayed: {},
            downloads: DownloadActions()
        )
    }
}
