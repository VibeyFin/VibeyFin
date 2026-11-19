import SwiftUI

enum PlayerInsets {
    /// The same inset on both sides, the larger of the two, so controls sit centred on the picture whichever side the system strip is on.
    static func symmetric(leading: CGFloat, trailing: CGFloat) -> CGFloat {
        max(leading, trailing)
    }
}
