import SwiftUI
import Shared

/// One row of a [PickerSheetView]: `id` is handed back when it is chosen.
struct PickerRow: Identifiable {
    let id: String
    let label: String
    var selected = false
    /// Small text at the end of the row, such as how many titles it holds.
    var detail: String?
    /// What VoiceOver says for the row, when the label alone is not enough.
    var description: String?
}

/// A list to pick from in a sheet: a title and one row per choice, a check on the one in use. Loading is grey lines,
/// an empty list says so, a failure offers Try again. Used by Home's Categories and by a category page's Sort and
/// Genre. The player picks tracks from a `Menu`, but these lists load when opened and need those states.
struct PickerSheetView: View {
    let title: String
    let rows: [PickerRow]
    let loading: Bool
    let message: String?
    let emptyText: String?
    let onPick: (String) -> Void
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(title)
                .sheetTitle()
                .accessibilityAddTraits(.isHeader)
                .padding(.horizontal, Space.s4)
                .padding(.top, Space.s5)
            if loading {
                VStack(alignment: .leading, spacing: Space.s3) {
                    ForEach(0..<5, id: \.self) { _ in
                        SkeletonLine(font: .body, width: 160)
                    }
                }
                .padding(.horizontal, Space.s4)
                Spacer(minLength: 0)
            } else if let message {
                LoadErrorView(message: message, onRetry: onRetry)
                    .padding(.horizontal, Space.s4)
                Spacer(minLength: 0)
            } else if let emptyText {
                Text(emptyText)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, Space.s4)
                Spacer(minLength: 0)
            } else {
                List(rows) { row in
                    Button { onPick(row.id) } label: {
                        HStack(spacing: Space.s3) {
                            Text(row.label)
                                .foregroundStyle(.primary)
                            Spacer()
                            if let detail = row.detail {
                                Text(detail)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            // Only the chosen row has a mark, so the state is not carried by colour.
                            if row.selected {
                                Image(systemName: "checkmark").foregroundStyle(.primary)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(row.description ?? row.label)
                    .accessibilityAddTraits(row.selected ? [.isButton, .isSelected] : .isButton)
                }
                .listStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(.systemBackground))
        .presentationDetents([.medium, .large])
    }
}
