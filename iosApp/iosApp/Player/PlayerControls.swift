import Shared
import SwiftUI
import UIKit

private struct PlayerInClusterKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// The control sits inside a `PlayerControlCluster`, which draws the one shared backdrop.
    fileprivate var playerInCluster: Bool {
        get { self[PlayerInClusterKey.self] }
        set { self[PlayerInClusterKey.self] = newValue }
    }
}

/// The symbol of a round player control, sized to touch and growing with Dynamic Type. Before iOS 26
/// it sits on a scrim disc; on iOS 26 the glass button style draws the disc.
private struct PlayerSymbol: View {
    let systemName: String
    var font: Font = .title2

    // 44 pt to touch at the default text size.
    @ScaledMetric(relativeTo: .title2) private var diameter: CGFloat = 44
    @Environment(\.playerInCluster) private var inCluster

    var body: some View {
        let symbol = Image(systemName: systemName)
            .font(font.weight(.semibold))
            .foregroundStyle(.primary)
            .frame(width: diameter, height: diameter)
            .contentShape(Circle())
        if inCluster {
            symbol
        } else if #available(iOS 26.0, *) {
            symbol
        } else {
            symbol.background(Circle().fill(Brand.scrim))
        }
    }
}

/// How a round control is drawn: real Liquid Glass on iOS 26 (`.glass`, never tinted), plain on the
/// scrim before that. Glass floats over the video, which is what it is for: never over content.
private struct PlayerControlStyle: ViewModifier {
    @Environment(\.playerInCluster) private var inCluster

    func body(content: Content) -> some View {
        if inCluster {
            content.buttonStyle(PressableButtonStyle())
        } else if #available(iOS 26.0, *) {
            content
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
        } else {
            content.buttonStyle(PressableButtonStyle())
        }
    }
}

/// A round icon button for the player's controls.
struct PlayerIconButton: View {
    let systemName: String
    /// What VoiceOver says; the button shows only the symbol.
    let label: String
    var font: Font = .title2
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            PlayerSymbol(systemName: systemName, font: font)
        }
        .modifier(PlayerControlStyle())
        .accessibilityLabel(label)
    }
}

/// The audio and subtitle picker: one round button that opens a menu with an Audio and a Subtitles
/// picker. A list is left out when the file has nothing to choose from. The labels are the shared
/// model's own wording; only the two section titles are set here.
struct PlayerTrackMenu: View {
    let audio: [TrackOption]
    let subtitles: [TrackOption]
    let onSelectAudio: (Int32) -> Void
    let onSelectSubtitle: (Int32) -> Void
    /// A touch landed on the button, so the menu is about to open. A menu can't say when it opens or
    /// closes, so this is the only moment the player can hold its controls up.
    var onTouch: () -> Void = {}

    /// A subtitle is showing: the "Off" row has a negative index.
    private var subtitleIsOn: Bool {
        subtitles.contains { $0.selected && $0.index >= 0 }
    }

    var body: some View {
        Menu {
            if !audio.isEmpty {
                picker(Strings.shared.audio, options: audio, onSelect: onSelectAudio)
            }
            if !subtitles.isEmpty {
                picker(Strings.shared.subtitles, options: subtitles, onSelect: onSelectSubtitle)
            }
        } label: {
            PlayerSymbol(systemName: subtitleIsOn ? "captions.bubble.fill" : "captions.bubble")
        }
        .modifier(PlayerControlStyle())
        .simultaneousGesture(TapGesture().onEnded(onTouch))
        .accessibilityLabel(Strings.shared.audioAndSubtitles)
    }

    /// The picker shows what the shared model marks as selected, and a pick goes straight back to it:
    /// nothing is kept here, so the state is the only truth.
    private func picker(_ title: String, options: [TrackOption], onSelect: @escaping (Int32) -> Void) -> some View {
        Picker(
            title.titleCased(),
            selection: Binding(
                get: { options.first(where: { $0.selected })?.index ?? options.first?.index ?? 0 },
                set: onSelect
            )
        ) {
            ForEach(options, id: \.index) { option in
                Text(option.label).tag(option.index)
            }
        }
    }
}

/// The playback speed: a capsule button with the current speed that opens a menu of the speeds. The
/// labels are the shared model's own wording; only the section title is set here.
struct PlayerSpeedMenu: View {
    let speeds: [Float]
    let selected: Float
    let onSelect: (Float) -> Void
    /// See `PlayerTrackMenu.onTouch`.
    var onTouch: () -> Void = {}

    var body: some View {
        Menu {
            Picker(
                Strings.shared.speed.titleCased(),
                selection: Binding(get: { selected }, set: onSelect)
            ) {
                ForEach(speeds, id: \.self) { speed in
                    Text(Strings.shared.speedValue(speed: speed)).tag(speed)
                }
            }
        } label: {
            Text(Strings.shared.speedValue(speed: selected))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundStyle(.primary)
                .padding(.horizontal, Space.s3)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Capsule())
                .modifier(CapsuleBackdrop())
        }
        .modifier(PlayerCapsuleStyle())
        .simultaneousGesture(TapGesture().onEnded(onTouch))
        .accessibilityLabel(Strings.shared.labelled(label: Strings.shared.speed, value: Strings.shared.speedValue(speed: selected)))
    }
}

/// The scrim behind a capsule control before iOS 26, where the glass style draws its own.
private struct CapsuleBackdrop: ViewModifier {
    @Environment(\.playerInCluster) private var inCluster

