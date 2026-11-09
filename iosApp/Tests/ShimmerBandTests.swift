import XCTest
@testable import VibeyFin

final class ShimmerBandTests: XCTestCase {
    func testABlockAtTheLeftEdgeSeesTheBandWhereTheScreenHasIt() throws {
        let points = try XCTUnwrap(ShimmerBand.points(center: 100, half: 50, minX: 0, width: 200))

        XCTAssertEqual(points.start.x, 0.25, accuracy: 0.0001)
        XCTAssertEqual(points.end.x, 0.75, accuracy: 0.0001)
    }

    func testABlockFurtherRightSeesTheSameBandFurtherLeftInItself() throws {
        let points = try XCTUnwrap(ShimmerBand.points(center: 300, half: 50, minX: 200, width: 100))

        XCTAssertEqual(points.start.x, 0.5, accuracy: 0.0001)
        XCTAssertEqual(points.end.x, 1.5, accuracy: 0.0001)
    }

    func testTheBandRunsAcrossTheMiddle() throws {
        let points = try XCTUnwrap(ShimmerBand.points(center: 100, half: 50, minX: 0, width: 200))

        XCTAssertEqual(points.start.y, 0.5)
        XCTAssertEqual(points.end.y, 0.5)
    }

    func testABlockWithNoWidthHasNoBand() {
        XCTAssertNil(ShimmerBand.points(center: 100, half: 50, minX: 0, width: 0))
    }
}
