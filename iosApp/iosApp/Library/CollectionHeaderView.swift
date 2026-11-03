import SwiftUI
import Shared

/// A collection's header: its picture full-bleed with the title, how many titles it holds and the overview over the lower part,
/// fading into the page. A regular width shows a wide picture with the text in a column on the left.
/// As on an item's page, the picture stays put while the page scrolls (`scrollOffset`): the text goes up over it, it darkens, and the
/// rest of the page covers it from below.
struct CollectionHeaderView: View {
    let collection: MediaItem
    let count: Int
    var scrollOffset: CGFloat = 0

    @Environment(\.horizontalSizeClass) private var sizeClass
    private var wide: Bool { sizeClass == .regular }

    var body: some View {
        ZStack(alignment: wide ? .bottomLeading : .bottom) {
            backdrop
                // Moved down as far as the header has gone up, so it holds still; the clip lets the page below cover it.
                .offset(y: scrollOffset)
            VStack(alignment: wide ? .leading : .center, spacing: Space.s2) {
                Text(collection.title)
                    .font(.brandDisplay)
                    .tracking(-0.8)
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(wide ? .leading : .center)
                    .lineLimit(3)
                if count > 0 {
                    Text(Strings.shared.collectionTitles(count: Int32(count)))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                if let overview = collection.overview {
                    Text(overview)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(wide ? .leading : .center)
                        .lineLimit(4)
                }
            }
            .frame(maxWidth: wide ? 520 : .infinity, alignment: wide ? .leading : .center)
            .padding(.horizontal, Space.s4)
            .padding(.bottom, Space.s4)
        }
        .frame(maxWidth: .infinity)
        .clipped()
    }

    private var backdrop: some View {
        ZStack {
            if let url = collection.backdropUrl ?? collection.posterUrl {
                Artwork(
                    url: url,
                    title: collection.title,
                    aspectRatio: wide ? 21.0 / 9.0 : 3.0 / 4.0,
                    blurHash: collection.backdropUrl != nil ? collection.backdropBlurHash : collection.posterBlurHash
                )
            } else {
                Color(.tertiarySystemBackground).aspectRatio(wide ? 21.0 / 9.0 : 3.0 / 4.0, contentMode: .fit)
            }
            if wide {
                LinearGradient(
                    stops: [.init(color: Brand.scrimStrong, location: 0), .init(color: Brand.scrim, location: 0.45), .init(color: .clear, location: 0.8)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            }
            LinearGradient(
                stops: [
                    .init(color: Brand.scrim, location: 0),
                    .init(color: .clear, location: 0.2),
                    .init(color: .clear, location: 0.45),
                    .init(color: Brand.scrim, location: 0.7),
                    .init(color: Brand.scrimStrong, location: 0.88),
                    .init(color: Color(.systemBackground), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            // The darkening as the page scrolls, as on an item's page.
            GeometryReader { proxy in
                Color(.systemBackground).opacity(ScrollDarkening.opacity(scrolled: scrollOffset, height: proxy.size.height))
            }
        }
    }
}
