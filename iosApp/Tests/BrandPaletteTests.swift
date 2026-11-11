import XCTest
@testable import VibeyFin

final class BrandPaletteTests: XCTestCase {
    func testAnUnknownOrMissingIdIsTheDefaultPalette() {
        XCTAssertEqual(BrandPalette.byId(nil).action, BrandPalette.defaultPalette.action)
        XCTAssertEqual(BrandPalette.byId("no-such-theme").spark, BrandPalette.defaultPalette.spark)
    }

    func testTheDefaultThemeIsOneOfTheThemes() {
        XCTAssertNotNil(BrandPalette.all[BrandPalette.defaultId])
    }
}
