import SwiftUI

/// The one filter chip, on Home, a category page and Search (the mockup's FilterChips): a capsule on `surface-2`
/// with `ink`; `active` fills it `ink` with `bg` text, like the season buttons. One that opens a list has a chevron.
/// 44pt to touch, 36pt to look at. A capsule on the system background, never glass; aqua is not used here.
struct FilterChipButton: View {
    let label: String
    var active = false
    var opensList = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Space.s1) {
                Text(label)
                if opensList {
                    Image(systemName: "chevron.down").font(.caption.weight(.semibold))
                }
            }
            .font(.subheadline.weight(.medium))
            .foregroundStyle(active ? Color(.systemBackground) : Color.primary)
            .padding(.horizontal, Space.s4)
            .frame(height: 36)
            .background(Capsule().fill(active ? Color.primary : Color(.tertiarySystemBackground)))
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
    }
}
