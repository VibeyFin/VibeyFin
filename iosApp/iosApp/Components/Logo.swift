import SwiftUI
import Shared

/// The Fin Play logo uses fixed brand colours on every background, not theme tokens.
enum LogoColors {
    static let mark: Color = Color(hex: 0xFFFFFF)
    static let track: Color = Color(hex: 0x3A3A3C)
    static let fill: Color = Color(hex: 0xE07A45)
}

enum LogoBar {
    static let restingFraction: Double = 0.62

    static func fraction(progress: Double?) -> Double {
        guard let progress else { return restingFraction }
        return min(max(progress, 0), 1)
    }
}

/// The Fin Play mark with its progress bar and, optionally, the wordmark under it. `progress` nil draws the bar static.
struct Logo: View {
    var progress: Double?
    var markSize: CGFloat = 96
    var showsWordmark: Bool = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: Space.s3) {
            LogoMark(fraction: LogoBar.fraction(progress: reduceMotion ? nil : progress))
                .frame(width: markSize, height: markSize)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: progress)
            if showsWordmark {
                Text(Strings.shared.appName)
                    .font(.brandDisplay)
                    .tracking(-0.4)
                    .foregroundStyle(LogoColors.mark)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.appName)
    }
}

private struct LogoMark: View {
    let fraction: Double

    var body: some View {
        Canvas { context, size in
            let scale: CGFloat = size.width / 100
            let finPath: Path = Path { path in
                path.move(to: CGPoint(x: 27, y: 17))
                path.addCurve(to: CGPoint(x: 81, y: 48), control1: CGPoint(x: 50, y: 19), control2: CGPoint(x: 69, y: 31))
                path.addLine(to: CGPoint(x: 27, y: 69))
                path.addCurve(to: CGPoint(x: 27, y: 17), control1: CGPoint(x: 34, y: 54), control2: CGPoint(x: 34, y: 32))
                path.closeSubpath()
            }
            let barRect: CGRect = CGRect(x: 23, y: 81, width: 62, height: 8)
            let fillRect: CGRect = CGRect(x: 23, y: 81, width: 62 * fraction, height: 8)
            let scaled: CGAffineTransform = CGAffineTransform(scaleX: scale, y: scale)

            context.fill(finPath.applying(scaled), with: .color(LogoColors.mark))
            context.stroke(
                finPath.applying(scaled),
                with: .color(LogoColors.mark),
                style: StrokeStyle(lineWidth: 8 * scale, lineJoin: .round)
            )
            context.fill(Path(roundedRect: barRect, cornerRadius: 4).applying(scaled), with: .color(LogoColors.track))
            context.fill(Path(roundedRect: fillRect, cornerRadius: 4).applying(scaled), with: .color(LogoColors.fill))
        }
        .accessibilityHidden(true)
    }
}
