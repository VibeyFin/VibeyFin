import AVFoundation
import MediaPlayer
import Shared
import SwiftUI
import UIKit

/// What a vertical swipe on the player changes: the brightness on the left half, the volume on the right.
enum SwipeLevel {
    case brightness, volume
}

/// The volume and the brightness a swipe on the player changes. iOS has no call that sets the volume, so it moves the slider of
/// a hidden `MPVolumeView`; that view being on screen also keeps the system's volume HUD away, so the hardware buttons show
/// this player's capsule instead. The brightness is the screen's (iOS has no per-window one): the value before the first swipe
/// is kept and put back with `restoreBrightness()`.
final class PlayerSwipe: ObservableObject {
    /// The level being changed, or the last one while the capsule fades out; nil before the first change.
    @Published private(set) var kind: SwipeLevel?
    /// The level shown, 0 to 1; it follows the finger smoothly.
    @Published private(set) var level: Double = 0
    /// The capsule is showing: a finger is changing the level, or a hardware button just did.
    @Published private(set) var visible = false

    fileprivate weak var host: UIView?
    private var volumeSlider: UISlider? {
        host?.subviews.compactMap { $0 as? MPVolumeView }.first?.subviews.compactMap { $0 as? UISlider }.first
    }
    private var screen: UIScreen? { host?.window?.windowScene?.screen }
    private var startLevel: Double = 0
    private var savedBrightness: CGFloat?
    private var hideTask: Task<Void, Never>?
    private var volumeObservation: NSKeyValueObservation?
    /// The volume this swipe last set, so its own change doesn't come back through the observer as a button press.
    private var lastSetVolume: Float?

    init() {
        // A hardware button changes the volume: the capsule shows it, as the system HUD would have.
        volumeObservation = AVAudioSession.sharedInstance().observe(\.outputVolume, options: [.new]) { [weak self] _, change in
            guard let volume = change.newValue else { return }
            DispatchQueue.main.async { self?.volumeChangedOutside(volume) }
        }
    }

    func begin(_ kind: SwipeLevel) {
        hideTask?.cancel()
        self.kind = kind
        switch kind {
        case .volume: level = Double(AVAudioSession.sharedInstance().outputVolume)
        case .brightness:
            if savedBrightness == nil { savedBrightness = screen?.brightness }
            level = Double(screen?.brightness ?? 0.5)
        }
        startLevel = level
        visible = true
    }

    /// Moves the level to where the finger is: `by` is the share of the full range since the swipe began, up positive.
    func move(by: Double) {
        guard let kind else { return }
        level = min(1, max(0, startLevel + by))
        switch kind {
        case .volume:
            lastSetVolume = Float(level)
            volumeSlider?.setValue(Float(level), animated: false)
            volumeSlider?.sendActions(for: .valueChanged)
        case .brightness:
            screen?.brightness = CGFloat(level)
        }
    }

    func end() {
        if let kind {
            let percent = Int32((level * 100).rounded())
            let text = kind == .volume ? Strings.shared.volumeLevel(percent: percent) : Strings.shared.brightnessLevel(percent: percent)
            UIAccessibility.post(notification: .announcement, argument: text)
        }
        scheduleHide()
    }

    /// Hands the screen its brightness from before the first swipe.
    func restoreBrightness() {
        guard let saved = savedBrightness else { return }
        screen?.brightness = saved
        savedBrightness = nil
    }

    private func volumeChangedOutside(_ volume: Float) {
        if let mine = lastSetVolume, abs(mine - volume) < 0.01 { return }
        lastSetVolume = nil
        hideTask?.cancel()
        kind = .volume
        level = Double(volume)
        visible = true
        scheduleHide()
    }

    private func scheduleHide() {
        hideTask?.cancel()
        hideTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            guard !Task.isCancelled else { return }
            self?.visible = false
        }
    }
}

/// Holds the hidden `MPVolumeView` the swipe moves. It must be in the window to work, and its being there keeps the
/// system volume HUD away. Nearly transparent and too small to touch.
struct PlayerVolumeHost: UIViewRepresentable {
    let swipe: PlayerSwipe

