import SwiftUI
import Shared

/// 0 before, 1 grown in the middle, 2 in the header's button.
struct ProfileFlight {
    let profile: Profile
    let from: CGRect
    let size: CGFloat
    var step = 0
}

struct ProfileFlightView: View {
    let flight: ProfileFlight
    let button: CGRect

    var body: some View {
        GeometryReader { proxy in
            let origin = proxy.frame(in: .global).origin
            let middle = CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)
            let start = CGPoint(x: flight.from.midX - origin.x, y: flight.from.midY - origin.y)
            let end = CGPoint(x: button.midX - origin.x, y: button.midY - origin.y)
            Avatar(profile: flight.profile, size: flight.size)
                .clipShape(RoundedRectangle(cornerRadius: flight.step == 2 ? flight.size / 2 : Radius.card, style: .continuous))
                .scaleEffect(flight.step == 2 ? button.width / flight.size : flight.step == 1 ? 1.8 : 1)
                .position(flight.step == 2 ? end : flight.step == 1 ? middle : start)
        }
        .allowsHitTesting(false)
    }
}
