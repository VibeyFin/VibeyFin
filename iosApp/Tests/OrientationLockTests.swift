import XCTest
@testable import VibeyFin

final class OrientationLockTests: XCTestCase {
    func testACompactWindowStaysUprightOutsideThePlayer() {
        XCTAssertEqual(OrientationLock.defaultMask(horizontal: .compact, vertical: .regular), .portrait)
    }

    func testAWindowThatIsRegularOnlyAcrossStaysUpright() {
        XCTAssertEqual(OrientationLock.defaultMask(horizontal: .regular, vertical: .compact), .portrait)
    }

    func testAWindowThatIsRegularBothWaysTurnsFreely() {
        XCTAssertEqual(OrientationLock.defaultMask(horizontal: .regular, vertical: .regular), .all)
    }

    func testThePlayerKeepsACompactWindowInLandscape() {
        XCTAssertEqual(OrientationLock.playerMask(horizontal: .compact, vertical: .regular), .landscape)
        XCTAssertEqual(OrientationLock.playerMask(horizontal: .compact, vertical: .compact), .landscape)
    }

    func testThePlayerLetsAWindowThatIsRegularBothWaysFollowTheDevice() {
        XCTAssertEqual(OrientationLock.playerMask(horizontal: .regular, vertical: .regular), .all)
    }
}
