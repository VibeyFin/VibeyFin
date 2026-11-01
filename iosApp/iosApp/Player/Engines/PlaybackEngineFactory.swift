import Foundation
import Shared

/// Builds the player for a video screen. Which player a stream uses is decided per stream by the shared model.
enum PlaybackEngineFactory {
    @MainActor
    static func make() -> PlaybackEngine {
        SwitchingEngine()
    }
}
