import Foundation

/// A tapped download notification: the profile it was for.
struct DownloadTap: Equatable {
    let serverId: String
    let userId: String

    private enum Key {
        static let marker = "openDownloads"
        static let serverId = "serverId"
        static let userId = "userId"
    }

    static func userInfo(serverId: String, userId: String) -> [AnyHashable: Any] {
        [Key.marker: true, Key.serverId: serverId, Key.userId: userId]
    }

    init?(userInfo: [AnyHashable: Any]) {
        guard userInfo[Key.marker] as? Bool == true,
              let serverId = userInfo[Key.serverId] as? String,
              let userId = userInfo[Key.userId] as? String else { return nil }
        self.serverId = serverId
        self.userId = userId
    }
}
