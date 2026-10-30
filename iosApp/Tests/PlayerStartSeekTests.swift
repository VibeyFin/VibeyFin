import XCTest
@testable import VibeyFin

final class PlayerStartSeekTests: XCTestCase {
    func testSeeksAgainWhenAirPlayDroppedTheEarlySeek() {
        XCTAssertTrue(PlayerController.needsStartSeek(start: 600, current: 0, external: true))
        XCTAssertTrue(PlayerController.needsStartSeek(start: 600, current: .nan, external: true))
    }

    func testLeavesAnEarlySeekThatLanded() {
        XCTAssertFalse(PlayerController.needsStartSeek(start: 600, current: 600.4, external: true))
    }

    func testNeverSeeksAgainOffAirPlay() {
        XCTAssertFalse(PlayerController.needsStartSeek(start: 600, current: 0, external: false))
    }

    func testNoSeekWhenStartingFromTheBeginning() {
        XCTAssertFalse(PlayerController.needsStartSeek(start: 0, current: 0, external: true))
    }
}
