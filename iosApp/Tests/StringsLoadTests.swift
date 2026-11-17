import Shared
import XCTest
@testable import VibeyFin

final class StringsLoadTests: XCTestCase {
    override func setUp() {
        super.setUp()
        Texts.shared.select(tag: "en")
    }

    override func tearDown() {
        Texts.shared.select(tag: "en")
        super.tearDown()
    }

    func testTheAppBundleSuppliesTheEnglishText() {
        XCTAssertEqual(Strings.shared.tryAgain, "Try again")
        XCTAssertEqual(Strings.shared.whoIsWatching, "Who's watching?")
    }
}
