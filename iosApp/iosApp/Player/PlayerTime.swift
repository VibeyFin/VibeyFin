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

extension SeekSide {
    /// The left third of the video skips back, the right third forward; the middle is neither.
    static func at(x: CGFloat, width: CGFloat) -> SeekSide? {
        guard width > 0 else { return nil }
        if x < width / 3 { return .back }
        if x > width * 2 / 3 { return .forward }
        return nil
    }
}
