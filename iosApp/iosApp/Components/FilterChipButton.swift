import SwiftUI

/// The one filter chip, on Home, a category page and Search. One that opens a list has a chevron. 44pt to touch, 36pt to look at.
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
            .foregroundStyle(active ? Brand.onSparkSoft : Color.primary)
            .padding(.horizontal, Space.s4)
            .frame(height: 36)
            .background(Capsule().fill(active ? Brand.sparkSoft : Color(.tertiarySystemBackground)))
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
    }
}
