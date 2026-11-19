import XCTest
@testable import VibeyFin

final class PlayerInsetsTests: XCTestCase {
    func testTheLargerSideWinsWhenTheStripIsOnTheLeft() {
        XCTAssertEqual(PlayerInsets.symmetric(leading: 62, trailing: 0), 62)
    }

    func testTheLargerSideWinsWhenTheStripIsOnTheRight() {
        XCTAssertEqual(PlayerInsets.symmetric(leading: 0, trailing: 48), 48)
    }

    func testEqualSidesStayAsTheyAre() {
        XCTAssertEqual(PlayerInsets.symmetric(leading: 59, trailing: 59), 59)
    }
}
