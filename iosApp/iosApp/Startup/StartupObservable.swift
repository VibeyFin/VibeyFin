import Foundation
import Shared

/// Asks the shared code where the app opens. `StartupModel` calls back once, on the main thread.
final class StartupObservable: ObservableObject {
    private let model = StartupModel()
    private var started = false

    /// Starts working it out. Called when the root view appears, not from `init`.
    func start(onDecided: @escaping (StartDestination) -> Void) {
        guard !started else { return }
        started = true
        model.observe { destination in
            onDecided(destination)
        }
    }

    deinit {
        model.close()
    }
}
