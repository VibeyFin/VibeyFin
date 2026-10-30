import AVFoundation
import Shared
import SwiftUI

/// What the player shows while the video plays on another screen over AirPlay (`AVPlayer.isExternalPlaybackActive`).
enum ExternalPlayback {
    /// The receiver's name from the audio route's AirPlay ports: the first that has one. Nil when the system gave none,
    /// which the shared `Strings.playingOn` words as "Playing on AirPlay".
    static func deviceName(fromPortNames names: [String]) -> String? {
        names.lazy
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty }
    }

    /// The AirPlay outputs of the current audio route, by name.
    static func currentPortNames() -> [String] {
        AVAudioSession.sharedInstance().currentRoute.outputs
            .filter { $0.portType == .airPlay }
            .map(\.portName)
    }
}

/// Behind the controls while AirPlaying: the item's artwork, blurred and dimmed, or a flat dark ground without
/// one. The picture is on the other screen, so this is only a quiet backdrop for the white controls.
struct ExternalPlaybackBackdrop: View {
    let artworkUrl: String?

    var body: some View {
        // The artwork is an overlay on the flat ground, so its fill size never changes the layout: the ground takes the
        // space it is offered and the image is cropped to it.
        Color(.secondarySystemBackground)
            .overlay {
                if let url = artworkUrl.flatMap(URL.init(string:)) {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill().blur(radius: 24).opacity(0.6)
                        }
                    }
                    // The blur's soft edge falls outside the screen.
                    .scaleEffect(1.1)
                }
            }
            .clipped()
            .ignoresSafeArea()
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// "Playing on <device>": a status, not a button, centred above the transport. White on Liquid Glass, a
/// `scrim-strong` capsule before iOS 26, never aqua.
struct ExternalPlaybackStatus: View {
    let text: String

    // The play disc's size, so the pill clears it at any text size.
    @ScaledMetric(relativeTo: .largeTitle) private var playDiameter: CGFloat = 76

    var body: some View {
        ZStack {
            Color.clear
            pill.alignmentGuide(VerticalAlignment.center) { $0[.bottom] + playDiameter / 2 + Space.s4 }
        }
        .allowsHitTesting(false)
    }

    @ViewBuilder
    private var pill: some View {
        let label = HStack(spacing: Space.s2) {
            Image(systemName: "airplay.video")
            Text(text).lineLimit(1).truncationMode(.tail)
        }
        .font(.headline)
        .foregroundStyle(.white)
        .padding(.horizontal, Space.s4)
        .padding(.vertical, Space.s2)
        .frame(minHeight: 44)
        .padding(.horizontal, Space.s5)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
        if #available(iOS 26.0, *) {
            label.glassEffect(.regular, in: Capsule())
        } else {
            label.background(Capsule().fill(Brand.scrimStrong))
        }
    }
}
