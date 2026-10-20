import Shared
import SwiftUI

/// The playback stats panel: how the server delivers the video and what the player measures, one
/// row per field. A value nobody knows reads as unavailable. Takes no touches.
struct StatsOverlay: View {
    let diagnostics: PlaybackDiagnostics

    private var rows: [(label: String, value: String)] {
        let strings = Strings.shared
        let none = strings.statsUnavailable
        var rows: [(label: String, value: String)] = [
            (strings.statsLabelMethod, diagnostics.method.map { strings.statsMethod(method: $0) } ?? none)
        ]
        if !diagnostics.transcodeReasons.isEmpty {
            rows.append((strings.statsLabelReasons, diagnostics.transcodeReasons.joined(separator: "\n")))
        }
        rows.append((strings.statsLabelVideo, diagnostics.videoCodec ?? none))
        rows.append((strings.statsLabelAudio, diagnostics.audioCodec ?? none))
        if let width = diagnostics.width, let height = diagnostics.height {
            rows.append((strings.statsLabelResolution, strings.statsResolution(width: width.int32Value, height: height.int32Value)))
        } else {
            rows.append((strings.statsLabelResolution, none))
        }
        rows.append((strings.statsLabelFrameRate, diagnostics.frameRate.map { strings.statsFrameRate(framesPerSecond: $0.floatValue) } ?? none))
        rows.append((strings.statsLabelBitrate, diagnostics.bitrateKbps.map { strings.statsBitrate(kbps: $0.int32Value) } ?? none))
        rows.append((strings.statsLabelDroppedFrames, diagnostics.droppedFrames.map { "\($0.int32Value)" } ?? none))
        rows.append((strings.statsLabelThroughput, diagnostics.throughputKbps.map { strings.statsBitrate(kbps: $0.int32Value) } ?? none))
        rows.append((strings.statsLabelBuffer, diagnostics.bufferMs.map { strings.statsBuffer(milliseconds: $0.int64Value) } ?? none))
        return rows
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(alignment: .firstTextBaseline, spacing: Space.s3) {
                    Text(row.label)
                        .foregroundStyle(.secondary)
                        .frame(minWidth: 88, alignment: .leading)
                    Text(row.value)
                        .foregroundStyle(.primary)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .font(.caption2)
        .monospacedDigit()
        .padding(.horizontal, Space.s3)
        .padding(.vertical, Space.s2)
        .frame(maxWidth: 280, alignment: .leading)
        .background(Brand.scrim, in: RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
        .allowsHitTesting(false)
    }
}

/// "Skip intro", "Skip credits": a secondary button (aqua is for Play), shown for the whole segment
/// whether or not the controls are up. Real Liquid Glass on iOS 26, the scrim before that. The label
/// is the shared model's wording.
struct SkipButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label.titleCased())
                .font(.headline)
                .foregroundStyle(.primary)
                .padding(.horizontal, Space.s4)
                .frame(minHeight: 44)
                .contentShape(Capsule())
        }
        .modifier(SkipButtonStyle())
    }
}

private struct SkipButtonStyle: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .buttonStyle(.glass)
                .buttonBorderShape(.capsule)
        } else {
            content
                .buttonStyle(.plain)
                .background(Capsule().fill(Brand.scrim))
        }
    }
}
