import UIKit
import Shared

/// SwiftUI has no way to say which orientations a screen allows, so the app delegate answers for
/// the whole app from `OrientationLock.mask`. Everything allows what Info.plist lists, except the
/// player on a phone, which allows landscape only.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        OrientationLock.mask
    }

    /// iOS woke the app for a download that finished or needs the session's answer while it was closed: the shared downloader picks the
    /// session back up, finishes its files, and calls `completionHandler` when done.
    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        IosEntryKt.handleBackgroundDownloadEvents(identifier: identifier, completion: completionHandler)
    }
}

enum OrientationLock {
    /// What the app allows right now.
    static var mask: UIInterfaceOrientationMask = defaultMask

    /// What Info.plist allows: iPad turns every way, iPhone everything but upside down.
    private static var defaultMask: UIInterfaceOrientationMask { defaultMask(for: UIDevice.current.userInterfaceIdiom) }

    /// An iPhone stays upright outside the player, which asks for landscape itself; an iPad turns freely. The Info.plist still lists landscape for the iPhone, or the player could not ask for it.
    static func defaultMask(for idiom: UIUserInterfaceIdiom) -> UIInterfaceOrientationMask {
        idiom == .pad ? .all : .portrait
    }

    private static var orientationToRestore: UIInterfaceOrientationMask?

    private static var scene: UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    }

    /// Phones and tablets turn to landscape for the player and stay there. (An iPad refuses it while the app is windowed, in Split View or in Slide Over: "The current windowing mode does not allow for programmatic changes to interface orientation". The request is kept for when the system allows it.)
    static func lockLandscape() {
        guard orientationToRestore == nil else { return }
        orientationToRestore = scene.map { maskFor($0.interfaceOrientation) } ?? .portrait
        apply(.landscape)
    }

    /// Lets the app turn freely again and goes back to the orientation it was in before the player.
    static func restore() {
        guard let previous = orientationToRestore else { return }
        orientationToRestore = nil
        mask = defaultMask
        apply(previous, keepingMask: true)
    }

    private static func apply(_ target: UIInterfaceOrientationMask, keepingMask: Bool = false) {
        if !keepingMask { mask = target }
        guard let scene else { return }
        scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: target)) { _ in }
    }

    private static func maskFor(_ orientation: UIInterfaceOrientation) -> UIInterfaceOrientationMask {
        switch orientation {
        case .landscapeLeft: return .landscapeLeft
        case .landscapeRight: return .landscapeRight
        case .portraitUpsideDown: return .portraitUpsideDown
        default: return .portrait
        }
    }
}
