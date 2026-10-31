import XCTest
@testable import VibeyFin

final class ExternalPlaybackTests: XCTestCase {
    func testTakesTheFirstNameThatIsThere() {
        XCTAssertEqual(ExternalPlayback.deviceName(fromPortNames: ["Living Room TV", "Kitchen"]), "Living Room TV")
        XCTAssertEqual(ExternalPlayback.deviceName(fromPortNames: ["", "  ", "Bedroom"]), "Bedroom")
    }

    func testTrimsTheName() {
        XCTAssertEqual(ExternalPlayback.deviceName(fromPortNames: ["  Living Room TV \n"]), "Living Room TV")
    }

    func testNoNameMeansNil() {
        XCTAssertNil(ExternalPlayback.deviceName(fromPortNames: []))
        XCTAssertNil(ExternalPlayback.deviceName(fromPortNames: ["", " "]))
    }
}
