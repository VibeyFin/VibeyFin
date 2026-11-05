import SwiftUI
import UIKit

/// The video itself: the view the engine draws in. Everything drawn over it (controls, scrims) is
/// SwiftUI in `PlayerView`.
struct PlayerSurface: UIViewRepresentable {
    let controller: PlayerController
    let pip: PlayerPictureInPicture

    func makeUIView(context: Context) -> UIView {
        controller.engine.makeVideoView(pip: pip)
    }

    func updateUIView(_ view: UIView, context: Context) {}
}
