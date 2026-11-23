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
        return defaultMask(horizontal: traits?.horizontalSizeClass ?? .compact, vertical: traits?.verticalSizeClass ?? .regular)
    }

    /// A window regular both ways (a foldable's inner display) turns freely; a compact one stays upright outside the player.
    static func defaultMask(horizontal: UIUserInterfaceSizeClass, vertical: UIUserInterfaceSizeClass) -> UIInterfaceOrientationMask {
        horizontal == .regular && vertical == .regular ? .all : .portrait
    }

    /// The player keeps a compact window in landscape; a window regular both ways (the inner display) follows the device, so it can reach the tabletop pose.
    static func playerMask(horizontal: UIUserInterfaceSizeClass, vertical: UIUserInterfaceSizeClass) -> UIInterfaceOrientationMask {
        horizontal == .regular && vertical == .regular ? .all : .landscape
    }

    private static var windowPlayerMask: UIInterfaceOrientationMask {
        let traits: UITraitCollection? = scene?.keyWindow?.traitCollection
        return playerMask(horizontal: traits?.horizontalSizeClass ?? .compact, vertical: traits?.verticalSizeClass ?? .regular)
    }

    /// The window's traits changed (a foldable switched display): the player's lock follows the new window, and a window left sideways turns upright when the new default forbids it.
    static func refreshDefault(horizontal: UIUserInterfaceSizeClass, vertical: UIUserInterfaceSizeClass) {
        guard let scene else { return }
        if orientationToRestore != nil {
            apply(playerMask(horizontal: horizontal, vertical: vertical))
            return
        }
        guard lockedMask == nil else { return }
        let allowed: UIInterfaceOrientationMask = defaultMask(horizontal: horizontal, vertical: vertical)
        scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        guard !allowed.contains(maskFor(scene.interfaceOrientation)) else { return }
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: allowed)) { _ in }
    }

    private static var orientationToRestore: UIInterfaceOrientationMask?

    private static var scene: UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    }

    /// A compact window turns to landscape for the player and stays there. (An iPad refuses it while the app is windowed, in Split View or in Slide Over: "The current windowing mode does not allow for programmatic changes to interface orientation". The request is kept for when the system allows it.)
    /// Returns whether the screen has to turn for it, so a caller can wait for the turn.
    @discardableResult
    static func lockLandscape() -> Bool {
        guard orientationToRestore == nil else { return false }
        let current = scene?.interfaceOrientation
        orientationToRestore = current.map { maskFor($0) } ?? .portrait
        let target: UIInterfaceOrientationMask = windowPlayerMask
        apply(target)
        return target == .landscape && current?.isLandscape == false
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
        guard target != .all else { return }
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
