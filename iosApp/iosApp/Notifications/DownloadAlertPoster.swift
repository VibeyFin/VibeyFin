import Shared
import UIKit
import UserNotifications

/// Posts the notification for a finished queue or a failed download, unless the app is in front.
enum DownloadAlertPoster {
    static func post(_ alert: DownloadAlert) {
        guard UIApplication.shared.applicationState != .active else { return }
        let content = UNMutableNotificationContent()
        content.title = alert.title
        content.body = alert.body
        content.sound = .default
        content.threadIdentifier = "downloads"
        content.userInfo = DownloadTap.userInfo(serverId: alert.profile.serverId, userId: alert.profile.userId)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
    }
}
