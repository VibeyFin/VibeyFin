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

/// A list to pick from in a sheet: a title and one row per choice, the one in use lit as a soft pill. Loading is grey lines,
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
    /// A search field above the rows (the People filter): what was typed and who hears each change.
    var search: Binding<String>? = nil
    var searchHint = ""

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(title)
                .sheetTitle()
                .accessibilityAddTraits(.isHeader)
                .padding(.horizontal, Space.s4)
                .padding(.top, Space.s5)
            if let search {
                HStack(spacing: Space.s2) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField(searchHint, text: search)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                .padding(.horizontal, Space.s3)
                .frame(height: 44)
                .background(Capsule().fill(Color(.tertiarySystemBackground)))
                .padding(.horizontal, Space.s4)
            }
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
                        }
                        .fontWeight(row.selected ? .semibold : .regular)
                        // The chosen row is lit as a soft rounded pill in the accent tint, with no tick.
                        .padding(.horizontal, Space.s3)
                        .padding(.vertical, Space.s2)
                        .background(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).fill(row.selected ? Brand.accentSoft : Color.clear))
                        .contentShape(Rectangle())
                    }
                    .listRowBackground(Color.clear)
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
