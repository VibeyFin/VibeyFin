import SwiftUI
import Shared
import UserNotifications

@main
struct iOSApp: App {
    // Answers which orientations the app allows; the player turns phones to landscape through it.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        IosEntryKt.startKoinIos()
        // Both must be in place before launch ends: the background task's handler, and the delegate that gets a tap which launched the app.
        NewTitlesScheduler.register()
        UNUserNotificationCenter.current().delegate = NotificationTaps.shared
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
        }
        // At launch and each time the app comes back: the next background check is asked for, or dropped when nobody wants one.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { NewTitlesScheduler.refresh() }
        }
    }
}
