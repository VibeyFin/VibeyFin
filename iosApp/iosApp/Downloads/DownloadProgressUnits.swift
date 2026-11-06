import Foundation

enum DownloadProgressUnits {
    /// Never complete: a full bar makes the system end the task, so only finishing the queue may fill it.
    static func units(done: Int64, total: Int64) -> (completed: Int64, total: Int64) {
        let total = max(total, done + 1, 2)
        return (min(max(done, 0), total - 1), total)
    }

    /// Writes in the order that keeps the bar below full at every step: a total smaller than the bytes still set would read as finished.
    static func apply(_ units: (completed: Int64, total: Int64), to progress: Progress) {
        if units.completed < progress.completedUnitCount {
            progress.completedUnitCount = units.completed
            progress.totalUnitCount = units.total
        } else {
            progress.totalUnitCount = units.total
            progress.completedUnitCount = units.completed
        }
    }
}
