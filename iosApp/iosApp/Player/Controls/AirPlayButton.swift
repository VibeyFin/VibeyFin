import AVKit
import SwiftUI

/// The system AirPlay route picker, white like the rest of the player's controls. It is one 44 pt
/// cell of the top-right cluster.
struct AirPlayButton: UIViewRepresentable {
    // Scales with Dynamic Type like the sibling cells of the cluster, so they keep one baseline.
    @ScaledMetric(relativeTo: .title2) private var diameter: CGFloat = 44

    /// A touch on the button, before the picker shows.
    var onTouch: () -> Void = {}
    /// The picker opened (`true`) or closed (`false`), like the speed and track menus.
    var onOpenChange: (Bool) -> Void = { _ in }
    /// Takes all the space it is offered, so a tile can be the touch target.
    var fillsProposal: Bool = false

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.tintColor = UIColor(Brand.onMedia)
        picker.activeTintColor = UIColor(Brand.onMedia)
        picker.prioritizesVideoDevices = true
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {
        context.coordinator.onTouch = onTouch
        context.coordinator.onOpenChange = onOpenChange
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: AVRoutePickerView, context: Context) -> CGSize? {
        let standard: CGSize = CGSize(width: diameter, height: diameter)
        return fillsProposal ? proposal.replacingUnspecifiedDimensions(by: standard) : standard
    }

    final class Coordinator: NSObject, AVRoutePickerViewDelegate {
        var onTouch: () -> Void = {}
        var onOpenChange: (Bool) -> Void = { _ in }

        func routePickerViewWillBeginPresentingRoutes(_ routePickerView: AVRoutePickerView) {
            onTouch()
            onOpenChange(true)
        }

        func routePickerViewDidEndPresentingRoutes(_ routePickerView: AVRoutePickerView) {
            onOpenChange(false)
        }
    }
}
