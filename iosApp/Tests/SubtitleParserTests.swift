import XCTest
@testable import VibeyFin

final class SubtitleParserTests: XCTestCase {
    func testReadsSrtBlocksAndDropsMarkup() {
        let text = "\u{FEFF}1\n00:00:01,500 --> 00:00:03,000\n<i>Hello</i>\nthere\n\n2\n00:01:00,000 --> 00:01:02,250\n{\\an8}Second\n"
        let cues = SubtitleParser.parse(text)
        XCTAssertEqual(cues.count, 2)
        XCTAssertEqual(cues[0], SubtitleCue(start: 1.5, end: 3, text: "Hello\nthere"))
        XCTAssertEqual(cues[1], SubtitleCue(start: 60, end: 62.25, text: "Second"))
    }

    func testReadsAFileInAWindowsCodePage() throws {
        let text = "1\n00:00:01,000 --> 00:00:02,000\nCaf\u{E9} \u{FC}ber\n"
        let data = try XCTUnwrap(text.data(using: .windowsCP1252))
        XCTAssertNil(String(data: data, encoding: .utf8))
        XCTAssertEqual(SubtitleParser.parse(SubtitleParser.decode(data)).map(\.text), ["Caf\u{E9} \u{FC}ber"])
    }

    func testReadsAUtf16FileWithABom() throws {
        let data = try XCTUnwrap("1\n00:00:01,000 --> 00:00:02,000\nHi\n".data(using: .utf16))
        XCTAssertEqual(SubtitleParser.parse(SubtitleParser.decode(data)).map(\.text), ["Hi"])
    }

    func testReadsWebVttWithHeaderAndCueSettings() {
        let text = "WEBVTT\n\n00:01.000 --> 00:02.000 align:start\nLine\n\n01:00:00.000 --> 01:00:01.000\nHour\n"
        let cues = SubtitleParser.parse(text)
        XCTAssertEqual(cues.map(\.text), ["Line", "Hour"])
        XCTAssertEqual(cues[1].start, 3600)
    }

    func testActiveCuesAtATime() {
        let cues = SubtitleParser.parse("1\n00:00:01,000 --> 00:00:05,000\nA\n\n2\n00:00:02,000 --> 00:00:03,000\nB\n")
        XCTAssertEqual(SubtitleParser.active(in: cues, at: 0.5).count, 0)
        XCTAssertEqual(SubtitleParser.active(in: cues, at: 2.5).map(\.text), ["A", "B"])
        XCTAssertEqual(SubtitleParser.active(in: cues, at: 4).map(\.text), ["A"])
        XCTAssertEqual(SubtitleParser.active(in: cues, at: 5).count, 0)
    }

    func testALongCueStaysActiveUnderManyShortOnes() {
        var cues = [SubtitleCue(start: 0, end: 60, text: "Sign")]
        for index in 1...10 { cues.append(SubtitleCue(start: Double(index), end: Double(index) + 0.5, text: "Line \(index)")) }
        XCTAssertEqual(SubtitleParser.active(in: cues, at: 20).map(\.text), ["Sign"])
    }

    func testReadsAssDialogue() {
        let text = "[Script Info]\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
            + "Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,{\\an8}Hello, there\\Nfriend\n"
        let cues = SubtitleParser.parse(text)
        XCTAssertEqual(cues, [SubtitleCue(start: 1.5, end: 3, text: "Hello, there\nfriend")])
    }

    func testSkipsGarbage() {
        XCTAssertTrue(SubtitleParser.parse("not a subtitle file").isEmpty)
    }
}
