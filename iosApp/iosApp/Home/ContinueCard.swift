import SwiftUI
import Shared

/// A 16:9 card in the Continue watching row: the picture with a play disc in the middle and the
/// progress bar along its bottom edge if something is in progress, then the title with what is left (or
/// the episode) under it, close together, and a "…" menu for the card's actions. Tapping the picture
/// plays, with no detail page in between; tapping the words opens the detail page. A long press opens
/// the same actions as the "…".
struct ContinueCardTile: View {
    let card: ContinueCard
    let onPlay: () -> Void
    let onDetails: () -> Void
    let onMarkWatched: () -> Void
    var onRemove: () -> Void = {}
    /// Offline there is no page to open and nothing to send, so the card has no menu.
    var showsMenu = true
    /// The card's own download, if any, and whether the user may start one: the menu offers Download without opening the page.
    var download: Download?
    var canDownload = false
    var onDownload: () -> Void = {}
    var onDownloadAction: (DownloadAction) -> Void = { _ in }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            Button(action: onPlay) {
                Artwork(url: card.imageUrl, title: card.title, aspectRatio: 16.0 / 9.0, blurHash: card.imageBlurHash)
                    .overlay { PlayDisc() }
                    .overlay(alignment: .bottom) {
                        if let progress = card.progress {
                            ProgressBar(progress: CGFloat(progress.floatValue))
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
            }
            .buttonStyle(PressableButtonStyle())
            // One element for VoiceOver: the title, the episode and how far in.
            .accessibilityLabel(card.accessibilityLabel)
            HStack(alignment: .top, spacing: Space.s2) {
                Button(action: onDetails) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(card.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        // Always one line, a space for a movie nobody started, so every card is as tall as the skeleton.
                        Text(card.subtitle ?? " ")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(PressableButtonStyle())
                .accessibilityHidden(true)
                if showsMenu {
                    Menu {
                        menuRows
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .frame(width: 32, height: 24)
                    }
                    .accessibilityLabel(Strings.shared.moreActions)
                }
            }
        }
        .frame(width: Size.thumbWidth, alignment: .leading)
        // Long press on the picture: the same actions as the "…".
        .contextMenu {
            if showsMenu {
                menuRows
            }
        }
    }

    /// The card's options, the same behind the "…" and on a long press.
    @ViewBuilder
    private var menuRows: some View {
        Button(action: onDetails) {
            Label(Strings.shared.details.titleCased(), systemImage: "info.circle")
        }
        Button(action: onMarkWatched) {
            Label(Strings.shared.markWatched.titleCased(), systemImage: "checkmark.circle")
        }
        if card.canRemove {
            Button(action: onRemove) {
                Label(Strings.shared.removeFromContinueWatching.titleCased(), systemImage: "xmark.circle")
            }
        }
        downloadItems
    }

    /// Download: start it, or pause, resume or remove what is there (the same entries as an episode's menu).
    @ViewBuilder
    private var downloadItems: some View {
        if let download {
            DownloadActionButtons(actions: DownloadBridge.shared.actions(download: download), onAction: onDownloadAction)
        } else if canDownload {
            Button(action: onDownload) {
                Label(Strings.shared.download.titleCased(), systemImage: "arrow.down.to.line")
            }
        }
    }
}

/// A round outline with a play triangle over the picture. It says "tap to play"; the card does the playing.
private struct PlayDisc: View {
    var body: some View {
        Image(systemName: "play.fill")
            .font(.title3)
            .foregroundStyle(Brand.onMedia)
            .frame(width: 44, height: 44)
            .background(Brand.scrim.opacity(0.6), in: Circle())
            .overlay(Circle().strokeBorder(Brand.overlayStrong, lineWidth: 1))
            .accessibilityHidden(true)
    }
}

private struct ProgressBar: View {
    let progress: CGFloat

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Brand.progressTrack
                Brand.spark.frame(width: proxy.size.width * min(max(progress, 0), 1))
            }
        }
        .frame(height: 3)
    }
}

/// The Continue watching row: a title over a row of cards that runs to the screen edge. While it
/// loads it shows the same cards as placeholders; if it can't load it says why and offers to try again.
struct ContinueRowView: View {
    let row: ContinueRow
    let onPlay: (String) -> Void
    let onDetails: (String) -> Void
    let onMarkWatched: (String) -> Void
    var onRemove: (String) -> Void = { _ in }
    let onRetry: () -> Void
    var showsMenu = true
    var downloads: [String: Download] = [:]
    var canDownload = false
    var onDownload: (String) -> Void = { _ in }
    var onDownloadAction: (String, DownloadAction) -> Void = { _, _ in }

    var body: some View {
        // Nothing to show (it came back empty): no title and no gap, like any other row.
        if row.isVisible {
            content
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Strings.shared.continueWatching.titleCased())
                .sectionTitle()
                .screenMargin()
            if row.loading {
                ContinueSkeletonRow()
            } else if let message = row.message {
                LoadErrorView(message: message, onRetry: onRetry)
                    .screenMargin()
            } else {
                HorizontalRow {
                    LazyHStack(alignment: .top, spacing: Space.s3) {
                        ForEach(row.cards, id: \.id) { card in
                            ContinueCardTile(
                                card: card,
                                onPlay: { onPlay(card.id) },
                                onDetails: { onDetails(card.id) },
                                onMarkWatched: { onMarkWatched(card.id) },
                                onRemove: { onRemove(card.id) },
                                showsMenu: showsMenu,
                                download: downloads[card.id],
                                canDownload: canDownload,
                                onDownload: { onDownload(card.id) },
                                onDownloadAction: { onDownloadAction(card.id, $0) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/// Stands in for the row while it loads, redacted so nothing moves when the cards arrive. The words under
/// the pictures are never seen, only drawn as grey bars, so they borrow strings that already exist.
struct ContinueSkeletonRow: View {
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        // In a scroll view that never scrolls: the three fixed-width cards are wider than a phone, and
        // laid out on their own they would stretch everything above them past the screen's edge.
        HorizontalRow(lifts: false) {
            HStack(alignment: .top, spacing: Space.s3) {
                ForEach(0..<skeletonCount(3, regularWidth: sizeClass == .regular), id: \.self) { _ in
                    VStack(alignment: .leading, spacing: Space.s2) {
                        Color(.tertiarySystemBackground)
                            .aspectRatio(16.0 / 9.0, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(Strings.shared.continueWatching)
                                .font(.subheadline.weight(.semibold))
                            Text(Strings.shared.play)
                                .font(.caption)
                        }
                    }
                    .frame(width: Size.thumbWidth, alignment: .leading)
                }
            }
            .redacted(reason: .placeholder)
            .skeletonShimmer()
        }
        .scrollDisabled(true)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.loadingContinueWatching)
    }
}
