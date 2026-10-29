import Shared
import SwiftUI

/// The scrubber with the elapsed and remaining time under it. It is the only part of the controls that
/// observes the clock, so a tick redraws these and nothing else.
struct PlayerProgressBar: View {
    @ObservedObject var clock: PlayerClock
    let duration: TimeInterval
    /// The second under the finger while the scrubber is dragged; it wins over the clock.
    let scrubPosition: TimeInterval?
    let onScrub: (TimeInterval) -> Void
    let onCommit: (TimeInterval) -> Void
    let trickplay: TrickplayTiles?

    private var shownPosition: TimeInterval { scrubPosition ?? clock.position }

    var body: some View {
        VStack(spacing: 0) {
            PlayerScrubber(
                position: shownPosition,
                duration: duration,
                bufferedPosition: clock.bufferedPosition,
                onScrub: onScrub,
                onCommit: onCommit,
                trickplay: trickplay
            )
            HStack {
                Text(Strings.shared.clock(milliseconds: PlayerTime.milliseconds(shownPosition)))
                Spacer()
                if duration > 0 {
                    Text(Strings.shared.timeLeft(
                        positionMs: PlayerTime.milliseconds(shownPosition),
                        durationMs: PlayerTime.milliseconds(duration)
                    ))
                }
            }
            .font(.footnote)
            .monospacedDigit()
            .foregroundStyle(.secondary)
        }
    }
}
