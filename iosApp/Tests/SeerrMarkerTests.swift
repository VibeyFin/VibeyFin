import XCTest
import Shared
@testable import VibeyFin

final class SeerrMarkerTests: XCTestCase {
    func testEachStateHasItsOwnSymbol() {
        XCTAssertEqual(SeerrMarker.symbol(.requested), "clock")
        XCTAssertEqual(SeerrMarker.symbol(.approved), "hourglass")
        XCTAssertEqual(SeerrMarker.symbol(.partlyavailable), "circle.lefthalf.filled")
        XCTAssertEqual(SeerrMarker.symbol(.available), "play.circle")
        XCTAssertEqual(SeerrMarker.symbol(.declined), "nosign")
        XCTAssertEqual(SeerrMarker.symbol(.failed), "exclamationmark.circle")
    }

    func testTheMenuOpenerHoldsOneTitleAtATime() {
        let opener = SeerrMenuOpener()
        XCTAssertNil(opener.title)
        XCTAssertNil(opener.model)

        opener.open(Self.title(603))
        XCTAssertEqual(opener.title?.tmdbId, 603)
        let first = opener.model
        XCTAssertNotNil(first)

        // The same title again keeps what was read; another one replaces it.
        opener.open(Self.title(603))
        XCTAssertTrue(opener.model === first)
        opener.open(Self.title(604))
        XCTAssertEqual(opener.title?.tmdbId, 604)
        XCTAssertFalse(opener.model === first)
    }

    private static func title(_ tmdbId: Int32) -> SeerrTitle {
        SeerrTitle(
            tmdbId: tmdbId, type: .movie, title: "Harbour Bridge", year: KotlinInt(int: 2025), posterUrl: nil, backdropUrl: nil, overview: nil,
            state: .notrequested, jellyfinItemId: nil, releaseDate: nil
        )
    }
}
