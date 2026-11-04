import XCTest
@testable import VibeyFin

final class OrientationLockTests: XCTestCase {
    func testAnIPhoneStaysUprightOutsideThePlayer() {
        XCTAssertEqual(OrientationLock.defaultMask(for: .phone), .portrait)
    }

    func testAnIPadTurnsFreely() {
        XCTAssertEqual(OrientationLock.defaultMask(for: .pad), .all)
    }
}
