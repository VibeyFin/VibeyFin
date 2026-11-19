import SwiftUI

/// Where a horizontal fold cuts the player: the video stays above it, the controls sit below.
struct TabletopSplit: Equatable {
    let videoBottom: CGFloat
    let controlsTop: CGFloat
}

enum PlayerFold {
    /// A fold that runs across the width is the tabletop pose. A vertical one is the book pose, which keeps the layout as it is.
    static func tabletopSplit(crease: CGRect?, in size: CGSize) -> TabletopSplit? {
        guard let crease, size.width > 0, crease.width >= size.width * 0.9, crease.height < crease.width else { return nil }
        return TabletopSplit(videoBottom: crease.minY, controlsTop: crease.maxY)
    }
}

extension View {
    func foldReader(split: Binding<TabletopSplit?>) -> some View {
        onGeometryChange(for: TabletopSplit?.self) { proxy in
            guard #available(iOS 27.1, *) else { return nil }
            return PlayerFold.tabletopSplit(crease: proxy.reservedRegions(kind: .division).first?.frame, in: proxy.size)
        } action: { split.wrappedValue = $0 }
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
