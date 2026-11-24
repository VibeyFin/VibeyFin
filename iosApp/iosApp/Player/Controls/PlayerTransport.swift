import Shared
import SwiftUI

/// The centre transport: skip back, play or pause, skip forward, as glass discs (scrim discs before iOS 26); the skip
/// glyphs carry the number of seconds from the settings.
struct PlayerTransport: View {
    let isPlaying: Bool
    let skipBackSeconds: Int
    let skipForwardSeconds: Int
    let onSkipBack: () -> Void
    let onTogglePlay: () -> Void
    let onSkipForward: () -> Void

    // Fixed sizes would not grow with Dynamic Type; these scale with the glyph's text style.
    @ScaledMetric(relativeTo: .title2) private var skipDiameter: CGFloat = 56
    @ScaledMetric(relativeTo: .largeTitle) private var playDiameter: CGFloat = 76

    var body: some View {
        HStack(spacing: Space.s6) {
            glassDisc(symbol: Self.symbol("gobackward", seconds: skipBackSeconds), font: .title2, diameter: skipDiameter, action: onSkipBack)
                .accessibilityLabel(Strings.shared.skipBackLabel(seconds: Int32(skipBackSeconds)))
            glassDisc(symbol: isPlaying ? "pause.fill" : "play.fill", font: .largeTitle, diameter: playDiameter, action: onTogglePlay)
                .accessibilityLabel(isPlaying ? Strings.shared.pause : Strings.shared.play)
            glassDisc(symbol: Self.symbol("goforward", seconds: skipForwardSeconds), font: .title2, diameter: skipDiameter, action: onSkipForward)
                .accessibilityLabel(Strings.shared.skipForwardLabel(seconds: Int32(skipForwardSeconds)))
        }
    }

    /// The numbered symbols exist only for these steps; anything else falls back to the plain arrow.
    static func symbol(_ base: String, seconds: Int) -> String {
        [5, 10, 15, 30, 45, 60].contains(seconds) ? "\(base).\(seconds)" : base
    }

    @ViewBuilder
    private func glassDisc(symbol: String, font: Font, diameter: CGFloat, action: @escaping () -> Void) -> some View {
        let glyph = Image(systemName: symbol)
            .font(font.weight(.semibold))
            .foregroundStyle(Brand.onMedia)
            .frame(width: diameter, height: diameter)
            .contentShape(Circle())
        if #available(iOS 26.0, *) {
            Button(action: action) { glyph }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
        } else {
            Button(action: action) { glyph.background(Circle().fill(Brand.scrim)) }
                .buttonStyle(PressableButtonStyle())
        }
    }
}

/// The running total of skips in a row, as YouTube draws it ("+30      >", "<      −30"): the number and one chevron, no
/// background. On every tap the number pops a little bigger and settles, the chevron recoils towards it and eases back out,
/// and a faint second chevron closes in from behind. Under Reduce Motion it only shows.
struct SeekIndicator: View {
    let forward: Bool
    let text: String
    let taps: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pop: CGFloat = 1
    /// 1 to 0 as the chevron eases back out from the number.
    @State private var recoil: CGFloat = 0
    /// 0 to 1 as the faint chevron behind closes in.
    @State private var flight: CGFloat = 1

    private var ahead: CGFloat { forward ? 1 : -1 }

    var body: some View {
        HStack(spacing: Space.s7) {
            if !forward { chevrons }
            Text(text)
                .font(.title3.weight(.medium))
                .monospacedDigit()
                .scaleEffect(pop)
            if forward { chevrons }
        }
        .foregroundStyle(Brand.onMedia)
        .shadow(color: Brand.scrim, radius: 2, y: 1)
        .accessibilityHidden(true)
        .onAppear { animateTap(count: 1) }
        .onChange(of: forward) { _, _ in animateTap(count: 1) }
        .onChange(of: taps) { _, count in animateTap(count: count) }
    }

    private var chevrons: some View {
        ZStack {
            if flight < 1 {
                chevron
                    .offset(x: -ahead * (1 - flight) * 7)
                    .opacity(0.6 * flight)
            }
            chevron
                .offset(x: -ahead * recoil * 4)
        }
    }

    private var chevron: some View {
        Image(systemName: forward ? "chevron.forward" : "chevron.backward")
            .font(.title3.weight(.semibold))
    }

    private func animateTap(count: Int) {
        guard !reduceMotion else { return }
        withAnimation(.easeOut(duration: 0.08)) {
            pop = 1.15
        } completion: {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.45)) { pop = 1 }
        }
        withoutAnimation { recoil = 1 }
        withAnimation(.easeOut(duration: 0.12)) { recoil = 0 }
        if count > 1 {
            withoutAnimation { flight = 0 }
            withAnimation(.easeOut(duration: 0.2)) { flight = 1 }
        }
    }

    private func withoutAnimation(_ change: () -> Void) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction, change)
    }
}
