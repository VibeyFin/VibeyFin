import SwiftUI
import Shared

/// What a long press on a poster opens: a header with the poster, the title and its year, then Details, the Trailer (only when the title
/// has one: a grey line holds its place while the server is asked), Download and My List. A trailer the server holds plays in the app; one
/// on the web opens in the system's viewer. The sheet is as tall as its rows, so Dynamic Type never clips it, and it keeps the system's
/// own sheet background (Liquid Glass on iOS 26).
struct TileMenuSheetView: View {
    let state: TileMenuUiState
    let onDetails: () -> Void
    let onTrailer: (Trailer) -> Void
    let onMyList: () -> Void
    let onDownload: () -> Void
    let onDownloadAction: (DownloadAction) -> Void

    /// The height of the content, the sheet's one detent. A guess until the content has been measured.
    @State private var contentHeight: CGFloat = 320

    var body: some View {
        if let item = state.item {
            VStack(alignment: .leading, spacing: 0) {
                header(item)
                Divider().padding(.horizontal, Space.s5).padding(.bottom, Space.s1)
                row(Strings.shared.details, "info.circle", onDetails)
                if let trailer = state.trailer {
                    row(Strings.shared.trailer, "film") { onTrailer(trailer) }
                } else if state.checkingTrailer {
                    SkeletonLine(font: .body, width: 120)
                        .padding(.horizontal, Space.s5)
                        .frame(minHeight: 52, alignment: .leading)
                }
                if state.offersDownload { downloadRow }
                if let message = state.actionMessage {
                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(Color(.systemRed))
                        .padding(.horizontal, Space.s5)
                        .padding(.vertical, Space.s2)
                }
                row(Strings.shared.myListAction(onList: item.isFavorite), item.isFavorite ? "heart.fill" : "heart", onMyList)
            }
            .padding(.top, Space.s5)
            .padding(.bottom, Space.s2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                GeometryReader { proxy in
                    Color.clear.preference(key: SheetHeightKey.self, value: proxy.size.height + proxy.safeAreaInsets.bottom)
                }
            )
            .onPreferenceChange(SheetHeightKey.self) { if $0 > 0 { contentHeight = $0 } }
            .presentationDetents([.height(contentHeight)])
            .presentationDragIndicator(.visible)
        }
    }

    private func header(_ item: MediaItem) -> some View {
        HStack(spacing: Space.s3) {
            Artwork(url: item.posterUrl, title: item.title, aspectRatio: CGFloat(item.posterAspectRatio), blurHash: item.posterBlurHash)
                .frame(height: 60)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                let facts = item.menuFacts
                if !facts.isEmpty {
                    Text(facts).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Space.s5)
        .padding(.bottom, Space.s3)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    /// Download: with no download a tap starts one (a show asks first); with one the row says where it is and opens pause, resume and remove.
    @ViewBuilder
    private var downloadRow: some View {
        if let download = state.download {
            Menu {
                DownloadActionButtons(actions: DownloadBridge.shared.actions(download: download), onAction: onDownloadAction)
            } label: { downloadLabel.padding(.horizontal, Space.s2) }
        } else {
            Button(action: onDownload) { downloadLabel }.buttonStyle(MenuRowButtonStyle())
        }
    }

    private var downloadLabel: some View {
        MenuRowLabel(title: state.downloadLabel.titleCased()) {
            DownloadBadgeView(badge: state.downloadBadge, progress: state.downloadProgress?.floatValue, tint: .secondary)
        }
    }

    private func row(_ label: String, _ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            MenuRowLabel(title: label) { Image(systemName: icon) }
        }
        .buttonStyle(MenuRowButtonStyle())
    }
}

/// An icon and a title in a 52 pt row, the icon secondary. While the row is held both take the accent tint.
private struct MenuRowLabel<Icon: View>: View {
    let title: String
    @ViewBuilder let icon: () -> Icon
    @Environment(\.menuRowHeld) private var held

    var body: some View {
        HStack(spacing: Space.s4) {
            icon()
                .foregroundStyle(held ? Brand.onAccentSoft : Color.secondary)
                .frame(width: 24, height: 24)
            Text(title).foregroundStyle(held ? Brand.onAccentSoft : Color.primary)
            Spacer()
        }
        .padding(.horizontal, Space.s4)
        .frame(minHeight: 52)
        .contentShape(Rectangle())
    }
}

/// A held row lights up as a soft rounded pill in the accent tint, inset from the sheet's edges; it does not shrink or move.
private struct MenuRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .environment(\.menuRowHeld, configuration.isPressed)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(configuration.isPressed ? Brand.accentSoft : Color.clear))
            .padding(.horizontal, Space.s2)
    }
}

private struct MenuRowHeldKey: EnvironmentKey {
    static let defaultValue = false
}

private extension EnvironmentValues {
    var menuRowHeld: Bool {
        get { self[MenuRowHeldKey.self] }
        set { self[MenuRowHeldKey.self] = newValue }
    }
}

private struct SheetHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}
