import SwiftUI
import Shared

private struct SkeletonShimmerWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat? = nil
}

extension EnvironmentValues {
    /// The width the shimmer sweeps across, the screen's, set once at the root; nil until it is measured.
    var skeletonShimmerWidth: CGFloat? {
        get { self[SkeletonShimmerWidthKey.self] }
        set { self[SkeletonShimmerWidthKey.self] = newValue }
    }
}

extension View {
    /// The one shimmer every skeleton shape wears: a soft grey light over the shape, placed by the screen's clock and position so it
    /// lines up across every placeholder on the screen. Nothing when the system asks for Reduce Motion.
    func skeletonShimmer() -> some View {
        modifier(SkeletonShimmerEffect())
    }
}

/// Where the band falls inside one shape: the screen's band centre and half-width, the shape's left edge on the screen and its width.
enum ShimmerBand {
    static func points(center: CGFloat, half: CGFloat, minX: CGFloat, width: CGFloat) -> (start: UnitPoint, end: UnitPoint)? {
        guard width > 0 else { return nil }
        let local = center - minX
        return (UnitPoint(x: (local - half) / width, y: 0.5), UnitPoint(x: (local + half) / width, y: 0.5))
    }
}

private struct SkeletonShimmerEffect: ViewModifier {
    @Environment(\.skeletonShimmerWidth) private var sweepWidth
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        if let sweepWidth, !reduceMotion {
            content.overlay {
                SkeletonShimmerBand(sweepWidth: sweepWidth).mask { content }
            }
        } else {
            content
        }
    }
}

private struct SkeletonShimmerBand: View {
    let sweepWidth: CGFloat

    var body: some View {
        GeometryReader { proxy in
            let frame = proxy.frame(in: .global)
            // Every band reads the same clock, so the light is in the same place on every shape at once.
            TimelineView(.animation) { timeline in
                let elapsed = Int64(timeline.date.timeIntervalSinceReferenceDate * 1000)
                let center = CGFloat(SkeletonShimmer.shared.bandCenter(elapsedMillis: elapsed, sweepWidth: Float(sweepWidth)))
                let half = CGFloat(SkeletonShimmer.shared.bandHalfWidth(sweepWidth: Float(sweepWidth)))
                if let points = ShimmerBand.points(center: center, half: half, minX: frame.minX, width: frame.width) {
                    LinearGradient(colors: [.clear, Color(.separator), .clear], startPoint: points.start, endPoint: points.end)
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
