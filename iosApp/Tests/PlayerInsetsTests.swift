import XCTest
@testable import VibeyFin

final class PlayerInsetsTests: XCTestCase {
    func testTheSmallerSideIsPaddedWhenTheStripIsOnTheLeft() {
        let padding = PlayerInsets.balance(leading: 252, trailing: 0)
        XCTAssertEqual(padding.leading, 0)
        XCTAssertEqual(padding.trailing, 252)
    }

    func testTheSmallerSideIsPaddedWhenTheStripIsOnTheRight() {
        let padding = PlayerInsets.balance(leading: 0, trailing: 144)
        XCTAssertEqual(padding.leading, 144)
        XCTAssertEqual(padding.trailing, 0)
    }

    func testEqualSidesNeedNoPadding() {
        let padding = PlayerInsets.balance(leading: 59, trailing: 59)
        XCTAssertEqual(padding.leading, 0)
        XCTAssertEqual(padding.trailing, 0)
    }

    func testALandscapeFrameSpansTheWidth() {
        XCTAssertTrue(PlayerInsets.spansWidth(size: CGSize(width: 678, height: 466)))
    }

    func testAnUprightFrameKeepsInsideTheSafeArea() {
        XCTAssertFalse(PlayerInsets.spansWidth(size: CGSize(width: 466, height: 678)))
    }

    func testTheCameraRowClearsTheStripInsetWhenTheStripIsTrailing() {
        XCTAssertEqual(PlayerInsets.cameraClearance(at: .bottom, leading: 0, trailing: 84), 84)
        XCTAssertEqual(PlayerInsets.cameraClearance(at: .top, leading: 0, trailing: 84), 0)
    }

    func testTheCameraMovesToTheTopRowWhenTheStripIsLeading() {
        XCTAssertEqual(PlayerInsets.cameraClearance(at: .top, leading: 84, trailing: 0), 84)
        XCTAssertEqual(PlayerInsets.cameraClearance(at: .bottom, leading: 84, trailing: 0), 0)
    }

    func testNeitherRowClearsAnythingWithoutAStrip() {
        XCTAssertEqual(PlayerInsets.cameraClearance(at: .top, leading: 0, trailing: 0), 0)
        XCTAssertEqual(PlayerInsets.cameraClearance(at: .bottom, leading: 0, trailing: 0), 0)
    }

    func testTheInnerDisplayTopRowKeepsOutOfTheStripAndTheBottomSpans() {
        let top = PlayerInsets.rowInsets(at: .top, leading: 0, trailing: 84, regularWidth: true)
        let bottom = PlayerInsets.rowInsets(at: .bottom, leading: 0, trailing: 84, regularWidth: true)
        XCTAssertEqual(top.leading, 0)
        XCTAssertEqual(top.trailing, 84)
        XCTAssertEqual(bottom.leading, 0)
        XCTAssertEqual(bottom.trailing, 0)
    }

    func testTheOuterDisplayRowsClearTheCameraCornerOnBothSides() {
        let bottom = PlayerInsets.rowInsets(at: .bottom, leading: 0, trailing: 84, regularWidth: false)
        XCTAssertEqual(bottom.leading, 84)
        XCTAssertEqual(bottom.trailing, 84)
    }
}
