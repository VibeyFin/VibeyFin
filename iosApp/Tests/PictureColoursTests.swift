import XCTest
@testable import VibeyFin

@MainActor
final class PictureColoursTests: XCTestCase {
    private struct Pixel {
        let red: UInt8
        let green: UInt8
        let blue: UInt8
    }

    private func frame(width: Int, height: Int, pixel: (_ column: Int, _ row: Int) -> Pixel) -> [GlowColour] {
        var bytes: [UInt8] = []
        for row in 0..<height {
            for column in 0..<width {
                let value: Pixel = pixel(column, row)
                bytes += [value.blue, value.green, value.red, 255]
            }
        }
        return bytes.withUnsafeBufferPointer {
            PictureColours.bottomEdge(bgra: $0.baseAddress!, width: width, height: height, bytesPerRow: width * 4)
        }
    }

    func testTheColumnsAreAveragedLeftToRight() {
        let colours: [GlowColour] = frame(width: 40, height: 40) { column, _ in
            column < 20 ? Pixel(red: 200, green: 0, blue: 0) : Pixel(red: 0, green: 0, blue: 200)
        }
        XCTAssertEqual(colours.count, PictureColours.stopCount)
        XCTAssertGreaterThan(colours[0].red, 0.7)
        XCTAssertGreaterThan(colours[1].red, 0.7)
        XCTAssertGreaterThan(colours[2].blue, 0.7)
        XCTAssertGreaterThan(colours[3].blue, 0.7)
        XCTAssertEqual(colours[0].blue, 0, accuracy: 0.0001)
    }

    func testOnlyTheLowerThirdCounts() {
        let colours: [GlowColour] = frame(width: 40, height: 40) { _, row in
            row < 20 ? Pixel(red: 0, green: 200, blue: 0) : Pixel(red: 200, green: 0, blue: 0)
        }
        XCTAssertEqual(colours[0].green, 0, accuracy: 0.0001)
        XCTAssertGreaterThan(colours[0].red, 0.7)
    }

    func testBlackBarsAtTheBottomAreSkipped() {
        let colours: [GlowColour] = frame(width: 40, height: 40) { _, row in
            row >= 36 ? Pixel(red: 0, green: 0, blue: 0) : Pixel(red: 0, green: 180, blue: 0)
        }
        XCTAssertGreaterThan(colours[0].green, 0.6)
    }

    func testAnAllBlackFrameGivesBlack() {
        let colours: [GlowColour] = frame(width: 40, height: 40) { _, _ in Pixel(red: 0, green: 0, blue: 0) }
        XCTAssertEqual(colours.map { $0.red + $0.green + $0.blue }, [0, 0, 0, 0])
    }

    func testBrightnessIsCappedAndTheHueKept() {
        let colours: [GlowColour] = frame(width: 40, height: 40) { _, _ in Pixel(red: 255, green: 128, blue: 0) }
        XCTAssertEqual(colours[0].red, 0.85, accuracy: 0.001)
        XCTAssertEqual(colours[0].green / colours[0].red, 128.0 / 255.0, accuracy: 0.01)
    }

    func testAFrameThatIsTooNarrowGivesNoColours() {
        XCTAssertEqual(frame(width: 2, height: 10) { _, _ in Pixel(red: 9, green: 9, blue: 9) }, [])
    }

    func testSmallChangesAreNotWorthACrossfade() {
        let base: [GlowColour] = Array(repeating: GlowColour(red: 0.5, green: 0.4, blue: 0.3), count: 4)
        let nearly: [GlowColour] = Array(repeating: GlowColour(red: 0.51, green: 0.4, blue: 0.3), count: 4)
        let other: [GlowColour] = Array(repeating: GlowColour(red: 0.2, green: 0.4, blue: 0.3), count: 4)
        XCTAssertTrue(PictureColours.differs(base, from: nil))
        XCTAssertFalse(PictureColours.differs(nearly, from: base))
        XCTAssertTrue(PictureColours.differs(other, from: base))
    }

    func testWithoutASourceThereIsNoGlow() async {
        let sampler = PictureColourSampler { nil }
        let task = Task { await sampler.run(oneShot: false) }
        try? await Task.sleep(for: .milliseconds(100))
        task.cancel()
        await task.value
        XCTAssertNil(sampler.glow)
    }
}
