import SwiftUI
import Shared

/// What a long press on a poster opens: the title, then Details, the Trailer (only when the title has one: a grey line holds its place
/// while the server is asked) and My List. A trailer the server holds plays in the app; one on the web opens in the system's viewer.
struct TileMenuSheetView: View {
    let state: TileMenuUiState
    let onDetails: () -> Void
    let onTrailer: (Trailer) -> Void
    let onMyList: () -> Void

    var body: some View {
        if let item = state.item {
            VStack(alignment: .leading, spacing: 0) {
                Text(item.title)
                    .sheetTitle()
                    .lineLimit(2)
                    .padding(.horizontal, Space.s4)
                    .padding(.top, Space.s5)
                    .padding(.bottom, Space.s2)
                row(Strings.shared.details, "info.circle", onDetails)
                if let trailer = state.trailer {
                    row(Strings.shared.trailer, "film") { onTrailer(trailer) }
                } else if state.checkingTrailer {
                    SkeletonLine(font: .body, width: 120)
                        .padding(.horizontal, Space.s4)
                        .frame(minHeight: 52, alignment: .leading)
                }
                if let message = state.actionMessage {
                    Text(message).font(.subheadline).foregroundStyle(.red).padding(.horizontal, Space.s4).padding(.vertical, Space.s2)
                }
                row(Strings.shared.myListAction(onList: item.isFavorite), item.isFavorite ? "checkmark" : "plus", onMyList)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemBackground))
            .presentationDetents([.height(300)])
        }
    }

    private func row(_ label: String, _ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Space.s4) {
                Image(systemName: icon).foregroundStyle(.secondary).frame(width: 24)
                Text(label).foregroundStyle(.primary)
                Spacer()
            }
            .padding(.horizontal, Space.s4)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
    }
}
