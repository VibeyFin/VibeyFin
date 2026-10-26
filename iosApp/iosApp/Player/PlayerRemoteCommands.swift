import MediaPlayer

/// The buttons on the Control Center and lock screen media card: play, pause, skip and the scrubber. They act on the one player
/// that registered them, and go with it, so a closed player is never controlled from the card.
final class PlayerRemoteCommands {
    struct Handlers {
        var play: () -> Void
        var pause: () -> Void
        var togglePlayPause: () -> Void
        var skip: (SeekSide) -> Void
        var seek: (TimeInterval) -> Void
    }

    private var installed: [(command: MPRemoteCommand, target: Any)] = []

    /// Registers the handlers, replacing any set before.
    func install(_ handlers: Handlers) {
        remove()
        let center = MPRemoteCommandCenter.shared()
        add(center.playCommand) { handlers.play() }
        add(center.pauseCommand) { handlers.pause() }
        add(center.togglePlayPauseCommand) { handlers.togglePlayPause() }
        add(center.skipBackwardCommand) { handlers.skip(.back) }
        add(center.skipForwardCommand) { handlers.skip(.forward) }
        installed.append((center.changePlaybackPositionCommand, center.changePlaybackPositionCommand.addTarget { event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            handlers.seek(event.positionTime)
            return .success
        }))
        // There is no queue to go through.
        center.nextTrackCommand.isEnabled = false
        center.previousTrackCommand.isEnabled = false
    }

    /// The seconds the skip buttons on the card jump, the profile's own.
    func setSkipIntervals(back: Int, forward: Int) {
        let center = MPRemoteCommandCenter.shared()
        center.skipBackwardCommand.preferredIntervals = [NSNumber(value: back)]
        center.skipForwardCommand.preferredIntervals = [NSNumber(value: forward)]
    }

    func remove() {
        for (command, target) in installed { command.removeTarget(target) }
        installed = []
    }

    private func add(_ command: MPRemoteCommand, _ action: @escaping () -> Void) {
        command.isEnabled = true
        installed.append((command, command.addTarget { _ in
            action()
            return .success
        }))
    }
}
