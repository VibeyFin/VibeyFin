import SwiftUI

enum HingeGlow {
    static let peakOpacity: Double = 0.35
    static let fadeLength: CGFloat = 240
    static let crossfadeSeconds: Double = 1.2
    private static let offBelowDegrees: Double = 45
    private static let fullFromDegrees: Double = 90
    private static let fullToDegrees: Double = 150
    private static let offAtDegrees: Double = 180

    /// Nothing at 45 degrees or less, rising to full at 90, held to 150 (the simulator's tabletop is about 129), and off again flat at 180.
    static func share(angleDegrees: Double) -> Double {
        let share: Double
        if angleDegrees < fullFromDegrees {
            share = (angleDegrees - offBelowDegrees) / (fullFromDegrees - offBelowDegrees)
        } else if angleDegrees <= fullToDegrees {
            share = 1
        } else {
            share = (offAtDegrees - angleDegrees) / (offAtDegrees - fullToDegrees)
        }
        return min(max(share, 0), 1)
    }

    static func opacity(angleDegrees: Double) -> Double {
        peakOpacity * share(angleDegrees: angleDegrees)
    }
}

/// The glow below the crease in the tabletop pose: the colours of the picture's lower edge. With none to read, nothing is drawn.
struct HingeGlowView: View {
    @ObservedObject var sampler: PictureColourSampler
    @State private var angleDegrees: Double?
    @State private var thermallyLimited: Bool = PictureColourSampler.isThermallyLimited
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    private var opacity: Double {
        if reduceTransparency || thermallyLimited || sampler.glow == nil { return 0 }
        if reduceMotion { return HingeGlow.peakOpacity }
        return angleDegrees.map { HingeGlow.opacity(angleDegrees: $0) } ?? 0
    }

    private var colours: [Color] {
        sampler.glow?.colours.map(\.color) ?? [.clear, .clear]
    }

    var body: some View {
        ZStack {
            LinearGradient(colors: colours, startPoint: .leading, endPoint: .trailing)
                .id(sampler.glow?.generation ?? 0)
                .transition(.opacity)
        }
        .mask(LinearGradient(
            stops: [
                .init(color: Brand.onMedia, location: 0),
                .init(color: Brand.onMedia.opacity(0.45), location: 0.35),
                .init(color: Brand.onMedia.opacity(0.12), location: 0.7),
                .init(color: .clear, location: 1)
            ],
            startPoint: .top,
            endPoint: .bottom
        ))
        .frame(height: HingeGlow.fadeLength)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .opacity(opacity)
        .animation(reduceMotion ? nil : .easeInOut(duration: HingeGlow.crossfadeSeconds), value: sampler.glow?.generation)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: opacity)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .hingeAngle($angleDegrees)
        .onReceive(NotificationCenter.default.publisher(for: ProcessInfo.thermalStateDidChangeNotification)) { _ in
            thermallyLimited = PictureColourSampler.isThermallyLimited
        }
        .task(id: [reduceMotion, reduceTransparency]) {
            guard !reduceTransparency else { return }
            await sampler.run(oneShot: reduceMotion)
        }
    }
}

extension View {
    @ViewBuilder
    fileprivate func hingeAngle(_ degrees: Binding<Double?>) -> some View {
        if #available(iOS 27.1, *) {
            onHingeChange { _, new in degrees.wrappedValue = new.hinge.map { $0.angle.degrees } }
        } else {
            self
        }
    }
}
