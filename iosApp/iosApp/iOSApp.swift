import SwiftUI
import Shared

@main
struct iOSApp: App {
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
