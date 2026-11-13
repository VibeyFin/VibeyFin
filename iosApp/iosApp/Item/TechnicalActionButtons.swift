import SwiftUI
import Shared

/// An action under a line of opened Technical info, such as Search for subtitles: the system's bordered capsule, which
/// highlights where it is held and doesn't move.
struct TechnicalActionButton: View {
    let systemImage: String
    let label: String
    var role: ButtonRole?
    let action: () -> Void

    var body: some View {
        Button(role: role, action: action) {
            Label(label.titleCased(), systemImage: systemImage)
                .font(.subheadline.weight(.medium))
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.capsule)
        .tint(role == .destructive ? .red : Color(.systemGray))
        .foregroundStyle(role == .destructive ? AnyShapeStyle(.red) : AnyShapeStyle(.primary))
        .frame(minHeight: 44)
    }
}

/// Search and Delete side by side under the Subtitles tracks; when they don't fit, Delete goes under Search.
struct SubtitleActionButtons: View {
    let searches: Bool
    let deletes: Bool
    let onSearch: () -> Void
    let onDelete: () -> Void

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Space.s2) { buttons }
            VStack(alignment: .leading, spacing: Space.s2) { buttons }
        }
        .padding(.top, Space.s2)
    }

    @ViewBuilder private var buttons: some View {
        if searches {
            TechnicalActionButton(systemImage: "magnifyingglass", label: Strings.shared.searchForSubtitles, action: onSearch)
        }
        if deletes {
            TechnicalActionButton(systemImage: "trash", label: Strings.shared.deleteSubtitles, role: .destructive, action: onDelete)
        }
    }
}
