import AVKit
import SwiftUI

/// The system AirPlay route picker, white like the rest of the player's controls. It is one 44 pt
/// cell of the top-right cluster.
struct AirPlayButton: UIViewRepresentable {
    // Scales with Dynamic Type like the sibling cells of the cluster, so they keep one baseline.
    @ScaledMetric(relativeTo: .title2) private var diameter: CGFloat = 44

    func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.tintColor = .white
        picker.activeTintColor = .white
        picker.prioritizesVideoDevices = true
        return picker
    }

    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: AVRoutePickerView, context: Context) -> CGSize? {
        CGSize(width: diameter, height: diameter)
    }
}
