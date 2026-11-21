import SwiftUI

/// Where a horizontal fold cuts the player: the video stays above it, the controls sit below.
struct TabletopSplit: Equatable {
    let videoBottom: CGFloat
    let controlsTop: CGFloat
}

/// Where a vertical fold cuts the player: the picture runs across, the controls sit right of it. `originX` is the player's left edge on screen.
struct BookSplit: Equatable {
    let creaseMinX: CGFloat
    let creaseMaxX: CGFloat
    let originX: CGFloat

    var controlsLeading: CGFloat { originX + creaseMaxX }
}

struct FoldPose: Equatable {
    var tabletop: TabletopSplit?
    var book: BookSplit?
}

enum PlayerFold {
    /// A fold that runs across the width is the tabletop pose.
    static func tabletopSplit(crease: CGRect?, in size: CGSize) -> TabletopSplit? {
        guard let crease, size.width > 0, crease.width >= size.width * 0.9, crease.height < crease.width else { return nil }
        return TabletopSplit(videoBottom: crease.minY, controlsTop: crease.maxY)
    }

    /// A fold that runs down the full height is the book pose.
    static func bookSplit(crease: CGRect?, in size: CGSize, originX: CGFloat) -> BookSplit? {
        guard let crease, size.height > 0, crease.height >= size.height * 0.9, crease.width < crease.height else { return nil }
        return BookSplit(creaseMinX: crease.minX, creaseMaxX: crease.maxX, originX: originX)
    }
}

extension View {
    func foldReader(tabletop: Binding<TabletopSplit?>, book: Binding<BookSplit?>) -> some View {
        onGeometryChange(for: FoldPose.self) { proxy in
            guard #available(iOS 27.1, *) else { return FoldPose() }
            let crease: CGRect? = proxy.reservedRegions(kind: .division).first?.frame
            return FoldPose(
                tabletop: PlayerFold.tabletopSplit(crease: crease, in: proxy.size),
                book: PlayerFold.bookSplit(crease: crease, in: proxy.size, originX: proxy.frame(in: .global).minX)
            )
        } action: {
            tabletop.wrappedValue = $0.tabletop
            book.wrappedValue = $0.book
        }
    }

    func bookReader(_ book: Binding<BookSplit?>) -> some View {
        onGeometryChange(for: BookSplit?.self) { proxy in
            guard #available(iOS 27.1, *) else { return nil }
            return PlayerFold.bookSplit(crease: proxy.reservedRegions(kind: .division).first?.frame, in: proxy.size, originX: proxy.frame(in: .global).minX)
        } action: { book.wrappedValue = $0 }
    }

    func bookControls(_ book: BookSplit?) -> some View {
        padding(.leading, book?.controlsLeading ?? 0)
    }

    @ViewBuilder
    func bookSubtitles(_ book: BookSplit?) -> some View {
        if let book {
            frame(width: book.creaseMinX).frame(maxWidth: .infinity, alignment: .leading)
        } else {
            self
        }
    }

    @ViewBuilder
    func tabletopVideo(split: TabletopSplit?) -> some View {
        if let split {
            frame(height: split.videoBottom)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else {
            self
        }
    }

    func tabletopControls(split: TabletopSplit?) -> some View {
        padding(.top, split?.controlsTop ?? 0)
    }
}
