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
    /// The window was announced with `entered` but never appeared: the video is still inline.
    var failedToStart: () -> Void = {}

    private var controller: AVPictureInPictureController?
    private var restoring = false
    private var silenced = false

    /// Ties picture-in-picture to the video's layer. Does nothing where the device can't do it.
    func attach(to layer: AVPlayerLayer) {
        guard controller == nil, AVPictureInPictureController.isPictureInPictureSupported() else { return }
        let pip = AVPictureInPictureController(playerLayer: layer)
        pip?.delegate = self
        pip?.canStartPictureInPictureAutomaticallyFromInline = true
        controller = pip
        isSupported = pip != nil
    }

    /// Turns the video into the window now, as leaving the app would.
    func start() {
        guard let controller, controller.isPictureInPicturePossible else { return }
        controller.startPictureInPicture()
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
        entered()
    }

    func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        failedToStartPictureInPictureWithError error: Error
    ) {
        let announced = isActive && !silenced
        isActive = false
        restoring = false
        if announced { failedToStart() }
    }

    /// Tapping the window: the player screen never went away, so there is nothing to put back.
    func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
    ) {
        restoring = true
        completionHandler(true)
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ controller: AVPictureInPictureController) {
        let closed = !restoring
        restoring = false
        guard !silenced else { return }
        isActive = false
        left(closed)
    }
}
