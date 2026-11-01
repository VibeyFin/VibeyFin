import SwiftUI
import Shared

/// "More like this" with Seerr's titles mixed in: the library's posters open their pages, a Seerr one opens its request page and
/// offers the Seerr menu on a long press.
struct SimilarRowView: View {
    let title: String
    let tiles: [SimilarTile]
    let tileWidth: CGFloat
    let onOpen: (MediaItem) -> Void
    let onOpenSeerr: (SeerrTitle) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(title).sectionTitle().lineLimit(1).accessibilityAddTraits(.isHeader).screenMargin()
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: Space.s3) {
                    ForEach(tiles, id: \.key) { tile in
                        if let library = tile as? SimilarTileLibrary {
                            Button { onOpen(library.item) } label: { ArtworkTile(item: library.item, captionLines: 2).frame(width: tileWidth) }
                                .buttonStyle(PressableButtonStyle())
                        } else if let seerr = tile as? SimilarTileSeerr {
                            SeerrTileButton(title: seerr.title, onOpen: onOpenSeerr).frame(width: tileWidth)
                        }
                    }
                }
                .screenMargin()
                .liftRoomInside()
            }
            .liftRoom()
        }
    }
}
