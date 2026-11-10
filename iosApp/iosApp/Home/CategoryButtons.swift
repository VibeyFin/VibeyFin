import SwiftUI
import Shared

/// Shows, Movies, New and Categories as one scrolling row of capsules. Every one opens something: the first three
/// open their page; Categories carries a chevron and opens a list of genres first, which is how it differs. None of
/// them stays selected, because Home itself is never filtered.
struct CategoryButtonRow: View {
    let buttons: [CategoryButton]
    let onTap: (CategoryButton) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Space.s2) {
                ForEach(buttons, id: \.kind) { button in
                    FilterChipButton(label: button.label.titleCased(), opensList: button.opensSheet) { onTap(button) }
                        .accessibilityLabel(button.opensSheet ? Strings.shared.opensList(label: button.label) : button.label)
                }
            }
            .screenMargin()
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Strings.shared.filterHome)
    }
}

/// Sort and Genre under a category page's title. One that changes the page shows filled (`ink` with `bg` text, like
/// the season buttons); each has a chevron, as it opens a list.
struct ListChipRow: View {
    let chips: [ListChip]
    let onTap: (ListChip) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Space.s2) {
                ForEach(chips, id: \.kind) { chip in
                    // Sort and Genre open a list and have a chevron; Movies and Shows switch on and off.
                    FilterChipButton(label: chip.label, active: chip.active, opensList: chip.opensList) { onTap(chip) }
                }
            }
            .screenMargin()
        }
    }
}
