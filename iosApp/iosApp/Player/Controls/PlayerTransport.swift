import Shared
import SwiftUI

/// The centre transport: play or pause on a glass disc (a scrim disc before iOS 26), with Previous and Next episode either
/// side for an episode, as plain symbols. Double taps on the video skip; VoiceOver gets the skips as actions on play.
struct PlayerTransport: View {
    let isPlaying: Bool
    let episodeControls: Bool
    /// The episode before this one, read with Previous; nil dims it.
    let previousEpisode: String?
    /// The episode after this one, read with Next; nil dims it.
    let nextEpisode: String?
    let skipBackSeconds: Int
    let skipForwardSeconds: Int
    let onPreviousEpisode: () -> Void
    let onTogglePlay: () -> Void
    let onNextEpisode: () -> Void
    let onSkipBack: () -> Void
    let onSkipForward: () -> Void

    // Fixed sizes would not grow with Dynamic Type; these scale with the glyph's text style.
    @ScaledMetric(relativeTo: .title) private var episodeSide: CGFloat = 56
    @ScaledMetric(relativeTo: .largeTitle) private var playDiameter: CGFloat = 76

    var body: some View {
        HStack(spacing: Space.s7) {
            if episodeControls {
                episodeButton(symbol: "backward.end.fill", label: Strings.shared.previousEpisode, episode: previousEpisode, action: onPreviousEpisode)
            }
            playButton
                .accessibilityAction(named: Strings.shared.skipBackLabel(seconds: Int32(skipBackSeconds)), onSkipBack)
                .accessibilityAction(named: Strings.shared.skipForwardLabel(seconds: Int32(skipForwardSeconds)), onSkipForward)
            if episodeControls {
                episodeButton(symbol: "forward.end.fill", label: Strings.shared.nextEpisode, episode: nextEpisode, action: onNextEpisode)
            }
        }
    }

    /// The numbered symbols exist only for these steps; anything else falls back to the plain arrow.
    static func symbol(_ base: String, seconds: Int) -> String {
        [5, 10, 15, 30, 45, 60].contains(seconds) ? "\(base).\(seconds)" : base
    }

    @ViewBuilder
    private var playButton: some View {
        let glyph = Image(systemName: isPlaying ? "pause.fill" : "play.fill")
            .font(.largeTitle.weight(.semibold))
            .foregroundStyle(Brand.onMedia)
            .frame(width: playDiameter, height: playDiameter)
            .contentShape(Circle())
        let label = isPlaying ? Strings.shared.pause : Strings.shared.play
        if #available(iOS 26.0, *) {
            Button(action: onTogglePlay) { glyph }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .accessibilityLabel(label)
        } else {
            Button(action: onTogglePlay) { glyph.background(Circle().fill(Brand.scrim)) }
                .buttonStyle(PressableButtonStyle())
                .accessibilityLabel(label)
        }
    }

    private func episodeButton(symbol: String, label: String, episode: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.title.weight(.semibold))
                .foregroundStyle(Brand.onMedia)
                .shadow(color: Brand.scrim, radius: 4)
                .frame(width: episodeSide, height: episodeSide)
                .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
        .disabled(episode == nil)
        .opacity(episode == nil ? 0.38 : 1)
        .accessibilityLabel(label)
        .accessibilityHint(episode ?? "")
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
