import Combine
import SwiftUI
import UIKit
import Shared

/// Set by TabShell, which owns the navigation: Details and Trailer from a Seerr poster's menu.
struct SeerrMenuHandlers {
    var details: (SeerrTitle) -> Void = { _ in }
    var trailer: (String) -> Void = { _ in }
}

/// The Seerr poster menu behind a long press anywhere in the tabs: the tab shell owns it. A press that rests starts reading the
/// title (`open`); the menu's rows follow what is read. A request from the menu leaves its message here for the shell's banner, and a
/// show's Request leaves the season picker here for the shell's sheet.
final class SeerrMenuOpener: ObservableObject {
    /// What the last request from a menu came to, with the title it was for (for View).
    @Published var notice: SeerrNotice?
    @Published var noticeTitle: SeerrTitle?
    /// The season picker is up (a show's Request, or a movie's when the profile chooses the quality).
    @Published private(set) var showsPicker = false

    /// Not published: a redraw of every poster during a press would cancel it.
    private(set) var model: SeerrTitleObservable?
    let stateChanges = PassthroughSubject<(SeerrTitle, SeerrTitleUiState), Never>()
    var handlers = SeerrMenuHandlers()

    private(set) var title: SeerrTitle?
    private var subscription: AnyCancellable?
    /// Request was chosen while the title was still being read; it is sent once it is.
    private var requestPending = false

    func open(_ title: SeerrTitle) {
        if let current = self.title, current.tmdbId == title.tmdbId, current.type == title.type, model != nil { return }
        self.title = title
        requestPending = false
        let model = SeerrTitleObservable(type: title.type, tmdbId: title.tmdbId)
        self.model = model
        subscription = model.$state.compactMap { $0 }.sink { [weak self, weak model] state in
            guard let self, let model, model === self.model else { return }
            self.received(state, title: title, model: model)
        }
        model.start()
    }

    func request() {
        guard let model else { return }
        if model.state?.loading == false { model.request() } else { requestPending = true }
    }

    func request4k() { model?.request4k() }
    func cancelRequest() { model?.cancelRequest() }
    func retryRequest() { model?.retryRequest() }
    func pickerDismiss() { model?.pickerDismiss() }

    private func received(_ state: SeerrTitleUiState, title: SeerrTitle, model: SeerrTitleObservable) {
        stateChanges.send((title, state))
        if requestPending, !state.loading {
            requestPending = false
            if state.canRequest { model.request() }
        }
        if showsPicker != (state.picker != nil) { showsPicker = state.picker != nil }
        if let shown = state.notice {
            model.noticeShown()
            if !shown.failure { UINotificationFeedbackGenerator().notificationOccurred(.success) }
            noticeTitle = title
            notice = shown
        }
    }
}

private struct SeerrMenuKey: EnvironmentKey {
    static let defaultValue: SeerrMenuOpener? = nil
}

extension EnvironmentValues {
    /// The Seerr poster menu the tab shell owns, so a Seerr poster anywhere can open it with a long press.
    var seerrMenu: SeerrMenuOpener? {
        get { self[SeerrMenuKey.self] }
        set { self[SeerrMenuKey.self] = newValue }
    }
}
