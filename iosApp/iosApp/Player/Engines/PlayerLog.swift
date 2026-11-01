import os

/// The player's log, in Console under the `player` category of `app.vibeyfin`.
enum PlayerLog {
    private static let logger = Logger(subsystem: "app.vibeyfin", category: "player")

    static func write(_ message: String) {
        logger.info("\(message, privacy: .public)")
    }
}
