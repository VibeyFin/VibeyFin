import UserNotifications

/// Asks iOS for permission to show notifications. Only shows the system prompt the first time; after that iOS answers from what the
/// user chose (in the prompt or in system settings).
enum NotificationPermission {
    /// Calls `onResult` on the main thread with whether notifications may be shown.
    static func request(_ onResult: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            DispatchQueue.main.async { onResult(granted) }
        }
    }
}
