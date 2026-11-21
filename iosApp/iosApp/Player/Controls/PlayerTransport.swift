import Shared
import SwiftUI

/// The centre transport: skip back, play or pause, skip forward. Glass discs over the video (a scrim
/// disc before iOS 26), white symbols; the skip glyphs carry the number of seconds from the settings.
struct PlayerTransport: View {
    let isPlaying: Bool
    let skipBackSeconds: Int
    let skipForwardSeconds: Int
    let onSkipBack: () -> Void
    let onTogglePlay: () -> Void
    let onSkipForward: () -> Void

    // Fixed discs would not grow with Dynamic Type; these scale with the glyph's text style.
    @ScaledMetric(relativeTo: .title2) private var skipDiameter: CGFloat = 56
    @ScaledMetric(relativeTo: .largeTitle) private var playDiameter: CGFloat = 76

    var body: some View {
        HStack(spacing: Space.s6) {
            disc(
                symbol: Self.symbol("gobackward", seconds: skipBackSeconds),
                label: Strings.shared.skipBackLabel(seconds: Int32(skipBackSeconds)),
                diameter: skipDiameter,
                font: .title2,
                action: onSkipBack
            )
            disc(
                symbol: isPlaying ? "pause.fill" : "play.fill",
                label: isPlaying ? Strings.shared.pause : Strings.shared.play,
                diameter: playDiameter,
                font: .largeTitle,
                action: onTogglePlay
            )
            disc(
                symbol: Self.symbol("goforward", seconds: skipForwardSeconds),
                label: Strings.shared.skipForwardLabel(seconds: Int32(skipForwardSeconds)),
                diameter: skipDiameter,
                font: .title2,
                action: onSkipForward
            )
        }
    }

    /// The numbered symbols exist only for these steps; anything else falls back to the plain arrow.
    static func symbol(_ base: String, seconds: Int) -> String {
        [5, 10, 15, 30, 45, 60].contains(seconds) ? "\(base).\(seconds)" : base
    }

    @ViewBuilder
    private func disc(symbol: String, label: String, diameter: CGFloat, font: Font, action: @escaping () -> Void) -> some View {
        let glyph = Image(systemName: symbol)
            .font(font.weight(.semibold))
            .foregroundStyle(Brand.onMedia)
            .frame(width: diameter, height: diameter)
            .contentShape(Circle())
        if #available(iOS 26.0, *) {
            Button(action: action) { glyph }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .accessibilityLabel(label)
        } else {
            Button(action: action) { glyph.background(Circle().fill(Brand.scrim)) }
                .buttonStyle(PressableButtonStyle())
                .accessibilityLabel(label)
        }
    }
}

/// The seconds skipped so far ("30s"), by the side the skips go.
struct SeekFlash: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.title3.weight(.semibold))
            .monospacedDigit()
            .foregroundStyle(Brand.onMedia)
            .padding(.horizontal, Space.s4)
            .padding(.vertical, Space.s2)
            .background(Capsule().fill(Brand.scrim))
            .accessibilityHidden(true)
    }
}
