import XCTest
@testable import VibeyFin

final class PlayerStartSeekTests: XCTestCase {
    func testSeeksAgainWhenAirPlayDroppedTheEarlySeek() {
        XCTAssertTrue(AVPlayerEngine.needsStartSeek(start: 600, current: 0, external: true))
        XCTAssertTrue(AVPlayerEngine.needsStartSeek(start: 600, current: .nan, external: true))
    }

    func testLeavesAnEarlySeekThatLanded() {
        XCTAssertFalse(AVPlayerEngine.needsStartSeek(start: 600, current: 600.4, external: true))
    }

    func testNeverSeeksAgainOffAirPlay() {
        XCTAssertFalse(AVPlayerEngine.needsStartSeek(start: 600, current: 0, external: false))
    }

    func testNoSeekWhenStartingFromTheBeginning() {
        XCTAssertFalse(AVPlayerEngine.needsStartSeek(start: 0, current: 0, external: true))
    }
}
