import SwiftUI

/// The sizes of the tabletop control surface, kept apart from the views so they can be tested.
enum TabletopMetrics {
    static let inset: CGFloat              = 20
    static let gap: CGFloat                = 12
    static let tightGap: CGFloat           = 8
    static let titleRowHeight: CGFloat     = 44
    static let transportHeight: CGFloat    = 72
    static let scrubberHeight: CGFloat     = 60
    static let secondaryHeight: CGFloat    = 64
    static let skipOfferHeight: CGFloat    = 28
    static let playWidthRatio: CGFloat     = 1.6
    static let closeRadius: CGFloat        = 10
    static let transportRadius: CGFloat    = 14
    static let secondaryRadius: CGFloat    = 12

    /// The height the stack needs, insets included, for a given gap.
    static func requiredHeight(gap: CGFloat, hasSkipOffer: Bool) -> CGFloat {
        let rows: [CGFloat] = [titleRowHeight, transportHeight, scrubberHeight, secondaryHeight] + (hasSkipOffer ? [skipOfferHeight] : [])
        let gaps: CGFloat   = CGFloat(rows.count - 1) * gap
        return rows.reduce(0, +) + gaps + inset * 2
    }

    /// The gaps tighten before anything else gives, and tiles never shrink.
    static func rowGap(availableHeight: CGFloat, hasSkipOffer: Bool) -> CGFloat {
        requiredHeight(gap: gap, hasSkipOffer: hasSkipOffer) <= availableHeight ? gap : tightGap
    }

    /// Skip back and forward are equal; play is `playWidthRatio` times as wide; the three fill the row.
    static func transportWidths(available: CGFloat, gap: CGFloat) -> (side: CGFloat, play: CGFloat) {
        let usable: CGFloat = max(available - gap * 2, 0)
        let side: CGFloat   = usable / (2 + playWidthRatio)
        return (side, side * playWidthRatio)
    }
}
