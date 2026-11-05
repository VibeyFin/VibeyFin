import BackgroundTasks
import os
import Shared
import UIKit
import UserNotifications

/// The download in hand in the system's own progress (iOS 26 and up); the transfers stay in the background URLSession. Main thread only.
final class DownloadSystemProgress {
    static let shared = DownloadSystemProgress()

    /// The system only accepts a pattern that starts with the app's own bundle ID, which a developer build changes.
    private static let taskPrefix: String = "\(Bundle.main.bundleIdentifier ?? "app.vibeyfin").download"
    private static let taskIdentifier: String = "\(taskPrefix).queue"

    private static let log: Logger = Logger(subsystem: "app.vibeyfin", category: "downloads")

    private let model = DownloadProgressModel()
    private var latest: DownloadStatus?
    private var requested = false
    private var registered = false
    private var throttle = ProgressUpdateThrottle(interval: 1)
    /// A `BGContinuedProcessingTask` while one runs; held untyped because the type needs iOS 26.
    private var task: AnyObject?

    /// Registers the task handler. Must run before the app finishes launching, and after Koin has started.
    func register() {
        guard #available(iOS 26.0, *) else { return }
        registered = BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.taskIdentifier, using: .main) { task in
            guard let task = task as? BGContinuedProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self.begin(task)
        }
        if !registered { Self.log.error("System progress not registered for \(Self.taskIdentifier, privacy: .public)") }
    }

    func start() {
        model.onAlert { DownloadAlertPoster.post($0) }
        model.observe { [weak self] in self?.apply($0) }
    }

    private func apply(_ snapshot: DownloadStatus?) {
        let queueStarted = latest == nil && snapshot != nil
        if let snapshot, snapshot.title != latest?.title || isWaiting(snapshot) {
            Self.log.notice("Now: \(snapshot.title, privacy: .public) | \(snapshot.subtitle, privacy: .public)")
        }
        latest = snapshot
        guard let snapshot else {
            Self.log.notice("Download queue ended")
            finish(success: true)
            requested = false
            return
        }
        if queueStarted { askForPermission() }
        guard #available(iOS 26.0, *) else { return }
        if let task = task as? BGContinuedProcessingTask {
            update(task, snapshot)
        } else if registered, !requested, UIApplication.shared.applicationState == .active {
            submit(snapshot)
        }
    }

    /// The system shows its prompt only once, so asking at every queue start still asks once; only for a profile that wants the alerts.
    private func askForPermission() {
        model.alertsWanted { wanted in
            guard wanted.boolValue else { return }
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                if settings.authorizationStatus == .notDetermined { NotificationPermission.request { _ in } }
            }
        }
    }

    @available(iOS 26.0, *)
    private func submit(_ snapshot: DownloadStatus) {
        // One try per queue: a refusal (the system wants a user's action to start one) is not repeated with every progress report.
        requested = true
        let request = BGContinuedProcessingTaskRequest(identifier: Self.taskIdentifier, title: snapshot.title, subtitle: snapshot.subtitle)
        request.strategy = .queue
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            Self.log.error("System progress refused: \(error.localizedDescription, privacy: .public)")
        }
    }

    @available(iOS 26.0, *)
    private func begin(_ task: BGContinuedProcessingTask) {
        finish(success: true)
        self.task = task
        Self.log.notice("System progress started")
        // The system ends the task on its own too; the transfers belong to the background session, so they go on and a new task is asked for.
        task.expirationHandler = { [weak self, weak task] in
            DispatchQueue.main.async {
                guard let self, let task, self.task === task else { return }
                Self.log.notice("System progress ended by the system or cancelled; downloads go on")
                self.finish(success: false)
                self.requested = false
            }
        }
        if let latest { update(task, latest) }
    }

    @available(iOS 26.0, *)
    private func update(_ task: BGContinuedProcessingTask, _ snapshot: DownloadStatus) {
        guard self.task === task, throttle.allows(title: throttleKey(snapshot), at: Date()) else { return }
        let units = DownloadProgressUnits.units(done: snapshot.queueDone, total: snapshot.queueTotal)
        DownloadProgressUnits.apply(units, to: task.progress)
        task.updateTitle(snapshot.title, subtitle: snapshot.subtitle)
    }

    /// The title, and the waiting text too: that line does not change with every chunk, so it is not held back.
    private func throttleKey(_ snapshot: DownloadStatus) -> String {
        isWaiting(snapshot) ? "\(snapshot.title) \(snapshot.subtitle)" : snapshot.title
    }

    private func isWaiting(_ snapshot: DownloadStatus) -> Bool {
        let waitingTexts: [String] = [Strings.shared.downloadWaitingForNetwork, Strings.shared.downloadWaitingForWifi, Strings.shared.downloadWaitingForServer]
        return waitingTexts.contains(snapshot.subtitle)
    }

    private func finish(success: Bool) {
        guard #available(iOS 26.0, *), let task = task as? BGContinuedProcessingTask else { return }
        self.task = nil
        Self.log.notice("System progress finished, success \(success, privacy: .public)")
        task.setTaskCompleted(success: success)
    }
}