    func body(content: Content) -> some View {
        if inCluster {
            content
        } else if #available(iOS 26.0, *) {
            content
        } else {
            content.background(Capsule().fill(Brand.scrim))
        }
    }
}

private struct PlayerCapsuleStyle: ViewModifier {
    @Environment(\.playerInCluster) private var inCluster

    func body(content: Content) -> some View {
        if inCluster {
            content.buttonStyle(PressableButtonStyle())
        } else if #available(iOS 26.0, *) {
            content
                .buttonStyle(.glass)
                .buttonBorderShape(.capsule)
        } else {
            content.buttonStyle(PressableButtonStyle())
        }
    }
}

/// The picture's one badge ("4K", "HDR10"), on the scrim so it reads over any video.
struct FormatBadge: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.primary)
            .padding(.horizontal, Space.s2)
            .padding(.vertical, 2)
            .background(Capsule().fill(Brand.scrim))
            .accessibilityLabel(text)
    }
}

/// One capsule holding the player's trailing controls as 44 pt cells: Liquid Glass on iOS 26, a
/// `Brand.scrim` capsule before that. The controls inside draw no backdrop of their own.
struct PlayerControlCluster<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        let cells = HStack(spacing: 0) { content() }
            .environment(\.playerInCluster, true)
            .clipShape(Capsule())
        if #available(iOS 26.0, *) {
            cells.glassEffect(.regular, in: Capsule())
        } else {
            cells.background(Capsule().fill(Brand.scrim))
        }
    }
}

/// Groups the player's glass controls so they blend and share one render pass on iOS 26. Before
/// that there is nothing to group. The app's aqua accent reaches glass buttons and menu labels
/// through the tint, so it is set to white here once: aqua stays on the scrubber's fill only.
struct PlayerGlassGroup<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer { content() }
                .tint(.white)
        } else {
            content().tint(.white)
        }
    }
}

/// The scrubber: a bar with no thumb that thickens while it is dragged. Aqua is the progress. A tap
/// or a drag anywhere on it moves there; the seek happens when the finger lifts.
struct PlayerScrubber: View {
    /// Seconds.
    let position: TimeInterval
    let duration: TimeInterval
    /// Seconds; how far the stream is loaded. Drawn between the played part and the track.
    var bufferedPosition: TimeInterval = 0
    /// While dragging, with the second under the finger.
    let onScrub: (TimeInterval) -> Void
    /// When the finger lifts, with the second to go to.
    let onCommit: (TimeInterval) -> Void
    /// A step of a VoiceOver swipe up or down on the scrubber.
    var accessibilityStep: TimeInterval = 10
    /// The thumbnails to preview while dragging; nil shows the timestamp alone.
    var trickplay: TrickplayTiles?

    @State private var isDragging = false
    @State private var thumbnail: UIImage?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let fraction = duration > 0 ? min(max(position / duration, 0), 1) : 0
            let bufferedFraction = duration > 0 ? min(max(bufferedPosition / duration, fraction), 1) : 0
            ZStack(alignment: .leading) {
                Capsule().fill(Brand.progressTrack)
                Capsule().fill(Color.white.opacity(0.35)).frame(width: width * bufferedFraction)
                Capsule().fill(Brand.accent).frame(width: width * fraction)
            }
            .frame(height: isDragging ? 10 : 4)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        isDragging = true
                        onScrub(seconds(at: value.location.x, in: width))
                    }
                    .onEnded { value in
                        isDragging = false
                        onCommit(seconds(at: value.location.x, in: width))
                    }
            )
        }
        .frame(height: 44)
        .overlay(alignment: .topLeading) { preview }
        .task(id: PreviewKey(dragging: isDragging, second: position.isFinite ? Int(position) : 0)) {
            guard isDragging, let trickplay else {
                thumbnail = nil
                return
            }
            let image = await trickplay.thumbnail(atMs: PlayerTime.milliseconds(position))
            // A newer drag position has cancelled this task; its answer must not replace the newer one.
            guard !Task.isCancelled else { return }
            thumbnail = image
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.15), value: isDragging)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.playbackPosition)
        .accessibilityValue(Strings.shared.positionDescription(
            positionMs: PlayerTime.milliseconds(position),
            durationMs: PlayerTime.milliseconds(duration)
        ))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: onCommit(min(position + accessibilityStep, duration))
            case .decrement: onCommit(max(position - accessibilityStep, 0))
            @unknown default: break
            }
        }
    }

    private struct PreviewKey: Equatable {
        let dragging: Bool
        let second: Int
    }

    /// Above the bar, centred on the finger but kept inside the bar's width so it never leaves the screen.
    @ViewBuilder private var preview: some View {
        if isDragging, let thumbnail, duration > 0 {
            GeometryReader { geometry in
                let width = geometry.size.width
                let fraction = min(max(position / duration, 0), 1)
                let previewWidth = ScrubPreview.width(forBarWidth: width)
                let x = min(max(width * fraction - previewWidth / 2, 0), max(width - previewWidth, 0))
                ScrubPreview(
                    thumbnail: thumbnail,
                    time: Strings.shared.clock(milliseconds: PlayerTime.milliseconds(position)),
                    pictureWidth: ScrubPreview.pictureWidth(forBarWidth: width)
                )
                    .alignmentGuide(.top) { $0[.bottom] }
                    .offset(x: x)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
    }

    private func seconds(at x: CGFloat, in width: CGFloat) -> TimeInterval {
        guard width > 0 else { return 0 }
        return duration * Double(min(max(x / width, 0), 1))
    }
}
