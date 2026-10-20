// Run with scripts/check-title-case.sh. No Xcode needed: TitleCase.swift only imports Foundation.
import Foundation

var failures = 0

func check(_ input: String, _ expected: String, line: Int = #line) {
    let actual = input.titleCased()
    if actual != expected {
        print("FAIL line \(line): \"\(input)\" -> \"\(actual)\", expected \"\(expected)\"")
        failures += 1
    }
}

// Articles, conjunctions and short prepositions stay lowercase, except first and last.
check("Continue watching", "Continue Watching")
check("Mark as watched", "Mark as Watched")
check("Add to my list", "Add to My List")
check("Sign in as another user", "Sign in as Another User")
check("Sign in", "Sign In")
check("Skip intro", "Skip Intro")
check("The long night", "The Long Night")
check("Look at that", "Look at That")
// Punctuation stays; the last word is capitalised past it.
check("Who's watching?", "Who's Watching?")
check("Add server", "Add Server")
// Words that already carry capitals or digits are left alone.
check("Open in iPhone settings", "Open in iPhone Settings")
check("Play S2:E4", "Play S2:E4")
check("Quick Connect", "Quick Connect")
check("VibeyFin", "VibeyFin")
// Edge cases.
check("", "")
check("done", "Done")
check("  spaced   out  ", "  Spaced   Out  ")
check("of", "Of")

if failures > 0 {
    print("\(failures) failure(s)")
    exit(1)
}
print("title case: all checks passed")
