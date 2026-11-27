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
    var blurHash: String?
    /// False in small thumbnails beside a title: a neutral film symbol stands in, so the title is not written twice.
    var showsTitle = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.redactionReasons) private var redactionReasons: RedactionReasons

    /// `dur-base` in `design/tokens.json`; no fade when the system asks for Reduce Motion.
    private var fade: Animation? { reduceMotion ? nil : .easeIn(duration: 0.14) }

    var body: some View {
        // The colour sets the size from the aspect ratio; the image fills it and is cropped to it.
        Color(.tertiarySystemBackground)
            .aspectRatio(aspectRatio, contentMode: .fit)
            .overlay {
                if redactionReasons.contains(.placeholder) {
                    EmptyView()
                } else if let url = url.flatMap(URL.init(string:)) {
                    // Each picture gets the tile's exact size and is cropped to it. Left to size itself, a picture and its BlurHash
                    // (both larger than the tile) end up offset from each other, and a strip of the blur shows at one edge.
                    GeometryReader { geo in
                        CachedImage(url: url, fade: fade) { image in
                            image.resizable().scaledToFill().frame(width: geo.size.width, height: geo.size.height).clipped()
                        } placeholder: {
                            if let blurHash, let blurred = BlurHashCache.image(for: blurHash, aspectRatio: aspectRatio) {
                                Image(uiImage: blurred)
                                    .resizable()
                                    .interpolation(.medium)
                                    .scaledToFill()
                                    .frame(width: geo.size.width, height: geo.size.height)
                                    .clipped()
                            } else {
                                Color.clear
                            }
                        }
                        .frame(width: geo.size.width, height: geo.size.height)
                    }
                } else if !showsTitle {
                    Image(systemName: "film").font(.title3).foregroundStyle(.tertiary).accessibilityHidden(true)
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

/// Decoded pictures kept in memory. `AsyncImage` keeps none of its own and leans on `URLCache`, whose memory part is too small
/// for backdrops, so a page that scrolls away and back (the hero carousel) would fetch and show its BlurHash all over again.
enum ImageMemoryCache {
    private static let images: NSCache<NSURL, UIImage> = {
        let cache = NSCache<NSURL, UIImage>()
        cache.totalCostLimit = 150 * 1024 * 1024
        return cache
    }()

    static func image(for url: URL) -> UIImage? { images.object(forKey: url as NSURL) }

    static func load(_ url: URL) async -> UIImage? {
        if let cached = image(for: url) { return cached }
        guard let (data, response) = try? await URLSession.shared.data(from: url),
              (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) ?? true,
              let image = UIImage(data: data) else { return nil }
        let cost = Int(image.size.width * image.scale * image.size.height * image.scale * 4)
        images.setObject(image, forKey: url as NSURL, cost: cost)
        return image
    }
}

/// Like `AsyncImage`, but a picture already loaded shows at once, with no placeholder and no fade.
struct CachedImage<Content: View, Placeholder: View>: View {
    let url: URL
    var fade: Animation?
    @ViewBuilder let content: (Image) -> Content
    @ViewBuilder let placeholder: () -> Placeholder

    @State private var loaded: UIImage?

    var body: some View {
        let shown = loaded ?? ImageMemoryCache.image(for: url)
        ZStack {
            placeholder()
            if let shown {
                content(Image(uiImage: shown)).transition(.opacity)
            }
        }
        .task(id: url) {
            guard ImageMemoryCache.image(for: url) == nil else { return }
            let image = await ImageMemoryCache.load(url)
            withAnimation(fade) { loaded = image }
        }
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
                // On iOS 18 the page zooms out of this poster, and back into it.
                .zoomSource(item.id)
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
            .foregroundStyle(Brand.onMedia)
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: minimumWidth), spacing: Space.s3, alignment: .top)],
            alignment: .leading,
            spacing: Space.s4
        ) {
            ForEach(items, id: \.id) { item in
                Button { onOpen(item) } label: { ArtworkTile(item: item) }
                    .tileButton(item)
                    .onAppear {
                        if items.suffix(loadAhead).contains(where: { $0.id == item.id }) { onNearEnd() }
                    }
            }
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.25), value: items.map(\.id))
    }
}

struct LoadingIndicator: View {
    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .frame(maxWidth: .infinity, minHeight: 120)
    }
}

/// What went wrong and a way to try again.
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
