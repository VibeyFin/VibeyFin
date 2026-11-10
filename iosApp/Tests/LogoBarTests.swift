import XCTest
@testable import VibeyFin

final class LogoBarTests: XCTestCase {
    func testNoProgressDrawsTheRestingBar() {
        XCTAssertEqual(LogoBar.fraction(progress: nil), 0.62)
    }

    func testProgressIsClampedToTheBar() {
        XCTAssertEqual(LogoBar.fraction(progress: -1), 0)
        XCTAssertEqual(LogoBar.fraction(progress: 0.4), 0.4)
        XCTAssertEqual(LogoBar.fraction(progress: 3), 1)
    }
}
