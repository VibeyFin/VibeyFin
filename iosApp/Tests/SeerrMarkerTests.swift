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
        let title = SeerrTitle(
            tmdbId: 603, type: .movie, title: "Harbour Bridge", year: KotlinInt(int: 2025), posterUrl: nil, backdropUrl: nil, overview: nil,
            state: .notrequested, jellyfinItemId: nil, releaseDate: nil
        )

        opener.open(title)
        XCTAssertEqual(opener.title?.tmdbId, 603)

        opener.close()
        XCTAssertNil(opener.title)
    }
}
