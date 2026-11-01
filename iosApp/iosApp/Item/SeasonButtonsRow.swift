import SwiftUI
import Shared

struct SeasonButton<ID: Hashable> {
    let id: ID
    let label: String
}

/// A show's seasons as a row of capsule buttons (the selected one filled `ink` with `bg` text). It opens scrolled to the selected one.
struct SeasonButtonsRow<ID: Hashable>: View {
    let seasons: [SeasonButton<ID>]
    let selectedId: ID?
    /// The page's side margin: the row runs to the screen's edges and starts in line with it.
    let margin: CGFloat
    let onSelect: (ID) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s2) {
                    ForEach(seasons, id: \.id) { season in
                        let selected = season.id == selectedId
                        Button { onSelect(season.id) } label: {
                            Text(season.label)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(selected ? Color(.systemBackground) : Color.primary)
                                .padding(.horizontal, Space.s4)
                                .frame(height: 36)
                                .background(Capsule().fill(selected ? Color.primary : Color(.tertiarySystemBackground)))
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableButtonStyle())
                        .id(season.id)
                        .accessibilityLabel(Strings.shared.seasonDescription(name: season.label, selected: selected))
                        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                    }
                }
                .padding(.horizontal, margin)
            }
            .padding(.horizontal, -margin)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Strings.shared.seasonsDescription(count: Int32(seasons.count)))
            // Opens with the selected season in view.
            .onAppear {
                if let id = selectedId { proxy.scrollTo(id, anchor: .center) }
            }
        }
    }
}
