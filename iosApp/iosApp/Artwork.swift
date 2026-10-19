import SwiftUI
import Shared

/// Artwork at `aspectRatio`, or the first letter of `title` on a plain tile when there is none. The
/// tile's own colour shows while an image loads or if it fails.
struct Artwork: View {
    let url: String?
    let title: String
    let aspectRatio: CGFloat

    var body: some View {
        // The colour sets the size from the aspect ratio; the image fills it and is cropped to it.
        Palette.surfaceHigh
            .aspectRatio(aspectRatio, contentMode: .fit)
            .overlay {
                if let url = url.flatMap(URL.init(string:)) {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill()
                        } else {
                            Color.clear
                        }
                    }
                } else {
                    Text(String(title.prefix(1)).uppercased())
                        .font(.system(size: 28, weight: .heavy))
                        .foregroundColor(Palette.textMuted)
                }
            }
            .clipped()
    }
}

/// A poster or a library tile with its title underneath, unless the picture already says it.
struct ArtworkTile: View {
    let item: MediaItem

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Artwork(url: item.posterUrl, title: item.title, aspectRatio: CGFloat(item.posterAspectRatio))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            if !item.nameIsOnPoster {
                Text(item.title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Palette.text)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
        }
        // One element for VoiceOver: the title, whether it is written under the picture or on it.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(item.title)
        .accessibilityAddTraits(.isButton)
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
            columns: [GridItem(.adaptive(minimum: minimumWidth), spacing: 16, alignment: .top)],
            alignment: .leading,
            spacing: 20
        ) {
            ForEach(items, id: \.id) { item in
                Button { onOpen(item) } label: { ArtworkTile(item: item) }
                    .buttonStyle(.plain)
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
            .tint(Palette.textMuted)
            .frame(maxWidth: .infinity, minHeight: 120)
    }
}

/// What went wrong and a way to try again. The button is outlined: amber is for the primary action.
struct LoadErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(message)
                .font(.body)
                .foregroundColor(Palette.text)
            Button(Strings.shared.tryAgain, action: onRetry)
                .buttonStyle(OutlineButtonStyle())
                .frame(maxWidth: 200)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
