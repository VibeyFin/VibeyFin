import XCTest
@testable import VibeyFin

final class TrickplayAimTests: XCTestCase {
    func testTheFirstPositionAims() {
        var aim = TrickplayAim()
        XCTAssertTrue(aim.needsAim(tile: 0, limited: false))
    }

    func testStayingInTheSameTileDoesNotAimAgain() {
        var aim = TrickplayAim()
        _ = aim.needsAim(tile: 2, limited: false)
        XCTAssertFalse(aim.needsAim(tile: 2, limited: false))
    }

    func testAnotherTileAimsAgain() {
        var aim = TrickplayAim()
        _ = aim.needsAim(tile: 2, limited: false)
        XCTAssertTrue(aim.needsAim(tile: 3, limited: false))
    }

    func testAChangedConnectionAimsAgainInTheSameTile() {
        var aim = TrickplayAim()
        _ = aim.needsAim(tile: 2, limited: false)
        XCTAssertTrue(aim.needsAim(tile: 2, limited: true))
        XCTAssertFalse(aim.needsAim(tile: 2, limited: true))
    }
}

final class TileByteCacheTests: XCTestCase {
    private func bytes(_ count: Int) -> Data { Data(repeating: 1, count: count) }

    func testItHoldsWhatFitsAndReturnsIt() {
        var cache = TileByteCache(limit: 100)
        cache.insert(bytes(40), for: 0)
        cache.insert(bytes(40), for: 1)
        XCTAssertEqual(cache.value(for: 0)?.count, 40)
        XCTAssertEqual(cache.totalBytes, 80)
    }

    func testTheLeastRecentlyUsedGoesFirstWhenFull() {
        var cache = TileByteCache(limit: 100)
        cache.insert(bytes(40), for: 0)
        cache.insert(bytes(40), for: 1)
        _ = cache.value(for: 0)
        cache.insert(bytes(40), for: 2)
        XCTAssertNil(cache.value(for: 1))
        XCTAssertNotNil(cache.value(for: 0))
        XCTAssertNotNil(cache.value(for: 2))
        XCTAssertLessThanOrEqual(cache.totalBytes, 100)
    }

    func testATileLargerThanTheLimitIsNotKept() {
        var cache = TileByteCache(limit: 100)
        cache.insert(bytes(101), for: 0)
        XCTAssertNil(cache.value(for: 0))
        XCTAssertEqual(cache.totalBytes, 0)
    }

    func testInsertingAgainReplacesTheBytesWithoutCountingTwice() {
        var cache = TileByteCache(limit: 100)
        cache.insert(bytes(60), for: 0)
        cache.insert(bytes(30), for: 0)
        XCTAssertEqual(cache.totalBytes, 30)
        XCTAssertEqual(cache.order, [0])
    }

    func testRemovingAndClearingFreeTheBytes() {
        var cache = TileByteCache(limit: 100)
        cache.insert(bytes(30), for: 0)
        cache.insert(bytes(30), for: 1)
        cache.remove(0)
        XCTAssertEqual(cache.totalBytes, 30)
        cache.removeAll()
        XCTAssertEqual(cache.totalBytes, 0)
        XCTAssertNil(cache.value(for: 1))
    }
}
