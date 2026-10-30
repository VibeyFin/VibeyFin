import Foundation

enum PlayerTime {
    /// Seconds from the player as the milliseconds the shared model counts in.
    static func milliseconds(_ seconds: TimeInterval) -> Int64 {
        Int64((seconds.isFinite ? max(0, seconds) : 0) * 1000)
    }
}

enum SeekSide {
    case back
    case forward
}
