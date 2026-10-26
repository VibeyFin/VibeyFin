import SwiftUI

/// The player the tab shell shows over the tabs, and whether it is out of sight while picture-in-picture plays. It outlives the screens that start
/// playback, so picture-in-picture can go on while the tabs are in front.
final class PlayerSession: ObservableObject {
    struct Request: Identifiable, Equatable {
        let id: String
        let startOver: Bool
        /// A new one for each start, so the same title played twice gets a fresh player.
        let token = UUID()
    }

    @Published private(set) var request: Request?
    /// The player is out of sight while picture-in-picture plays; its screen stays alive behind the tabs.
    @Published private(set) var minimised = false

    /// Ends the live player the way its close button does: the server hears `Stopped`, and a picture-in-picture window goes away.
    /// Set by the player screen while it exists.
    var stopCurrent: () -> Void = {}

    /// Starts `itemId`. A title still playing, in picture-in-picture or not, is stopped first, so there is only ever one.
    func play(_ itemId: String, startOver: Bool = false) {
        stopCurrent()
        stopCurrent = {}
        request = Request(id: itemId, startOver: startOver)
        minimised = false
    }

    func minimise() { minimised = true }

    func expand() { minimised = false }

    func end() {
        request = nil
        minimised = false
        stopCurrent = {}
    }
}

private struct PlayerSessionKey: EnvironmentKey {
    static let defaultValue = PlayerSession()
}

extension EnvironmentValues {
    /// The session the tab shell owns, so any screen can start playback.
    var playerSession: PlayerSession {
        get { self[PlayerSessionKey.self] }
        set { self[PlayerSessionKey.self] = newValue }
    }
}
