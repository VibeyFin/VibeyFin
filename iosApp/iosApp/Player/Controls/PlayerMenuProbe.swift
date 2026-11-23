import SwiftUI
import UIKit

extension View {
    /// Reports when the SwiftUI `Menu` this is attached to opens (`true`) and closes (`false`).
    ///
    /// `Menu` has no such callback. On iOS it is drawn by UIKit's context-menu machinery, so a hidden
    /// probe polls for that menu's views (see `MenuOpenProbe.menuIsShowing`). When none is ever seen
    /// nothing is reported, so the caller keeps whatever it does on a touch.
    func onMenuOpenChange(_ perform: @escaping (Bool) -> Void) -> some View {
        background(MenuOpenProbe(onChange: perform).allowsHitTesting(false))
    }
}

private struct MenuOpenProbe: UIViewRepresentable {
    let onChange: (Bool) -> Void

    func makeUIView(context: Context) -> ProbeView {
        let view = ProbeView()
        view.onChange = onChange
        return view
    }

    func updateUIView(_ view: ProbeView, context: Context) {
        view.onChange = onChange
    }
}

private final class ProbeView: UIView {
    var onChange: (Bool) -> Void = { _ in }
    private var timer: Timer?
    private var wasOpen = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        isAccessibilityElement = false
    }

    required init?(coder: NSCoder) { nil }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        timer?.invalidate()
        timer = nil
        // A menu that was open when the view went away is closed as far as the caller is concerned.
        if wasOpen {
            wasOpen = false
            onChange(false)
        }
        guard window != nil else { return }
        let timer = Timer(timeInterval: 0.1, repeats: true) { [weak self] _ in self?.poll() }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
    }

    deinit { timer?.invalidate() }

    private func poll() {
        let open = MenuOpenProbe.menuIsShowing()
        if open != wasOpen {
            wasOpen = open
            onChange(open)
        }
    }
}

extension MenuOpenProbe {
    /// UIKit has no public flag for "a context menu is showing", but while one is it puts its container
    /// into a window of the app, and those views carry "ContextMenu" in their class name (checked by
    /// name only, nothing private is called). If a future iOS renames them this reports false and the
    /// player falls back to holding its controls until the next touch.
    static func menuIsShowing() -> Bool {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.flatMap(\.windows).contains { containsMenu($0) }
    }

    private static func containsMenu(_ view: UIView) -> Bool {
        if !view.isHidden, NSStringFromClass(type(of: view)).contains("ContextMenu") { return true }
        return view.subviews.contains { containsMenu($0) }
    }
}
