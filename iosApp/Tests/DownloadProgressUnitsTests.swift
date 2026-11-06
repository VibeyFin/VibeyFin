import XCTest
@testable import VibeyFin

final class DownloadProgressUnitsTests: XCTestCase {
    func testPassesDoneAndTotalThrough() {
        let units = DownloadProgressUnits.units(done: 300, total: 1000)

        XCTAssertEqual(units.completed, 300)
        XCTAssertEqual(units.total, 1000)
    }

    func testDoneNeverPassesTotal() {
        let units = DownloadProgressUnits.units(done: 1200, total: 1000)

        XCTAssertEqual(units.completed, 1200)
        XCTAssertEqual(units.total, 1201)
    }

    func testAllBytesInStillLeavesTheBarOpen() {
        let units = DownloadProgressUnits.units(done: 1000, total: 1000)

        XCTAssertEqual(units.completed, 1000)
        XCTAssertEqual(units.total, 1001)
    }

    func testNothingYetStillHasALength() {
        let units = DownloadProgressUnits.units(done: 0, total: 0)

        XCTAssertEqual(units.completed, 0)
        XCTAssertEqual(units.total, 2)
    }

    func testMovingToASmallerVideoNeverShowsAFullBar() {
        let progress = Progress(totalUnitCount: 4_501)
        progress.completedUnitCount = 4_500
        var highestFraction: Double = 0
        let observation = progress.observe(\.fractionCompleted, options: [.new]) { _, change in
            highestFraction = max(highestFraction, change.newValue ?? 0)
        }

        DownloadProgressUnits.apply(DownloadProgressUnits.units(done: 0, total: 500), to: progress)

        observation.invalidate()
        XCTAssertLessThan(highestFraction, 1)
        XCTAssertEqual(progress.totalUnitCount, 500)
        XCTAssertEqual(progress.completedUnitCount, 0)
    }

    func testMovingToALargerVideoNeverShowsAFullBar() {
        let progress = Progress(totalUnitCount: 501)
        progress.completedUnitCount = 500
        var highestFraction: Double = 0
        let observation = progress.observe(\.fractionCompleted, options: [.new]) { _, change in
            highestFraction = max(highestFraction, change.newValue ?? 0)
        }

        DownloadProgressUnits.apply(DownloadProgressUnits.units(done: 100, total: 4_500), to: progress)

        observation.invalidate()
        XCTAssertLessThan(highestFraction, 1)
        XCTAssertEqual(progress.totalUnitCount, 4_500)
        XCTAssertEqual(progress.completedUnitCount, 100)
    }
}
