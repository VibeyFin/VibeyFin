import Observation
import QuartzCore

/// Calls back once per screen refresh while started.
protocol ShimmerTicker: AnyObject {
    func start(_ onTick: @escaping () -> Void)
    func stop()
}

/// The one clock every shimmer band reads, so all of them light the same spot at once. It only runs while a band is on screen.
@MainActor
@Observable
final class SkeletonShimmerClock {
    static let shared: SkeletonShimmerClock = SkeletonShimmerClock(ticker: DisplayLinkTicker(), now: { Date() })

    private(set) var elapsedMillis: Int64 = 0

    @ObservationIgnored private let ticker: ShimmerTicker
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private var viewers: Int = 0

    var isRunning: Bool { viewers > 0 }

    init(ticker: ShimmerTicker, now: @escaping () -> Date) {
        self.ticker = ticker
        self.now    = now
    }

    func join() {
        viewers += 1
        guard viewers == 1 else { return }
        tick()
        ticker.start { [weak self] in
            MainActor.assumeIsolated { self?.tick() }
        }
    }

    func leave() {
        guard viewers > 0 else { return }
        viewers -= 1
        if viewers == 0 { ticker.stop() }
    }

    private func tick() {
        elapsedMillis = Int64(now().timeIntervalSinceReferenceDate * 1000)
    }
}

final class DisplayLinkTicker: NSObject, ShimmerTicker {
    private var displayLink: CADisplayLink?
    private var onTick: (() -> Void)?

    func start(_ onTick: @escaping () -> Void) {
        let link: CADisplayLink = CADisplayLink(target: self, selector: #selector(step))

        self.onTick = onTick
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        onTick      = nil
    }

    @objc private func step() {
        onTick?()
    }
}
