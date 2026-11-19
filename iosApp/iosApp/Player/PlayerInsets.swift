import SwiftUI

enum PlayerInsets {
    /// The padding each side needs, on top of its own safe-area inset, to bring both to the larger of the two, so controls sit centred on the picture whichever side the system strip is on.
    static func balance(leading: CGFloat, trailing: CGFloat) -> (leading: CGFloat, trailing: CGFloat) {
        let larger: CGFloat = max(leading, trailing)
        return (larger - leading, larger - trailing)
    }
}

extension PlayerInsets {
    enum CameraEdge: Equatable {
        case top
        case bottom
    }

    /// The camera sits opposite the strip (bottom while the strip trails, top while it leads); the row on that edge clears it by the strip's inset.
    static func cameraClearance(at edge: CameraEdge, leading: CGFloat, trailing: CGFloat) -> CGFloat {
        let cameraEdge: CameraEdge? = trailing > leading ? .bottom : leading > trailing ? .top : nil
        return cameraEdge == edge ? max(leading, trailing) : 0
    }

    /// Held landscape the strip is on one short side only, so the rows may use the whole width.
    static func spansWidth(size: CGSize) -> Bool {
        size.width > size.height
    }

    /// The inner display has no camera, but the strip's clock and Wi-Fi sit over its top corner, so only the top row keeps out of the strip.
    static func rowInsets(at edge: CameraEdge, leading: CGFloat, trailing: CGFloat, regularWidth: Bool) -> (leading: CGFloat, trailing: CGFloat) {
        guard regularWidth else {
            let clearance: CGFloat = cameraClearance(at: edge, leading: leading, trailing: trailing)
            return (clearance, clearance)
        }
        return edge == .top ? (leading, trailing) : (0, 0)
    }
}

struct ControlsFrame: Equatable {
    let safeArea: EdgeInsets
    let size: CGSize
}

extension View {
    func rowInsets(_ insets: (leading: CGFloat, trailing: CGFloat)) -> some View {
        padding(.leading, insets.leading).padding(.trailing, insets.trailing)
    }

    @ViewBuilder
    func controlsSpanWidth(spans: Bool) -> some View {
        if spans { ignoresSafeArea(.container, edges: .horizontal) } else { self }
    }
}
