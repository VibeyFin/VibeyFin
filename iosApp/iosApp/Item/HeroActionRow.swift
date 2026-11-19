import SwiftUI
import Shared

/// One of the actions under the primary button: an icon over a label, in the full ink so it reads against the picture.
struct HeroActionButton: View {
    let label: String
    let description: String
    let systemImage: String
    /// A toggle springs when it flips.
    var pop: Bool?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HeroActionFace(label: label, systemImage: systemImage, pop: pop)
        }
        .buttonStyle(PressableButtonStyle())
        .accessibilityLabel(description)
    }
}

/// An action's icon over its label, for a button or a menu in the action row.
struct HeroActionFace: View {
    let label: String
    let systemImage: String
    var pop: Bool?

    var body: some View {
        VStack(spacing: Space.s1) {
            Image(systemName: systemImage)
                .font(.title2)
                .id(systemImage)
                .transition(.scale.combined(with: .opacity))
                .animation(.spring(response: 0.3, dampingFraction: 0.45), value: pop)
            // Medium weight in the full ink: the labels read against the picture.
            Text(label.titleCased()).font(.caption.weight(.medium))
        }
        .foregroundStyle(.primary)
        .frame(minWidth: 64, maxWidth: .infinity, minHeight: 72)
        .contentShape(Rectangle())
    }
}

/// The action row's places: four of one width, the widest action's, the first at the row's leading edge and the last ending at
/// its trailing edge, evenly spread. Fewer actions keep the same places from the leading edge; the rest stay empty. Too narrow for
/// that, the places share the width.
struct ActionRowLayout: Layout {
    private let places = 4

    private func placeWidth(_ width: CGFloat, _ subviews: Subviews) -> CGFloat {
        let widest = subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        return min(widest, width / CGFloat(places))
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        // Measured without a width (an ideal-size pass), the row is four of its widest button, never zero wide.
        let widest = subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        let width = proposal.width.flatMap { $0.isFinite ? $0 : nil } ?? widest * CGFloat(places)
        let place = placeWidth(width, subviews)
        let height = subviews.map { $0.sizeThatFits(ProposedViewSize(width: place, height: nil)).height }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let place = placeWidth(bounds.width, subviews)
        let gap = (bounds.width - place * CGFloat(places)) / CGFloat(places - 1)
        for (index, subview) in subviews.enumerated() {
            subview.place(
                at: CGPoint(x: bounds.minX + CGFloat(index) * (place + gap), y: bounds.minY),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: place, height: bounds.height)
            )
        }
    }
}
