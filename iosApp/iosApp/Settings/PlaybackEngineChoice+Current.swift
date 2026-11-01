import Shared

// PoC: remove with poc/playback-engine
extension PlaybackEngineChoice {
    /// The engine the next video plays with, from the device's "Playback engine (debug)" setting.
    static var current: PlaybackEngineChoice { PlaybackEngineChoiceStore.shared.current }
}
