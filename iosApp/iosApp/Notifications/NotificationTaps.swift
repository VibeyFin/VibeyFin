import Foundation
import UserNotifications

/// A tapped new-title notification: the title to open and the profile it was for.
struct NewTitleTap: Equatable {
    let serverId: String
    let userId: String
    let itemId: String

    private enum Key {
        static let serverId = "serverId"
        static let userId = "userId"
        static let itemId = "itemId"
    }

    static func userInfo(serverId: String, userId: String, itemId: String) -> [AnyHashable: Any] {
        [Key.serverId: serverId, Key.userId: userId, Key.itemId: itemId]
    }

    init?(userInfo: [AnyHashable: Any]) {
        guard let serverId = userInfo[Key.serverId] as? String,
              let userId = userInfo[Key.userId] as? String,
              let itemId = userInfo[Key.itemId] as? String else { return nil }
        self.serverId = serverId
        self.userId = userId
        self.itemId = itemId
    }
}

/// Receives the notification centre's callbacks and holds the last tap until the screens can act on it. A tap can arrive before the app
/// has decided where it opens, so the screens read `pending` rather than being told directly. Only the tabs open the title, and only
/// when that profile is the one signed in; everything else keeps the screen the app chose, so no PIN is ever skipped.
final class NotificationTaps: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationTaps()

    @Published var pending: NewTitleTap?

    /// A notification that arrives while the app is open still shows as a banner.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let tap = NewTitleTap(userInfo: response.notification.request.content.userInfo)
        DispatchQueue.main.async {
            if let tap { self.pending = tap }
            completionHandler()
        }
    }
}
