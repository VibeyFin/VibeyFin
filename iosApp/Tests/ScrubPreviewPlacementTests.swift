import XCTest
@testable import VibeyFin

final class ScrubPreviewPlacementTests: XCTestCase {
    func testThePreviewIsCentredOnTheThumb() {
        XCTAssertEqual(ScrubPreviewPlacement.x(barWidth: 800, previewWidth: 160, fraction: 0.5), 320)
    }

    func testThePreviewStaysInsideTheBarAtTheStart() {
        XCTAssertEqual(ScrubPreviewPlacement.x(barWidth: 800, previewWidth: 160, fraction: 0), 0)
    }

    func testThePreviewStaysInsideTheBarAtTheEnd() {
        XCTAssertEqual(ScrubPreviewPlacement.x(barWidth: 800, previewWidth: 160, fraction: 1), 640)
    }

    func testAPreviewWiderThanTheBarSitsAtTheStart() {
        XCTAssertEqual(ScrubPreviewPlacement.x(barWidth: 100, previewWidth: 160, fraction: 0.5), 0)
    }
}
