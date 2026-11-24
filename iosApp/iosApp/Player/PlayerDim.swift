import Shared
import SwiftUI

/// The first touch anywhere on the dimmed player only wakes it.
struct PlayerDimOverlay: View {
    let dimmed: Bool
    let onWake: () -> Void

    var body: some View {
        ZStack {
            if dimmed {
                Brand.scrimStrong
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { _ in onWake() })
                    .accessibilityElement()
                    .accessibilityLabel(Strings.shared.playerScreenDimmed)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { onWake() }
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: dimmed ? 1 : 0.15), value: dimmed)
    }
}
