import Foundation

/// One line of subtitle text and when it shows, in seconds of the video.
struct SubtitleCue: Equatable {
    let start: TimeInterval
    let end: TimeInterval
    let text: String
}

/// A subtitle file the app fetches itself, so any engine shows it the same way. `headers` carry the token.
struct SubtitleSource: Equatable {
    let url: URL
    let headers: [String: String]
}

enum SubtitleParser {
    /// Reads SRT and WebVTT. Both are blocks of a timing line (`00:01:02,500 --> 00:01:04,000`) and text lines.
    /// Markup (`<i>`, `{\an8}`) is dropped; a block without a readable timing line is skipped.
    static func parse(_ text: String) -> [SubtitleCue] {
        if text.contains("[Events]") { return parseAss(text) }
        let lines = text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").split(separator: "\n", omittingEmptySubsequences: false)
        var cues: [SubtitleCue] = []
        var index = 0
        while index < lines.count {
            guard let times = timing(String(lines[index])) else {
                index += 1
                continue
            }
            index += 1
            var body: [String] = []
            while index < lines.count, !lines[index].trimmingCharacters(in: .whitespaces).isEmpty {
                body.append(String(lines[index]))
                index += 1
            }
            let clean = strip(body.joined(separator: "\n"))
            if !clean.isEmpty { cues.append(SubtitleCue(start: times.0, end: times.1, text: clean)) }
        }
        return cues.sorted { $0.start < $1.start }
    }

    /// Reads the `Dialogue:` lines of an ASS/SSA file: the `Format:` line names the columns, the text is the last one.
    /// Positioning and styling are dropped; `\\N` is a line break.
    private static func parseAss(_ text: String) -> [SubtitleCue] {
        var columns: [String] = []
        var cues: [SubtitleCue] = []
        for raw in text.replacingOccurrences(of: "\r", with: "").split(separator: "\n") {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.hasPrefix("Format:") {
                columns = line.dropFirst(7).split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
            } else if line.hasPrefix("Dialogue:"), let startColumn = columns.firstIndex(of: "start"),
                      let endColumn = columns.firstIndex(of: "end"), let textColumn = columns.firstIndex(of: "text") {
                let fields = line.dropFirst(9).split(separator: ",", maxSplits: textColumn, omittingEmptySubsequences: false)
                guard fields.count > textColumn, let start = seconds(String(fields[startColumn])),
                      let end = seconds(String(fields[endColumn])) else { continue }
                let clean = strip(String(fields[textColumn]).replacingOccurrences(of: "\\N", with: "\n")
                    .replacingOccurrences(of: "\\n", with: "\n"))
                if !clean.isEmpty { cues.append(SubtitleCue(start: start, end: end, text: clean)) }
            }
        }
        return cues.sorted { $0.start < $1.start }
    }

    /// The cues showing at `time`. `cues` is sorted by start.
    static func active(in cues: [SubtitleCue], at time: TimeInterval) -> [SubtitleCue] {
        var low = 0
        var high = cues.count
        while low < high {
            let mid = (low + high) / 2
            if cues[mid].start <= time { low = mid + 1 } else { high = mid }
        }
        // Cues may overlap (typeset signs outlast the dialogue around them), so any earlier cue can still be showing.
        return cues[..<low].filter { $0.end > time }
    }

    private static func timing(_ line: String) -> (TimeInterval, TimeInterval)? {
        guard line.contains("-->") else { return nil }
        let parts = line.components(separatedBy: "-->")
        guard parts.count == 2, let start = seconds(parts[0]),
              let end = seconds(parts[1].trimmingCharacters(in: .whitespaces).components(separatedBy: " ").first ?? "") else { return nil }
        return (start, end)
    }

    private static func seconds(_ raw: String) -> TimeInterval? {
        let parts = raw.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".").split(separator: ":")
        guard (2...3).contains(parts.count) else { return nil }
        var total = 0.0
        for part in parts {
            guard let value = Double(part) else { return nil }
            total = total * 60 + value
        }
        return total
    }

    private static func strip(_ text: String) -> String {
        text.replacingOccurrences(of: "<[^>]*>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "\\{[^}]*\\}", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
