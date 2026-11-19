import XCTest
@testable import VibeyFin

final class PlayerHingeGlowTests: XCTestCase {
    func testTheGlowIsAtItsPeakAcrossTheTabletopRange() {
        for angle in [90.0, 110.0, 128.6, 150.0] {
            XCTAssertEqual(HingeGlow.opacity(angleDegrees: angle), HingeGlow.peakOpacity, accuracy: 0.0001)
        }
    }

    func testTheGlowIsOffWhenNearlyClosedOrFlat() {
        XCTAssertEqual(HingeGlow.opacity(angleDegrees: 30), 0)
        XCTAssertEqual(HingeGlow.opacity(angleDegrees: 45), 0)
        XCTAssertEqual(HingeGlow.opacity(angleDegrees: 180), 0)
    }

    func testTheGlowRisesAndFallsLinearlyOutsideThePeakRange() {
        XCTAssertEqual(HingeGlow.share(angleDegrees: 67.5), 0.5, accuracy: 0.0001)
        XCTAssertEqual(HingeGlow.share(angleDegrees: 165), 0.5, accuracy: 0.0001)
        XCTAssertEqual(HingeGlow.opacity(angleDegrees: 170), HingeGlow.peakOpacity / 3, accuracy: 0.0001)
    }
}
