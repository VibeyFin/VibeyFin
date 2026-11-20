import SwiftUI
import Shared

/// A collection's title, count and overview. The page draws the picture behind it, as the item page does.
struct CollectionHeaderView: View {
    let collection: MediaItem
    let count: Int
    let centered: Bool

    var body: some View {
        VStack(alignment: centered ? .center : .leading, spacing: Space.s2) {
            Text(collection.title)
                .font(.brandDisplay)
                .tracking(-0.4)
                .foregroundStyle(.primary)
                .multilineTextAlignment(centered ? .center : .leading)
                .lineLimit(3)
                .fixedSize(horizontal: false, vertical: true)
            if count > 0 {
                Text(Strings.shared.collectionTitles(count: Int32(count)))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            if let overview = collection.overview {
                Text(overview)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(centered ? .center : .leading)
                    .lineLimit(4)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
    }
}
