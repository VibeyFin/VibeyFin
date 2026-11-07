import AVKit
import UIKit

/// Picture-in-picture for the player's video layer. Leaving the app while a video plays turns it into
/// a window that keeps playing; the shared model is told when that starts and how it ends: dismissed
/// (the session is over) or expanded back into the app (nothing is reported).
final class PlayerPictureInPicture: NSObject, ObservableObject, AVPictureInPictureControllerDelegate {
    /// The window is up or about to be.
    @Published private(set) var isActive = false
    /// This device can show the window, so the player offers a button for it. False on the simulator.
    @Published private(set) var isSupported = false

    /// The window is about to appear.
    var entered: () -> Void = {}
    /// The window went away; `closed` is false when it was expanded back into the app.
    var left: (_ closed: Bool) -> Void = { _ in }
    /// The window was tapped: the player screen must be back in place before the window flies into it.
    /// Returns whether the screen is turning, so the window waits for it.
    var restoreRequested: () -> Bool = { false }
    /// The window was announced with `entered` but never appeared: the video is still inline.
    var failedToStart: () -> Void = {}

    /// The engine learns when the window is up or gone, for engines that keep their own state about it.
    var activeChanged: (Bool) -> Void = { _ in }

    private static let turnDuration: UInt64 = 500_000_000

    private var controller: AVPictureInPictureController?
    private weak var boundLayer: CALayer?
    private var playback: AnyObject?
    private var restoring = false
    private var silenced = false
    private var allowsStart = true

    /// Ties picture-in-picture to the video's layer. Does nothing where the device can't do it.
    func attach(to layer: AVPlayerLayer) {
        guard controller == nil, AVPictureInPictureController.isPictureInPictureSupported() else { return }
        let pip = AVPictureInPictureController(playerLayer: layer)
        pip?.delegate = self
        pip?.canStartPictureInPictureAutomaticallyFromInline = allowsStart
        controller = pip
        isSupported = pip != nil
    }

    /// Ties picture-in-picture to a new layer, for an engine whose layer changes with each stream.
    func replace(with layer: AVPlayerLayer) {
        guard !(layer === boundLayer && controller != nil) else { return }
        install(AVPictureInPictureController(playerLayer: layer), playback: nil, layer: layer)
    }

    /// Ties picture-in-picture to frames an engine draws itself. `playback` answers the window's transport and stays alive with it.
    func replace(with layer: AVSampleBufferDisplayLayer, playback: AVPictureInPictureSampleBufferPlaybackDelegate & AnyObject) {
        guard AVPictureInPictureController.isPictureInPictureSupported(), !(layer === boundLayer && controller != nil) else { return }
        let source = AVPictureInPictureController.ContentSource(sampleBufferDisplayLayer: layer, playbackDelegate: playback)
        install(AVPictureInPictureController(contentSource: source), playback: playback, layer: layer)
    }

    private func install(_ pip: AVPictureInPictureController?, playback: AnyObject?, layer: CALayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        // The old controller's window dies with it and its delegate is cut, so nothing else would say it is gone.
        if isActive {
            controller?.stopPictureInPicture()
            isActive = false
            activeChanged(false)
        }
        controller?.delegate = nil
        pip?.delegate = self
        pip?.canStartPictureInPictureAutomaticallyFromInline = allowsStart
        controller = pip
        boundLayer = layer
        self.playback = playback
        isSupported = pip != nil
    }

    /// The profile allows picture-in-picture: without it, leaving the app does not turn the video into a window.
    func allow(_ allowed: Bool) {
        controller?.canStartPictureInPictureAutomaticallyFromInline = allowed
        allowsStart = allowed
    }

    /// Turns the video into the window now, as leaving the app would.
    /// Whether the system may open the window by itself as the app leaves: supported, possible now, and playing.
    func mayStartAutomatically(whilePlaying playing: Bool) -> Bool {
        guard playing, let controller else { return false }
        return controller.canStartPictureInPictureAutomaticallyFromInline && controller.isPictureInPicturePossible
    }

    /// Returns whether the window was asked for.
    @discardableResult
    func start() -> Bool {
        guard allowsStart, let controller, controller.isPictureInPicturePossible else { return false }
        controller.startPictureInPicture()
        return true
    }

    /// The screen turns upright first and the window opens after, so the window does not turn with it.
    func startAfterTurningUpright() {
        Task { @MainActor in
            if OrientationLock.restore() { try? await Task.sleep(nanoseconds: Self.turnDuration) }
            if !start() { OrientationLock.lockLandscape() }
        }
    }

    /// Closes the window without reporting it: the caller has ended the session itself.
    func stop() {
        silenced = true
        isActive = false
        controller?.stopPictureInPicture()
    }

    // MARK: - AVPictureInPictureControllerDelegate

    func pictureInPictureControllerWillStartPictureInPicture(_ controller: AVPictureInPictureController) {
        guard !silenced else { return }
        restoring = false
        isActive = true
        activeChanged(true)
        entered()
    }

    func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        failedToStartPictureInPictureWithError error: Error
    ) {
        let announced = isActive && !silenced
        isActive = false
        activeChanged(false)
        restoring = false
        if announced { failedToStart() }
    }

    /// Tapping the window: the screen is put back first, so the window lands on the player and not on the page behind it.
    func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
    ) {
        restoring = true
        guard restoreRequested() else { return completionHandler(true) }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: Self.turnDuration)
            completionHandler(true)
        }
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ controller: AVPictureInPictureController) {
        let closed = !restoring
        restoring = false
        activeChanged(false)
        guard !silenced else { return }
        isActive = false
        left(closed)
    }
}
