import SwiftUI
import UIKit
import Shared

/// Artwork at `aspectRatio`, or the title on a plain `surface-2` tile when there is none. While the image
/// loads, or if it fails, the tile shows `blurHash` (the server's blurred stand-in for the picture) and the
/// picture fades in over it; without a hash the tile's own colour shows.
struct Artwork: View {
    let url: String?
    let title: String
    let aspectRatio: CGFloat
    var blurHash: String? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// `dur-base` in `design/tokens.json`; no fade when the system asks for Reduce Motion.
    private var fade: Animation? { reduceMotion ? nil : .easeIn(duration: 0.2) }

    var body: some View {
        // The colour sets the size from the aspect ratio; the image fills it and is cropped to it.
        Color(.tertiarySystemBackground)
            .aspectRatio(aspectRatio, contentMode: .fit)
            .overlay {
                if let url = url.flatMap(URL.init(string:)) {
                    ZStack {
                        if let blurHash, let blurred = BlurHashCache.image(for: blurHash, aspectRatio: aspectRatio) {
                            Image(uiImage: blurred)
                                .resizable()
                                .interpolation(.medium)
                                .scaledToFill()
                        }
                        AsyncImage(url: url, transaction: Transaction(animation: fade)) { phase in
                            if let image = phase.image {
                                image.resizable().scaledToFill().transition(.opacity)
                            } else {
                                Color.clear
                            }
                        }
                    }
                } else {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                        .padding(Space.s2)
                }
            }
            .clipped()
    }
}

/// Decodes each BlurHash once: a grid redraws its tiles often, and the picture only depends on the hash and the shape.
private enum BlurHashCache {
    private static let images = NSCache<NSString, UIImage>()

    static func image(for hash: String, aspectRatio: CGFloat) -> UIImage? {
        let key = "\(hash)@\(aspectRatio)" as NSString
        if let cached = images.object(forKey: key) { return cached }
        guard let image = BlurHashImages.shared.image(hash: hash, aspectRatio: Float(aspectRatio)) else { return nil }
        images.setObject(image, forKey: key)
        return image
    }
}

/// A poster or a library tile with its title underneath, unless the picture already says it.
struct ArtworkTile: View {
    let item: MediaItem
    /// In a row that scrolls sideways: the caption always takes this many lines, so a long title in one tile never makes
    /// the row taller (and pushes everything under it down) when it scrolls into view. 0 leaves it as it falls.
    var captionLines = 0

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            Artwork(
                url: item.posterUrl,
                title: item.title,
                aspectRatio: CGFloat(item.posterAspectRatio),
                blurHash: item.posterBlurHash
            )
                .overlay(alignment: .topTrailing) {
                    if item.played { WatchedBadge().padding(Space.s2) }
                }
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
            if !item.nameIsOnPoster || captionLines > 0 {
                // A tile whose picture already says its name keeps the same empty space, so the row stays one height.
                Text(item.nameIsOnPoster ? "" : item.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(captionLines > 0 ? captionLines : 2, reservesSpace: captionLines > 0)
                    .multilineTextAlignment(.leading)
            }
        }
        // One element for VoiceOver: the title and year, whether the title is written under the
        // picture or on it.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            item.played
                ? Strings.shared.cardLabel(title: item.title, year: item.year) + ", " + Strings.shared.watched
                : Strings.shared.cardLabel(title: item.title, year: item.year)
        )
        .accessibilityAddTraits(.isButton)
    }
}

/// The check on a watched poster's top-right corner, on the scrim so it reads over any picture.
/// VoiceOver reads the tile's own label, so the badge itself is hidden from it.
struct WatchedBadge: View {
    var body: some View {
        Image(systemName: "checkmark")
            .font(.caption.weight(.bold))
            .foregroundStyle(.white)
            .frame(width: 24, height: 24)
            .background(Brand.scrim, in: Circle())
            .accessibilityHidden(true)
    }
}

/// A grid of tiles; each opens through `onOpen`. Tiles near the end tell `onNearEnd` they appeared.
struct ArtworkGrid: View {
    let items: [MediaItem]
    let minimumWidth: CGFloat
    let onOpen: (MediaItem) -> Void
    var onNearEnd: () -> Void = {}
    /// Ask for the next page when one of this many last items scrolls into view.
    private let loadAhead = 6

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: minimumWidth), spacing: Space.s3, alignment: .top)],
            alignment: .leading,
            spacing: Space.s4
        ) {
            ForEach(items, id: \.id) { item in
                Button { onOpen(item) } label: { ArtworkTile(item: item) }
                    .buttonStyle(PressableButtonStyle())
                    .onAppear {
                        if items.suffix(loadAhead).contains(where: { $0.id == item.id }) { onNearEnd() }
                    }
            }
        }
    }
}

struct LoadingIndicator: View {
    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .frame(maxWidth: .infinity, minHeight: 120)
    }
}

/// What went wrong and a way to try again. The button is the grey secondary: aqua is for the primary action.
struct LoadErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(message)
                .font(.body)
                .foregroundStyle(.primary)
            SecondaryButton(title: Strings.shared.tryAgain, action: onRetry)
                .frame(maxWidth: 200)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
