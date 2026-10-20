import SwiftUI
import Shared

/// A 16:9 card in the Continue watching row: the picture, the aqua progress bar along its bottom edge
/// if something is in progress, and the title with the episode under it. Tapping plays; there is no
/// detail page in between.
struct ContinueCardTile: View {
    let card: ContinueCard

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            Artwork(url: card.imageUrl, title: card.title, aspectRatio: 16.0 / 9.0)
                .overlay(alignment: .bottom) {
                    if let progress = card.progress {
                        ProgressBar(progress: CGFloat(progress.floatValue))
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
            Text(card.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
            // Always one line, a space for a movie, so every card is as tall as the skeleton.
            Text(card.subtitle ?? " ")
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(width: Size.thumbWidth, alignment: .leading)
        // One element for VoiceOver: the title, the episode and how far in.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(card.accessibilityLabel)
        .accessibilityAddTraits(.isButton)
    }
}

/// Aqua is playback progress, here and in the player. The unfilled part is the `progress-track` token.
private struct ProgressBar: View {
    let progress: CGFloat

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Brand.progressTrack
                Brand.accent.frame(width: proxy.size.width * min(max(progress, 0), 1))
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
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Strings.shared.continueWatching.titleCased())
                .sectionTitle()
                .screenMargin()
                .opacity(row.isVisible ? 1 : 0)
            if !row.isVisible {
                // Nothing to show: the same space, unseen, so the libraries stay where they are.
                ContinueSkeletonRow().hidden()
            } else if row.loading {
                ContinueSkeletonRow()
            } else if let message = row.message {
                LoadErrorView(message: message, onRetry: onRetry)
                    .screenMargin()
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: Space.s3) {
                        ForEach(row.cards, id: \.id) { card in
                            Button { onPlay(card.id) } label: { ContinueCardTile(card: card) }
                                .buttonStyle(.plain)
                        }
                    }
                    .screenMargin()
                }
            }
        }
    }
}

/// Stands in for the row while it loads, redacted so nothing moves when the cards arrive. Not
/// animated; looping motion is an opt-in setting (`brand.md`). The words under the pictures are never
/// seen, only drawn as grey bars, so they borrow strings that already exist.
struct ContinueSkeletonRow: View {
    var body: some View {
        HStack(alignment: .top, spacing: Space.s3) {
            ForEach(0..<3, id: \.self) { _ in
                VStack(alignment: .leading, spacing: Space.s2) {
                    Color(.tertiarySystemBackground)
                        .aspectRatio(16.0 / 9.0, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                    Text(Strings.shared.continueWatching)
                        .font(.subheadline.weight(.semibold))
                    Text(Strings.shared.play)
                        .font(.caption)
                }
                .frame(width: Size.thumbWidth, alignment: .leading)
            }
        }
        .redacted(reason: .placeholder)
        .screenMargin()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.loadingContinueWatching)
    }
}
