import SwiftUI
import Shared

/// The file's details, collapsed until asked for.
struct TechnicalInfoView: View {
    let lines: [TechnicalLine]
    let onSearchSubtitles: () -> Void
    let onDeleteSubtitles: () -> Void
    @State private var open = false

    var body: some View {
        DisclosureGroup(isExpanded: $open) {
            VStack(alignment: .leading, spacing: Space.s2) {
                ForEach(lines, id: \.label) { line in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(line.label).font(.caption).foregroundStyle(.secondary)
                        ForEach(Array(line.rows.enumerated()), id: \.offset) { _, row in
                            Text(row.text).font(.subheadline).foregroundStyle(.primary)
                            if let reason = row.reason {
                                Text(reason).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                        if line.searchesSubtitles {
                            TechnicalActionRow(systemImage: "magnifyingglass", label: Strings.shared.searchForSubtitles, action: onSearchSubtitles)
                        }
                        if line.deletesSubtitles {
                            TechnicalActionRow(systemImage: "trash", label: Strings.shared.deleteSubtitles, action: onDeleteSubtitles)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, Space.s2)
        } label: {
            Text(Strings.shared.technicalInfo.titleCased())
                .sectionTitle()
        }
        .tint(.secondary)
    }
}
