import AVFoundation
import SwiftUI
import UIKit

/// The video itself: a view whose layer is an `AVPlayerLayer`. Everything drawn over it (controls,
/// scrims) is SwiftUI in `PlayerView`.
struct PlayerSurface: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        view.backgroundColor = .systemBackground
        // The controls carry the accessibility; the picture is not an element of its own.
        view.isAccessibilityElement = false
        return view
    }

    func updateUIView(_ view: PlayerLayerView, context: Context) {
        if view.playerLayer.player !== player {
            view.playerLayer.player = player
        }
    }
}

final class PlayerLayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer {
        // `layerClass` makes this always an AVPlayerLayer.
        layer as! AVPlayerLayer
    }
}
