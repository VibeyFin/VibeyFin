import BackgroundTasks
import Shared
import UserNotifications

/// Background refresh for new-title notifications. Jellyfin sends no push, so iOS wakes the app now and then (best effort: iOS picks
/// the real time) and the shared poller asks each profile with notifications on what is new. Every new movie and every new episode
/// becomes one local notification; episodes are grouped per show.
enum NewTitlesScheduler {
    static let taskIdentifier = "app.vibeyfin.newtitles"
    /// The earliest the next run may start. iOS decides when it really runs, often later.
    private static let interval: TimeInterval = 4 * 60 * 60

    /// Registers the task handler. Must run before the app finishes launching, and after Koin has started.
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: .main) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(refresh)
        }
    }

    /// Makes sure a run is pending when any saved profile has notifications on, and cancels it when none does. Called at launch,
    /// when the app comes to the foreground and when the Notifications setting changes. A run already pending is kept as it is:
    /// submitting again would push its earliest start 4 hours on each time, and an app opened often would never get a run.
    static func refresh() {
        let model = NewTitlesModel()
        model.notifiableProfileCount { count in
            withExtendedLifetime(model) {}
            guard count.intValue > 0 else {
                cancel()
                return
            }
            BGTaskScheduler.shared.getPendingTaskRequests { requests in
                if !requests.contains(where: { $0.identifier == taskIdentifier }) { schedule() }
            }
        }
    }

    /// Submits the next run. A pending request with the same identifier is replaced, so only a finished run calls this directly.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        // Fails in the simulator and when background refresh is off for the app; there is nothing to do about either here.
        try? BGTaskScheduler.shared.submit(request)
    }

    static func cancel() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
    }

    private static func handle(_ task: BGAppRefreshTask) {
        // The model must stay alive until its callback: the closures below hold it.
        let model = NewTitlesModel()
        // Only touched on the main queue (the handler is registered on it and the model calls back on it), so the task completes once.
        var finished = false
        let finish = { (success: Bool) in
            guard !finished else { return }
            finished = true
            task.setTaskCompleted(success: success)
        }
        // The poll can't be stopped halfway; iOS only gets told this run is over. Titles it already marked are not reported again.
        // iOS may call this on another queue: hop to main, where `finished` lives.
        task.expirationHandler = { DispatchQueue.main.async { finish(false) } }

        model.notifiableProfileCount { count in
            let profiles = count.intValue
            guard profiles > 0 else {
                // Nobody wants notifications any more: no next run.
                cancel()
                finish(true)
                return
            }
            // The next run is asked for first, so a run that iOS cuts short still has one after it.
            schedule()
            // If the user has since turned notifications off for the app in system settings, the posts fail silently and these
            // titles stay marked as notified. Accepted: at most once, as the poller's contract says.
            model.poll { results in
                for profile in results {
                    post(profile, named: profiles > 1)
                }
                finish(true)
            }
        }
    }

    /// Posts one notification per title. At most once: the poller has already marked these titles, so a failed post is not retried.
    private static func post(_ profile: ProfileNewTitles, named: Bool) {
        let center = UNUserNotificationCenter.current()
        for title in profile.titles {
            let content = UNMutableNotificationContent()
            content.title = title.isMovie
                ? Strings.shared.addedMovieNotificationTitle
                : Strings.shared.addedEpisodeNotificationTitle(seriesName: title.seriesName)
            content.body = Strings.shared.addedTitleNotificationBody(title: title.title, profileName: named ? profile.profileName : nil)
            content.sound = .default
            // iOS stacks a show's episodes together; new movies share one stack per server. Shows are told apart by name, since
            // NewTitle has no series id: two shows with the same name on one server share a stack.
            content.threadIdentifier = title.isMovie
                ? "\(profile.serverId)|movies"
                : "\(profile.serverId)|show|\(title.seriesName ?? title.itemId)"
            content.userInfo = NewTitleTap.userInfo(serverId: profile.serverId, userId: profile.userId, itemId: title.itemId)
            let request = UNNotificationRequest(
                identifier: "\(profile.serverId)|\(profile.userId)|\(title.itemId)",
                content: content,
                trigger: nil
            )
            center.add(request) { _ in }
        }
    }
}
