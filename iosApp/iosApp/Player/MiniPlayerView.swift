import Shared
import SwiftUI

/// Lets SwiftUI watch what the player is showing, for the mini player.
final class NowPlayingObservable: ObservableObject {
    /// Nil while nothing is playing.
    @Published private(set) var state: NowPlayingState?

    private let model = NowPlayingModel()
    private var started = false

    /// Starts watching; called when the view appears, not from `init`.
    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    /// Asks for the full player to come back.
    func restore() {
        model.requestRestore()
    }

    deinit {
        model.close()
    }
}

/// The mini player for iOS 26: the title, what is playing and a play or pause button, in the bottom
/// accessory of the tab bar, which draws the glass around it. Tapping it asks for the full player.
///
/// Not mounted anywhere yet: the app has no tab bar. It is finished when the tab bar mounts it with
/// `.tabViewBottomAccessory` and it is checked on the simulator; whoever builds the tab bar owns that.
@available(iOS 26.0, *)
struct MiniPlayerView: View {
    let state: NowPlayingState
    let onTogglePlay: () -> Void
    let onOpen: () -> Void

    var body: some View {
        HStack(spacing: Space.s3) {
            Button(action: onOpen) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(state.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    if let subtitle = state.subtitle {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button(action: onTogglePlay) {
                Image(systemName: state.playing ? "pause.fill" : "play.fill")
                    .font(.title3)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(state.playing ? Strings.shared.pause : Strings.shared.play)
        }
        .padding(.horizontal, Space.s4)
    }
}

@available(iOS 26.0, *)
#Preview("Playing") {
    MiniPlayerView(
        state: NowPlayingState(itemId: "e1", title: "Pilot", subtitle: "Severance · S1 E1", playing: true, positionMs: 61_000),
        onTogglePlay: {},
        onOpen: {}
    )
    .frame(height: 56)
    .background(.bar)
    .preferredColorScheme(.dark)
}

@available(iOS 26.0, *)
#Preview("Paused, long title") {
    MiniPlayerView(
        state: NowPlayingState(
            itemId: "m1",
            title: "The Assassination of Jesse James by the Coward Robert Ford",
            subtitle: nil,
            playing: false,
            positionMs: 0
        ),
        onTogglePlay: {},
        onOpen: {}
    )
    .frame(height: 56)
    .background(.bar)
    .preferredColorScheme(.dark)
}
