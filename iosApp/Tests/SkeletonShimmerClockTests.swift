import XCTest
@testable import VibeyFin

private final class FakeTicker: ShimmerTicker {
    private(set) var running: Bool = false
    private(set) var starts: Int  = 0
    private var onTick: (() -> Void)?

    func start(_ onTick: @escaping () -> Void) {
        running     = true
        starts     += 1
        self.onTick = onTick
    }

    func stop() {
        running = false
        onTick  = nil
    }

    func fire() { onTick?() }
}

@MainActor
final class SkeletonShimmerClockTests: XCTestCase {
    func testTheClockStartsWhenTheFirstSkeletonAppears() {
        let ticker: FakeTicker          = FakeTicker()
        let clock: SkeletonShimmerClock = SkeletonShimmerClock(ticker: ticker, now: { Date(timeIntervalSinceReferenceDate: 1) })

        clock.join()

        XCTAssertTrue(ticker.running)
        XCTAssertEqual(clock.elapsedMillis, 1_000)
    }

    func testMoreSkeletonsShareTheOneRunningClock() {
        let ticker: FakeTicker          = FakeTicker()
        let clock: SkeletonShimmerClock = SkeletonShimmerClock(ticker: ticker, now: { Date() })

        clock.join()
        clock.join()
        clock.leave()

        XCTAssertEqual(ticker.starts, 1)
        XCTAssertTrue(ticker.running)
    }

    func testTheClockStopsWhenTheLastSkeletonGoes() {
        let ticker: FakeTicker          = FakeTicker()
        let clock: SkeletonShimmerClock = SkeletonShimmerClock(ticker: ticker, now: { Date() })

        clock.join()
        clock.join()
        clock.leave()
        clock.leave()

        XCTAssertFalse(ticker.running)
        XCTAssertFalse(clock.isRunning)
    }

    func testAnExtraLeaveDoesNotStopALaterSkeleton() {
        let ticker: FakeTicker          = FakeTicker()
        let clock: SkeletonShimmerClock = SkeletonShimmerClock(ticker: ticker, now: { Date() })

        clock.leave()
        clock.join()

        XCTAssertTrue(ticker.running)
    }

    func testEachTickReadsTheTime() {
        var seconds: TimeInterval       = 2
        let ticker: FakeTicker          = FakeTicker()
        let clock: SkeletonShimmerClock = SkeletonShimmerClock(ticker: ticker, now: { Date(timeIntervalSinceReferenceDate: seconds) })

        clock.join()
        seconds = 2.5
        ticker.fire()

        XCTAssertEqual(clock.elapsedMillis, 2_500)
    }
}
