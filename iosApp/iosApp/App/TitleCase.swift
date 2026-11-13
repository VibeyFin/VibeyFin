import Foundation

extension String {
    /// Apple's headline-style capitalisation for button, tab and title labels on iOS: every word
    /// starts with a capital except articles, coordinating conjunctions and prepositions of four
    /// letters or fewer, and the first and last word always do. Only the first letter of a word is
    /// changed, and a word that already has a capital or a digit after its first letter ("iPhone",
    /// "S2:E4") is left alone. Not for errors, help text, server names or anything the user typed.
    func titleCased() -> String {
        titleCased(forLanguage: ActiveLanguage.shared.tag)
    }

    /// Title case is an English convention, so any other language is returned as written.
    func titleCased(forLanguage languageTag: String) -> String {
        guard languageTag == "en" || languageTag.hasPrefix("en-") else { return self }
        let smallWords: Set<String> = [
            "a", "an", "the", "and", "but", "for", "nor", "or", "so", "yet",
            "as", "at", "by", "from", "in", "into", "of", "off", "on", "onto",
            "out", "over", "per", "to", "up", "via", "with",
        ]
        // Split on single spaces so the original spacing survives.
        var words = components(separatedBy: " ")
        let wordIndexes = words.indices.filter { !words[$0].isEmpty }
        guard let first = wordIndexes.first, let last = wordIndexes.last else { return self }
        for index in wordIndexes {
            let word = words[index]
            let bare = word.trimmingCharacters(in: .punctuationCharacters).lowercased()
            let isEdge = index == first || index == last
            if !isEdge && smallWords.contains(bare) { continue }
            words[index] = word.capitalizingFirstLetter()
        }
        return words.joined(separator: " ")
    }

    private func capitalizingFirstLetter() -> String {
        guard let firstIndex = firstIndex(where: { $0.isLetter }) else { return self }
        let rest = self[index(after: firstIndex)...]
        if rest.contains(where: { $0.isUppercase || $0.isNumber }) { return self }
        return String(self[..<firstIndex]) + self[firstIndex].uppercased() + String(rest)
    }
}
