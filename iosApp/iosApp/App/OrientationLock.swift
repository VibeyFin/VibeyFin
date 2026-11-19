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
        OrientationLock.mask(for: window?.traitCollection)
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
    /// What the player asked for, until it lets go; nil means the default for the window.
    private static var lockedMask: UIInterfaceOrientationMask?

    /// What the app allows right now in a window with these traits.
    static func mask(for traits: UITraitCollection?) -> UIInterfaceOrientationMask {
        if let lockedMask { return lockedMask }
        if UIDevice.current.userInterfaceIdiom == .pad { return .all }
        return defaultMask(horizontal: traits?.horizontalSizeClass ?? .compact, vertical: traits?.verticalSizeClass ?? .regular)
    }

    /// A window regular both ways (a foldable's inner display) turns freely; a compact one stays upright outside the player.
    static func defaultMask(horizontal: UIUserInterfaceSizeClass, vertical: UIUserInterfaceSizeClass) -> UIInterfaceOrientationMask {
        horizontal == .regular && vertical == .regular ? .all : .portrait
    }

    private static var orientationToRestore: UIInterfaceOrientationMask?

    private static var scene: UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    }

    /// Phones and tablets turn to landscape for the player and stay there. (An iPad refuses it while the app is windowed, in Split View or in Slide Over: "The current windowing mode does not allow for programmatic changes to interface orientation". The request is kept for when the system allows it.)
    /// Returns whether the screen has to turn for it, so a caller can wait for the turn.
    @discardableResult
    static func lockLandscape() -> Bool {
        guard orientationToRestore == nil else { return false }
        let current = scene?.interfaceOrientation
        orientationToRestore = current.map { maskFor($0) } ?? .portrait
        apply(.landscape)
        return current?.isLandscape == false
    }

    /// Lets the app turn freely again and goes back to the orientation it was in before the player.
    /// Returns whether the screen has to turn for it, so a caller can wait for the turn.
    @discardableResult
    static func restore() -> Bool {
        guard let previous = orientationToRestore else { return false }
        let turns = scene.map { maskFor($0.interfaceOrientation) != previous } ?? false
        orientationToRestore = nil
        lockedMask = nil
        apply(previous, keepingMask: true)
        return turns
    }

    private static func apply(_ target: UIInterfaceOrientationMask, keepingMask: Bool = false) {
        if !keepingMask { lockedMask = target }
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
