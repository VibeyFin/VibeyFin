import XCTest
@testable import VibeyFin

final class SeekSideTests: XCTestCase {
    func testTheLeftThirdSkipsBack() {
        XCTAssertEqual(SeekSide.at(x: 10, width: 300), .back)
    }

    func testTheRightThirdSkipsForward() {
        XCTAssertEqual(SeekSide.at(x: 290, width: 300), .forward)
    }

    func testTheMiddleThirdDoesNotSkip() {
        XCTAssertNil(SeekSide.at(x: 150, width: 300))
    }

    func testNoWidthDoesNotSkip() {
        XCTAssertNil(SeekSide.at(x: 0, width: 0))
    }
}
