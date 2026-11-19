import SwiftUI
import Shared

/// A collection's header: its picture full-bleed with the title, how many titles it holds and the overview over the lower part,
/// fading into the page. A regular width shows a wide picture with the text in a column on the left.
struct CollectionHeaderView: View {
    let collection: MediaItem
    let count: Int
    var scrollOffset: CGFloat = 0

    @Environment(\.horizontalSizeClass) private var sizeClass
    private var wide: Bool { sizeClass == .regular }

    var body: some View {
        ZStack(alignment: wide ? .bottomLeading : .bottom) {
            backdrop
                .offset(y: scrollOffset)
            VStack(alignment: wide ? .leading : .center, spacing: Space.s2) {
                Text(collection.title)
                    .font(.brandDisplay)
                    .tracking(-0.4)
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

    private var backdropRatio: CGFloat { wide ? 21.0 / 9.0 : 3.0 / 4.0 }

    /// The page's width sizes the header; the picture and its fades are laid over it and run out to the screen's edges.
    private var backdrop: some View {
        Color.clear
            .aspectRatio(backdropRatio, contentMode: .fit)
            .overlay {
                GeometryReader { proxy in
                    backdropLayers(ratio: proxy.size.width / max(proxy.size.height, 1))
                        .frame(width: proxy.size.width, height: proxy.size.height)
                        .clipped()
                }
                .ignoresSafeArea(.container, edges: .horizontal)
            }
    }

    private func backdropLayers(ratio: CGFloat) -> some View {
        ZStack {
            if let url = collection.backdropUrl ?? collection.posterUrl {
                Artwork(
                    url: url,
                    title: collection.title,
                    aspectRatio: ratio,
                    blurHash: collection.backdropUrl != nil ? collection.backdropBlurHash : collection.posterBlurHash
                )
            } else {
                Color(.tertiarySystemBackground)
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
            GeometryReader { proxy in
                Color(.systemBackground).opacity(ScrollDarkening.opacity(scrolled: scrollOffset, height: proxy.size.height))
            }
        }
    }
}
