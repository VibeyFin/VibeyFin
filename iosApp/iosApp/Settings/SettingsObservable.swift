import Foundation
import Shared

/// Lets SwiftUI watch the shared settings state. `SettingsModel` calls back on the main thread.
final class SettingsObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: SettingsUiState?

    private let model = SettingsModel()
    private var started = false

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func setAlwaysAsk(_ value: Bool) {
        model.onAlwaysAskChanged(value: value)
    }

    func setShowPlaybackStats(_ value: Bool) {
        model.onShowPlaybackStatsChanged(value: value)
    }

    /// 0 is off, otherwise 2 to 5 episodes in a row.
    func setShowHomeHero(_ value: Bool) {
        model.onShowHomeHeroChanged(value: value)
    }

    func setHomeGenreRows(_ value: Int32) {
        model.onHomeGenreRowsChanged(value: value)
    }

    func setBingeThreshold(_ value: Int32) {
        model.onBingeThresholdChanged(value: value)
    }

    func setSkipBack(_ value: Int32) {
        model.onSkipBackChanged(value: value)
    }

    func setSkipForward(_ value: Int32) {
        model.onSkipForwardChanged(value: value)
    }

    func setAutoSkip(_ value: Bool) {
        model.onAutoSkipChanged(value: value)
    }

    deinit {
        model.close()
    }
}
