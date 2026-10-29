import Foundation

/// The playback position the controls draw, in seconds. It changes about twice a second, so it is
/// published apart from `PlayerController`: only the views that observe it re-evaluate on a tick.
final class PlayerClock: ObservableObject {
    @Published var position: TimeInterval = 0
    /// The end of the loaded range holding the position; the position itself when none does.
    @Published var bufferedPosition: TimeInterval = 0
}
