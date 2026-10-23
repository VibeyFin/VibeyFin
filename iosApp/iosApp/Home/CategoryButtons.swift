import SwiftUI
import Shared

/// Shows, Movies, New and Categories as one scrolling row of capsules. Every one opens something: the first three
/// open their page; Categories carries a chevron and opens a list of genres first, which is how it differs. None of
/// them stays selected, because Home itself is never filtered. A capsule on the system background, never glass; aqua
/// is not used here.
struct CategoryButtonRow: View {
    let buttons: [CategoryButton]
    let onTap: (CategoryButton) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Space.s2) {
                ForEach(buttons, id: \.kind) { button in
                    Button { onTap(button) } label: {
                        HStack(spacing: Space.s1) {
                            Text(button.label.titleCased())
                            if button.opensSheet {
                                Image(systemName: "chevron.down").font(.caption.weight(.semibold))
                            }
                        }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                        .padding(.horizontal, Space.s4)
                        .frame(height: 36)
                        .background(Capsule().fill(Color(.tertiarySystemBackground)))
                        // 44pt to touch, 36pt to look at.
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableButtonStyle())
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
                    Button { onTap(chip) } label: {
                        HStack(spacing: Space.s1) {
                            Text(chip.label)
                            // Sort and Genre open a list and have a chevron; Movies and Shows switch on and off.
                            if chip.opensList {
                                Image(systemName: "chevron.down").font(.caption.weight(.semibold))
                            }
                        }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(chip.active ? Color(.systemBackground) : Color.primary)
                        .padding(.horizontal, Space.s4)
                        .frame(height: 36)
                        .background(Capsule().fill(chip.active ? Color.primary : Color(.tertiarySystemBackground)))
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableButtonStyle())
                }
            }
            .screenMargin()
        }
    }
}
