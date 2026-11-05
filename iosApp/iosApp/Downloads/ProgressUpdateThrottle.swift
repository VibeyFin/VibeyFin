import Foundation

/// Lets the system progress be written at most once per interval, but a new title at once: the system limits how often it can be updated.
struct ProgressUpdateThrottle {
    let interval: TimeInterval
    private var lastUpdate: Date?
    private var lastTitle: String?

    init(interval: TimeInterval) {
        self.interval = interval
    }

    mutating func allows(title: String, at now: Date) -> Bool {
        let titleChanged: Bool = title != lastTitle
        let due: Bool = lastUpdate.map { now.timeIntervalSince($0) >= interval } ?? true
        guard titleChanged || due else { return false }
        lastUpdate = now
        lastTitle = title
        return true
    }
}
