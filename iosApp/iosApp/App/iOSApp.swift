import SwiftUI
import Shared

@main
struct iOSApp: App {
    // Answers which orientations the app allows; the player turns phones to landscape through it.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        IosEntryKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
        }
    }
}
