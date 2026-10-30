import XCTest
@testable import VibeyFin

final class TitleCaseTests: XCTestCase {
    private func assertTitleCased(_ input: String, _ expected: String, line: UInt = #line) {
        XCTAssertEqual(input.titleCased(), expected, line: line)
    }

    func testSmallWordsStayLowercaseExceptAtTheEdges() {
        assertTitleCased("Continue watching", "Continue Watching")
        assertTitleCased("Mark as watched", "Mark as Watched")
        assertTitleCased("Add to my list", "Add to My List")
        assertTitleCased("Sign in as another user", "Sign in as Another User")
        assertTitleCased("Sign in", "Sign In")
        assertTitleCased("Skip intro", "Skip Intro")
        assertTitleCased("The long night", "The Long Night")
        assertTitleCased("Look at that", "Look at That")
    }

    func testPunctuationStays() {
        assertTitleCased("Who's watching?", "Who's Watching?")
        assertTitleCased("Add server", "Add Server")
    }

    func testWordsWithCapitalsOrDigitsAreLeftAlone() {
        assertTitleCased("Open in iPhone settings", "Open in iPhone Settings")
        assertTitleCased("Play S2:E4", "Play S2:E4")
        assertTitleCased("Quick Connect", "Quick Connect")
        assertTitleCased("VibeyFin", "VibeyFin")
    }

    func testEdgeCases() {
        assertTitleCased("", "")
        assertTitleCased("done", "Done")
        assertTitleCased("  spaced   out  ", "  Spaced   Out  ")
        assertTitleCased("of", "Of")
    }
}
