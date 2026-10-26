import Foundation
import Shared

/// Lets SwiftUI watch the shared Home state and send it events. `HomeModel` calls back on the main thread.
final class HomeObservable: ObservableObject {
    /// Nil until the shared ViewModel reports its first state.
    @Published private(set) var state: HomeUiState?

    private let model = HomeModel()
    private var started = false

    /// Starts watching. Called when the screen appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    func switchProfile() {
        model.onSwitchProfile()
    }

    func switchServer() {
        model.onSwitchServer()
    }

    func signOut() {
        model.onSignOut()
    }

    func submitPin(_ pin: String) { model.onSubmitPin(pin: pin) }

    func forgotPin() { model.onForgotPin() }

    func submitPassword(_ password: String) { model.onSubmitPassword(password: password) }

    func dismissPin() { model.onDismissPin() }

    func retryLibraries() {
        model.onRetryLibraries()
    }

    func markWatched(_ cardId: String) {
        model.onMarkWatched(cardId: cardId)
    }

    func download(_ cardId: String) { model.onDownload(cardId: cardId) }

    func act(_ cardId: String, _ action: DownloadAction) { model.onDownloadAction(cardId: cardId, action: action) }

    func confirmRemove() { model.onConfirmRemove() }

    func dismissRemoval() { model.onDismissRemoval() }

    func retryAction() {
        model.onRetryAction()
    }

    func dismissMessage() {
        model.onDismissMessage()
    }

    func heroPlay() { model.onHeroPlay() }

    func heroNavHandled() { model.onHeroNavHandled() }

    func heroMyList() { model.onHeroMyList() }

    func retryContinue() {
        model.onRetryContinue()
    }

    /// "Try again" on one row: only it reloads.
    func retryRow(_ id: String) {
        model.onRetryRow(id: id)
    }

    /// A category button was tapped: Shows, Movies and New open their page, Categories opens its list.
    func category(_ kind: CategoryKind) {
        model.onCategory(kind: kind)
    }

    /// A genre in the Categories list was chosen: its page opens and the list closes.
    func pickOption(_ id: String) {
        model.onPickOption(id: id)
    }

    /// The screen has opened the page asked for.
    func listOpened() {
        model.onListOpened()
    }

    func dismissSheet() {
        model.onDismissSheet()
    }

    func retrySheet() {
        model.onRetrySheet()
    }

    /// Pull to refresh. The shared view model reloads both lists and reports `refreshing`; this returns
    /// when it has finished, which is what keeps the pull indicator up.
    @MainActor
    func refresh() async {
        model.onRefresh()
        // Give the state a moment to say it started; with nobody signed in it never does.
        try? await Task.sleep(nanoseconds: 100_000_000)
        while state?.refreshing == true {
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
    }

    /// The app came back to the foreground, where something may have been watched on another device.
    func foreground() {
        model.onForeground()
    }

    deinit {
        model.close()
    }
}
