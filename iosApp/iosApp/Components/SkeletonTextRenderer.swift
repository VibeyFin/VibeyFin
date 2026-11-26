import SwiftUI

/// The share of a line a skeleton bar covers, and where the bar sits in it.
enum SkeletonBar {
    static let minimumWidthFraction: CGFloat = 0.55
    static let insetFraction: CGFloat        = 0.2
    static let cornerRadius: CGFloat         = 4

    private static let hashMultiplier: Int = 31
    private static let hashBuckets: Int    = 1000

    /// The same line always gets the same share, between `minimumWidthFraction` and 1, so a skeleton does not change shape while it shimmers.
    static func widthFraction(for line: CGRect) -> CGFloat {
        let seed: Int   = (Int(line.minY) * hashMultiplier + Int(line.minX)) * hashMultiplier + Int(line.maxX)
        let bucket: Int = ((seed % hashBuckets) + hashBuckets) % hashBuckets

        return minimumWidthFraction + (1 - minimumWidthFraction) * CGFloat(bucket) / CGFloat(hashBuckets)
    }

    /// The bar drawn in place of [line]: inset from its top and bottom, a varied share of its width, kept centered when the line is.
    static func bar(for line: CGRect) -> CGRect {
        let inset: CGFloat = line.height * insetFraction
        let width: CGFloat = line.width * widthFraction(for: line)
        let left: CGFloat  = line.minX <= 0 ? line.minX : line.minX + (line.width - width) / 2

        return CGRect(x: left, y: line.minY + inset, width: width, height: line.height - inset * 2)
    }
}

/// Draws each line of a Text as one grey bar, so the real layout sets the height and the bars differ in length.
struct SkeletonTextRenderer: TextRenderer {
    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        for line in layout {
            let lineBounds: CGRect = line.typographicBounds.rect

            guard lineBounds.width > 0 else { continue }

            let bar: CGRect = SkeletonBar.bar(for: lineBounds)

            context.fill(Path(roundedRect: bar, cornerRadius: SkeletonBar.cornerRadius), with: .color(Color(.tertiarySystemFill)))
        }
    }
}
