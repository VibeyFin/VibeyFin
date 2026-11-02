import SwiftUI
import Shared

/// Three lines of an overview and "More" when it is long; "Less" folds it back.
struct ExpandableOverview: View {
    let text: String
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .lineLimit(expanded ? nil : 3)
                .frame(maxWidth: .infinity, alignment: .leading)
            // A rough test for "has more than three lines"; the text itself is cut by the line limit either way.
            if text.count > 140 {
                Button((expanded ? Strings.shared.less : Strings.shared.more).titleCased()) {
                    expanded.toggle()
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
        }
    }
}
