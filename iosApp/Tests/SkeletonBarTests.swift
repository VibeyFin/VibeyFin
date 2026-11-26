import XCTest
@testable import VibeyFin

final class SkeletonBarTests: XCTestCase {
    func testABarIsInsetFromTopAndBottomOfItsLine() {
        let bar: CGRect = SkeletonBar.bar(for: CGRect(x: 0, y: 0, width: 100, height: 20))

        XCTAssertEqual(bar.minY, 4, accuracy: 0.01)
        XCTAssertEqual(bar.maxY, 16, accuracy: 0.01)
    }

    func testABarIsNeverLongerThanItsLineNorShorterThanTheMinimumShare() {
        for index in 0..<40 {
            let line: CGRect = CGRect(x: 0, y: CGFloat(index) * 20, width: 100, height: 20)
            let width: CGFloat = SkeletonBar.bar(for: line).width

            XCTAssertLessThanOrEqual(width, 100)
            XCTAssertGreaterThanOrEqual(width, 55 - 0.01)
        }
    }

    func testLinesOfTheSameLengthGetBarsOfDifferentLengths() {
        let widths: Set<CGFloat> = Set((0..<6).map { SkeletonBar.bar(for: CGRect(x: 0, y: CGFloat($0) * 20, width: 100, height: 20)).width })

        XCTAssertGreaterThan(widths.count, 3)
    }

    func testTheSameLineAlwaysGetsTheSameBar() {
        let line: CGRect = CGRect(x: 0, y: 40, width: 100, height: 20)

        XCTAssertEqual(SkeletonBar.bar(for: line), SkeletonBar.bar(for: line))
    }

    func testACenteredLineStaysCentered() {
        let bar: CGRect = SkeletonBar.bar(for: CGRect(x: 20, y: 0, width: 100, height: 20))

        XCTAssertEqual(bar.midX, 70, accuracy: 0.01)
    }
}
