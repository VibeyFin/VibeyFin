import UIKit
import XCTest
@testable import VibeyFin

final class BrandFontTests: XCTestCase {
    func testTheBundledBrandFaceLoadsByItsPostScriptName() {
        XCTAssertNotNil(UIFont(name: "ArchivoCondensed-ExtraBold", size: 40))
    }
}
