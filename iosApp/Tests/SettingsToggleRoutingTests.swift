import XCTest
@testable import VibeyFin

final class SettingsToggleRoutingTests: XCTestCase {
    func testASwitchWithAFlagIdGoesToTheFlag() {
        XCTAssertEqual(SettingsToggleRoute.of(devFlagId: "show_skeletons"), .devFlag(id: "show_skeletons"))
    }

    func testASwitchWithoutAFlagIdGoesToTheSetting() {
        XCTAssertEqual(SettingsToggleRoute.of(devFlagId: nil), .setting)
    }

    func testFlagsSharingOneKeyGetDistinctRowIDs() {
        XCTAssertNotEqual(
            settingRowID(keyName: "devFlag", devFlagId: "one"),
            settingRowID(keyName: "devFlag", devFlagId: "two")
        )
    }

    func testASettingRowIDIsItsKeyName() {
        XCTAssertEqual(settingRowID(keyName: "quality", devFlagId: nil), "quality")
    }
}
