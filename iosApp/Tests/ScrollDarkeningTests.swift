import XCTest
@testable import VibeyFin

final class ScrollDarkeningTests: XCTestCase {
    func testClearAtTheTop() {
        XCTAssertEqual(ScrollDarkening.opacity(scrolled: 0, height: 400), 0)
    }

    func testHalfwayToFullyDark() {
        XCTAssertEqual(ScrollDarkening.opacity(scrolled: 110, height: 400), 0.47, accuracy: 0.0001)
    }

    func testStopsAtNearlyThePageColour() {
        XCTAssertEqual(ScrollDarkening.opacity(scrolled: 10_000, height: 400), ScrollDarkening.maxDarken)
    }

    func testNoHeightStaysClear() {
        XCTAssertEqual(ScrollDarkening.opacity(scrolled: 50, height: 0), 0)
    }

    func testPullingDownStaysClear() {
        XCTAssertEqual(ScrollDarkening.opacity(progress: -0.3), 0)
    }
}
