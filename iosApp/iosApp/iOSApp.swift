import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        MainViewControllerKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
