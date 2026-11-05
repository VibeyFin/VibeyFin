import XCTest
@testable import VibeyFin

final class ProgressUpdateThrottleTests: XCTestCase {
    private let start: Date = Date(timeIntervalSince1970: 1_000)

    func testTheFirstUpdateGoesThrough() {
        var throttle = ProgressUpdateThrottle(interval: 1)

        XCTAssertTrue(throttle.allows(title: "Downloading Heat", at: start))
    }

    func testUpdatesWithinTheIntervalAreHeldBack() {
        var throttle = ProgressUpdateThrottle(interval: 1)
        _ = throttle.allows(title: "Downloading Heat", at: start)

        XCTAssertFalse(throttle.allows(title: "Downloading Heat", at: start.addingTimeInterval(0.4)))
        XCTAssertFalse(throttle.allows(title: "Downloading Heat", at: start.addingTimeInterval(0.9)))
    }

    func testAnUpdateAfterTheIntervalGoesThrough() {
        var throttle = ProgressUpdateThrottle(interval: 1)
        _ = throttle.allows(title: "Downloading Heat", at: start)

        XCTAssertTrue(throttle.allows(title: "Downloading Heat", at: start.addingTimeInterval(1.1)))
    }

    func testANewTitleGoesThroughAtOnce() {
        var throttle = ProgressUpdateThrottle(interval: 1)
        _ = throttle.allows(title: "Downloading Heat", at: start)

        XCTAssertTrue(throttle.allows(title: "Downloading Ronin", at: start.addingTimeInterval(0.1)))
    }

    func testAHeldBackUpdateDoesNotRestartTheWait() {
        var throttle = ProgressUpdateThrottle(interval: 1)
        _ = throttle.allows(title: "Downloading Heat", at: start)
        _ = throttle.allows(title: "Downloading Heat", at: start.addingTimeInterval(0.9))

        XCTAssertTrue(throttle.allows(title: "Downloading Heat", at: start.addingTimeInterval(1.05)))
    }
}
