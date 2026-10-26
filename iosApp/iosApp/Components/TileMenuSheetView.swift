import SwiftUI
import Shared

/// What a long press on a poster opens: the title, then Details, the Trailer (only when the title has one: a grey line holds its place
/// while the server is asked) and My List. A trailer the server holds plays in the app; one on the web opens in the system's viewer.
struct TileMenuSheetView: View {
    let state: TileMenuUiState
    let onDetails: () -> Void
    let onTrailer: (Trailer) -> Void
    let onMyList: () -> Void
    let onDownload: () -> Void
    let onDownloadAction: (DownloadAction) -> Void

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
                if state.offersDownload { downloadRow }
                if let message = state.actionMessage {
                    Text(message).font(.subheadline).foregroundStyle(.red).padding(.horizontal, Space.s4).padding(.vertical, Space.s2)
                }
                row(Strings.shared.myListAction(onList: item.isFavorite), item.isFavorite ? "heart.fill" : "heart", onMyList)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemBackground))
            .presentationDetents([.height(state.offersDownload ? 352 : 300)])
        }
    }

    /// Download: with no download a tap starts one (a show asks first); with one the row says where it is and opens pause, resume and remove.
    @ViewBuilder
    private var downloadRow: some View {
        let label = HStack(spacing: Space.s4) {
            DownloadBadgeView(badge: state.downloadBadge, progress: state.downloadProgress?.floatValue, tint: .secondary)
                .frame(width: 24, height: 24)
            Text(state.downloadLabel.titleCased()).foregroundStyle(.primary)
            Spacer()
        }
        .padding(.horizontal, Space.s4)
        .frame(minHeight: 52)
        .contentShape(Rectangle())
        if let download = state.download {
            Menu {
                DownloadActionButtons(actions: DownloadBridge.shared.actions(download: download), onAction: onDownloadAction)
            } label: { label }
        } else {
            Button(action: onDownload) { label }.buttonStyle(PressableButtonStyle())
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
