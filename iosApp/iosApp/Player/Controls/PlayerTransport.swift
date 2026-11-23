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

/// The running total of skips in a row ("+30 >", "< −30"): bold text and one chevron, no background. The chevron nudges
/// the way of the skip on each tap, except under Reduce Motion.
struct SeekIndicator: View {
    let forward: Bool
    let text: String
    let taps: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var nudge: CGFloat = 0

    var body: some View {
        HStack(spacing: Space.s3) {
            if !forward { chevron }
            Text(text)
                .font(.title.weight(.bold))
                .monospacedDigit()
            if forward { chevron }
        }
        .foregroundStyle(Brand.onMedia)
        .shadow(color: Brand.scrim, radius: 3, y: 1)
        .accessibilityHidden(true)
        .onChange(of: taps) { _, _ in nudgeChevron() }
        .onAppear { nudgeChevron() }
    }

    private var chevron: some View {
        Image(systemName: forward ? "chevron.forward" : "chevron.backward")
            .font(.title2.weight(.heavy))
            .offset(x: nudge)
    }

    private func nudgeChevron() {
        guard !reduceMotion else { return }
        nudge = forward ? 6 : -6
        withAnimation(.easeOut(duration: 0.22)) { nudge = 0 }
    }
}
