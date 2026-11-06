import XCTest
@testable import VibeyFin

final class DownloadTapTests: XCTestCase {
    func testRoundTripsTheProfile() {
        let userInfo = DownloadTap.userInfo(serverId: "srv-1", userId: "u1")

        XCTAssertEqual(DownloadTap(userInfo: userInfo), DownloadTap(userInfo: ["openDownloads": true, "serverId": "srv-1", "userId": "u1"]))
        XCTAssertEqual(DownloadTap(userInfo: userInfo)?.userId, "u1")
    }

    func testANewTitleNotificationIsNotADownloadTap() {
        let userInfo = NewTitleTap.userInfo(serverId: "srv-1", userId: "u1", itemId: "m1")

        XCTAssertNil(DownloadTap(userInfo: userInfo))
    }

    func testADownloadNotificationIsNotANewTitleTap() {
        XCTAssertNil(NewTitleTap(userInfo: DownloadTap.userInfo(serverId: "srv-1", userId: "u1")))
    }
}
