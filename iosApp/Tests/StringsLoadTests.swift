import Shared
import XCTest
@testable import VibeyFin

final class StringsLoadTests: XCTestCase {
    func testTheAppBundleSuppliesTheEnglishText() {
        XCTAssertEqual(Strings.shared.tryAgain, "Try again")
        XCTAssertEqual(Strings.shared.whoIsWatching, "Who's watching?")
    }
}
