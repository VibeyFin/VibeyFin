import XCTest
@testable import VibeyFin

final class PlayerTabletopMetricsTests: XCTestCase {
    func testTheGapsStayAtTwelveWhenTheStackFits() {
        let needed: CGFloat = TabletopMetrics.requiredHeight(gap: 12, hasSkipOffer: false)
        XCTAssertEqual(TabletopMetrics.rowGap(availableHeight: needed, hasSkipOffer: false), 12)
    }

    func testTheGapsTightenToEightWhenTheLowerHalfIsShort() {
        let needed: CGFloat = TabletopMetrics.requiredHeight(gap: 12, hasSkipOffer: true)
        XCTAssertEqual(TabletopMetrics.rowGap(availableHeight: needed - 1, hasSkipOffer: true), 8)
    }

    func testTheSkipOfferAddsItsHeightAndOneGap() {
        let without: CGFloat = TabletopMetrics.requiredHeight(gap: 12, hasSkipOffer: false)
        let with: CGFloat    = TabletopMetrics.requiredHeight(gap: 12, hasSkipOffer: true)
        XCTAssertEqual(with - without, TabletopMetrics.skipOfferHeight + 12)
    }

    func testThePlayTileIsOnePointSixTimesASideTileAndTheRowIsFilled() {
        let widths: (side: CGFloat, play: CGFloat) = TabletopMetrics.transportWidths(available: 560, gap: 12)
        XCTAssertEqual(widths.play, widths.side * 1.6, accuracy: 0.001)
        XCTAssertEqual(widths.side * 2 + widths.play + 24, 560, accuracy: 0.001)
    }

    func testANarrowRowNeverGoesNegative() {
        let widths: (side: CGFloat, play: CGFloat) = TabletopMetrics.transportWidths(available: 10, gap: 12)
        XCTAssertEqual(widths.side, 0)
    }
}
