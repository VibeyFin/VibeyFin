import SwiftUI

/// An action under a line of opened Technical info, such as Search for subtitles.
struct TechnicalActionRow: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Space.s3) {
                Image(systemName: systemImage).foregroundStyle(.secondary)
                Text(label.titleCased()).foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
    }
}
