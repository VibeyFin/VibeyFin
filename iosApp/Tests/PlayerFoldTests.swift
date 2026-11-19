import XCTest
@testable import VibeyFin

final class PlayerFoldTests: XCTestCase {
    private let screen: CGSize = CGSize(width: 900, height: 640)

    func testAHorizontalFoldSplitsTheVideoFromTheControls() {
        let split: TabletopSplit? = PlayerFold.tabletopSplit(crease: CGRect(x: 0, y: 313, width: 900, height: 14), in: screen)
        XCTAssertEqual(split, TabletopSplit(videoBottom: 313, controlsTop: 327))
    }

    func testAVerticalFoldIsTheBookPoseAndKeepsTheLayout() {
        XCTAssertNil(PlayerFold.tabletopSplit(crease: CGRect(x: 443, y: 0, width: 14, height: 640), in: screen))
    }

    func testNoFoldKeepsTheLayout() {
        XCTAssertNil(PlayerFold.tabletopSplit(crease: nil, in: screen))
    }
}
