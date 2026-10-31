import SwiftUI
import Shared

/// Set by TabShell, which owns the navigation and the player.
struct TileMenuHandlers {
    var details: (MediaItem) -> Void = { _ in }
    var trailer: (Trailer) -> Void = { _ in }
}

extension View {
    /// The long-press menu. It goes on the Button that holds the tile, never inside its label, and no gesture of ours may sit beside it:
    /// either one keeps the system's long press from firing.
    func tileContextMenu(_ item: MediaItem, onShown: @escaping () -> Void = {}) -> some View {
        modifier(TileContextMenu(item: item, onShown: onShown))
    }

    /// A poster's Button: press feedback and the menu, with its data asked for once the finger has rested.
    func tileButton(_ item: MediaItem?) -> some View {
        modifier(TileButton(item: item))
    }

    /// A lifted tile grows and a horizontal scroll view clips at its edge: `liftRoom()` on the ScrollView and `liftRoomInside()` on its
    /// content leave room for that without moving the layout.
    func liftRoom() -> some View {
        modifier(LiftRoom())
    }

    func liftRoomInside() -> some View {
        padding(.vertical, Lift.room)
    }

    /// The hero is not a Button, so its data is asked for as it appears. `onShown` runs when the menu comes up.
    func heroMenu(_ item: MediaItem, onShown: @escaping () -> Void) -> some View {
        modifier(HeroMenu(item: item, onShown: onShown))
    }
}

private struct TileButton: ViewModifier {
    /// Nil for what has no menu, such as a person in search results.
    let item: MediaItem?
    @Environment(\.tileMenu) private var menu

    @ViewBuilder
    func body(content: Content) -> some View {
        if let item {
            content
                .buttonStyle(PressableButtonStyle(onHold: { menu?.open(item) }))
                .tileContextMenu(item)
        } else {
            content.buttonStyle(PressableButtonStyle())
        }
    }
}

/// How far a lifted tile may grow past its frame.
enum Lift {
    static let room = Space.s3
}

private struct LiftRoom: ViewModifier {

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.padding(.vertical, -Lift.room).scrollClipDisabled()
        } else {
            content.padding(.vertical, -Lift.room)
        }
    }
}

private struct HeroMenu: ViewModifier {
    let item: MediaItem
    let onShown: () -> Void
    @Environment(\.tileMenu) private var menu

    func body(content: Content) -> some View {
        content
            .task(id: item.id) { menu?.open(item) }
            .tileContextMenu(item, onShown: onShown)
    }
}

private struct TileContextMenu: ViewModifier {
    let item: MediaItem
    let onShown: () -> Void
    @Environment(\.tileMenu) private var menu

    @ViewBuilder
    func body(content: Content) -> some View {
        if let menu {
            content.modifier(LiveTileContextMenu(item: item, menu: menu, onShown: onShown))
        } else {
            content
        }
    }
}

private struct LiveTileContextMenu: ViewModifier {
    let item: MediaItem
    /// Not observed: a redraw of every poster during a press would cancel it.
    let menu: TileMenuObservable
    let onShown: () -> Void
    /// SwiftUI builds the rows when the poster is drawn, so they are rebuilt, for this poster only, when its data arrives.
    @State private var shown: TileMenuUiState?

    func body(content: Content) -> some View {
        content
            .onReceive(menu.stateChanges.filter { $0.item?.id == item.id }) { shown = $0 }
            .contextMenu { rows }
    }

    /// Until the data arrives the rows come from the poster itself, with a placeholder for the Trailer.
    @ViewBuilder
    private var rows: some View {
        let isFavorite = shown?.item?.isFavorite ?? item.isFavorite
        let offersDownload = shown?.offersDownload ?? (menu.canDownload && (item.canPlay || item.kind == ItemKind.series))
        let checkingTrailer = shown?.checkingTrailer ?? item.canHaveTrailer
        Button { menu.handlers.details(item) } label: {
            Label(Strings.shared.details.titleCased(), systemImage: "info.circle")
        }
        .onAppear(perform: onShown)
        if let trailer = shown?.trailer {
            Button { menu.handlers.trailer(trailer) } label: {
                Label(Strings.shared.trailer.titleCased(), systemImage: "film")
            }
        } else if checkingTrailer {
            // Not `.disabled`: the system draws that too faint to read.
            Button {} label: {
                Label(Strings.shared.checkingTrailer, systemImage: "film")
            }
        }
        Button { menu.toggleFavorite(item) } label: {
            Label(Strings.shared.myListAction(onList: isFavorite).titleCased(), systemImage: isFavorite ? "heart.fill" : "heart")
        }
        if offersDownload { downloadRows(shown) }
    }

    /// Download: with no download a tap starts one (a show asks first); with one, pause, resume and remove.
    @ViewBuilder
    private func downloadRows(_ state: TileMenuUiState?) -> some View {
        if let download = state?.download {
            DownloadActionButtons(actions: DownloadBridge.shared.actions(download: download), onAction: { menu.act($0) })
        } else {
            Button { menu.download(item) } label: {
                Label(
                    state?.downloadLabel.titleCased()
                        ?? (item.kind == ItemKind.series ? Strings.shared.downloadAllEpisodes : Strings.shared.download).titleCased(),
                    systemImage: "arrow.down.to.line"
                )
            }
        }
    }
}