    func makeUIView(context: Context) -> UIView {
        let host = UIView(frame: .zero)
        let volume = MPVolumeView(frame: CGRect(x: -100, y: -100, width: 1, height: 1))
        volume.alpha = 0.01
        volume.isUserInteractionEnabled = false
        host.addSubview(volume)
        host.isUserInteractionEnabled = false
        swipe.host = host
        return host
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}

extension View {
    /// A vertical swipe changes the brightness on the left half and the volume on the right; up raises it. A drag that starts
    /// near the screen's edges, where the system's own gestures live (Notification Centre, Control Centre, the home
    /// indicator), or that goes sideways first, is left alone. `onStart` runs when a swipe takes hold.
    func swipeForLevels(_ swipe: PlayerSwipe, enabled: Bool, size: CGSize, onStart: @escaping () -> Void) -> some View {
        modifier(SwipeForLevels(swipe: swipe, enabled: enabled, size: size, onStart: onStart))
    }
}

private struct SwipeForLevels: ViewModifier {
    let swipe: PlayerSwipe
    let enabled: Bool
    let size: CGSize
    let onStart: () -> Void

    /// Decided once per drag: the level it changes, or `.ignored` for a drag that isn't a swipe for one.
    @State private var decision: Decision = .undecided

    private enum Decision {
        case undecided, ignored
        case swiping(SwipeLevel)
    }

    func body(content: Content) -> some View {
        content.simultaneousGesture(
            DragGesture(minimumDistance: 12)
                .onChanged { drag in
                    guard enabled, size.height > 0 else { return }
                    if case .undecided = decision {
                        guard let kind = level(for: drag) else {
                            decision = .ignored
                            return
                        }
                        decision = .swiping(kind)
                        swipe.begin(kind)
                        onStart()
                    }
                    guard case .swiping = decision else { return }
                    swipe.move(by: -drag.translation.height / (size.height * fullSweep))
                }
                .onEnded { _ in
                    if case .swiping = decision { swipe.end() }
                    decision = .undecided
                }
        )
    }

    private func level(for drag: DragGesture.Value) -> SwipeLevel? {
        let at = drag.startLocation
        let edge = at.x < sideInset || at.x > size.width - sideInset || at.y < topBottomInset || at.y > size.height - topBottomInset
        guard !edge, abs(drag.translation.height) > abs(drag.translation.width) else { return nil }
        return at.x < size.width / 2 ? .brightness : .volume
    }

    /// The share of the player's height a swipe covers to go from nothing to full.
    private let fullSweep = 0.6
    private let sideInset: CGFloat = 24
    private let topBottomInset: CGFloat = 40
}

/// The level a swipe set, on the side being swiped: a Liquid Glass capsule on iOS 26 (a `Brand.scrim` one before), filled
/// from the bottom in white like Control Center, with the symbol at its foot in black once the fill reaches it. Never aqua.
struct SwipeLevelIndicator: View {
    @ObservedObject var swipe: PlayerSwipe

    var body: some View {
        HStack {
            if swipe.kind == .volume { Spacer(minLength: 0) }
            if let kind = swipe.kind, swipe.visible {
                capsule(kind)
                    .padding(.horizontal, Space.s6)
                    .transition(.opacity)
            }
            if swipe.kind == .brightness { Spacer(minLength: 0) }
        }
        .animation(.easeOut(duration: swipe.visible ? 0 : 0.3), value: swipe.visible)
        .allowsHitTesting(false)
    }

    private func capsule(_ kind: SwipeLevel) -> some View {
        let level = swipe.level
        let percent = Int32((level * 100).rounded())
        return ZStack(alignment: .bottom) {
            GeometryReader { proxy in
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    Rectangle().fill(.white).frame(height: proxy.size.height * level)
                }
            }
            Image(systemName: symbol(kind, level))
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(level >= iconCovered ? Color.black : Color.white)
                .frame(height: 28)
                .padding(.bottom, Space.s3)
        }
        .frame(width: 52, height: 160)
        .clipShape(Capsule())
        .modifier(SwipeCapsuleBackground())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(kind == .volume ? Strings.shared.volumeLevel(percent: percent) : Strings.shared.brightnessLevel(percent: percent))
    }

    private func symbol(_ kind: SwipeLevel, _ level: Double) -> String {
        switch kind {
        case .volume:
            if level <= 0.001 { return "speaker.slash.fill" }
            if level < 0.34 { return "speaker.wave.1.fill" }
            if level < 0.67 { return "speaker.wave.2.fill" }
            return "speaker.wave.3.fill"
        case .brightness:
            return level < 0.5 ? "sun.min.fill" : "sun.max.fill"
        }
    }

    /// The fill's level at which it covers the symbol at the capsule's foot.
    private let iconCovered = 0.16
}

/// Real Liquid Glass on iOS 26, the scrim before that.
private struct SwipeCapsuleBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular, in: Capsule())
        } else {
            content.background(Capsule().fill(Brand.scrim))
        }
    }
}
