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
        rows.append((strings.statsLabelEngine, diagnostics.engine ?? none))
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

/// "Skip intro", "Skip credits": a glass capsule with a leading skip symbol,
/// shown for the whole segment whether or not the controls are up. Real Liquid Glass on iOS 26, the
/// scrim before that. The label is the shared model's wording. `systemImage` is nil for a plain
/// quiet button such as "Stop watching".
struct SkipButton: View {
    let label: String
    var systemImage: String? = "forward.end.fill"
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Space.s2) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .accessibilityHidden(true)
                }
                Text(label.titleCased())
            }
                .font(.headline)
                .foregroundStyle(.primary)
                .padding(.horizontal, Space.s4)
                .frame(minHeight: 44)
                .contentShape(Capsule())
        }
        .modifier(SkipButtonStyle())
    }
}

struct SkipButtonStyle: ViewModifier {
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

/// "Are you still watching?" over the paused video, after several episodes in a row that started by
/// themselves. It covers the picture and takes every touch. Keeping on is the one primary action;
/// stopping is the quiet one. The wording is the shared model's.
struct StillWatchingPrompt: View {
    let onContinue: () -> Void
    let onStop: () -> Void

    var body: some View {
        ZStack {
            Brand.scrim.ignoresSafeArea()
            VStack(spacing: Space.s4) {
                Text(Strings.shared.stillWatchingPrompt)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.center)
                HStack(spacing: Space.s3) {
                    SkipButton(label: Strings.shared.stillWatchingStop, systemImage: nil, action: onStop)
                    Button(action: onContinue) {
                        Text(Strings.shared.stillWatchingContinue.titleCased())
                            .font(.headline)
                            .foregroundStyle(Color(.systemBackground))
                            .padding(.horizontal, Space.s4)
                            .frame(minHeight: 44)
                            .background(Capsule().fill(Color.primary))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(Space.s5)
        }
        // Nothing underneath is pressed while the question is up.
        .contentShape(Rectangle())
        .onTapGesture {}
        .accessibilityElement(children: .contain)
    }
}

/// The thumbnail with the time under it, as the design draws it: no box, the picture at `size` with the pop
/// shadow and an outline.
struct ScrubPreview: View {
    let thumbnail: UIImage
    let time: String
    let size: CGSize

    var body: some View {
        VStack(spacing: Space.s1) {
            Image(uiImage: thumbnail)
                .resizable()
                .scaledToFill()
                .frame(width: size.width, height: size.height)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous)
                        .strokeBorder(Brand.overlayWeak, lineWidth: 1)
                }
                .shadow(color: Brand.scrimStrong, radius: 12, y: 8)
            Text(time)
                .font(.footnote)
                .monospacedDigit()
                .foregroundStyle(.primary)
                .shadow(color: Brand.scrimStrong, radius: 2)
        }
        .fixedSize()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Where the preview sits along the bar.
enum ScrubPreviewPlacement {
    /// Centred on the thumb (`fraction` along the bar) but kept inside the bar's width, so it never leaves the screen.
    static func x(barWidth: CGFloat, previewWidth: CGFloat, fraction: CGFloat) -> CGFloat {
        min(max(barWidth * fraction - previewWidth / 2, 0), max(barWidth - previewWidth, 0))
    }
}

/// A short message over the picture, on the scrim. Takes no touches.
struct PlayerNotice: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(Brand.onMedia)
            .padding(.horizontal, Space.s4)
            .padding(.vertical, Space.s2)
            .background(Brand.scrimStrong, in: Capsule())
            .padding(.top, Space.s7 + Space.s4)
            .allowsHitTesting(false)
            .transition(.opacity)
    }
}

/// Under the top row, from the screen margin: it takes no touches, so it needn't keep out of the strip.
struct StatsPanel: View {
    let diagnostics: PlaybackDiagnostics

    var body: some View {
        VStack {
            HStack {
                StatsOverlay(diagnostics: diagnostics)
                Spacer(minLength: 0)
            }
            Spacer(minLength: 0)
        }
        .screenMargin()
        .padding(.top, Space.s7 + Space.s6 + Space.s2)
        .ignoresSafeArea(.container, edges: .horizontal)
        .allowsHitTesting(false)
    }
}

/// Everything over the video sits on the scrim.
struct PlayerScrims: View {
    var body: some View {
        VStack(spacing: 0) {
            LinearGradient(colors: [Brand.scrim, .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: 120)
            Spacer(minLength: 0)
            LinearGradient(colors: [.clear, Brand.scrim], startPoint: .top, endPoint: .bottom)
                .frame(height: 160)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

/// Bottom trailing; rises above the scrubber and the times while the controls show.
struct SkipOfferOverlay: View {
    let label: String
    let raised: Bool
    let action: () -> Void

    var body: some View {
        VStack {
            Spacer(minLength: 0)
            HStack {
                Spacer(minLength: 0)
                SkipButton(label: label, action: action)
            }
        }
        .screenMargin()
        .padding(.bottom, raised ? Space.s7 + Space.s6 : Space.s4)
    }
}

/// While skips in a row show: the total sits on the side of the skip and only the scrubber stays.
struct SeekingOverlay<Bar: View>: View {
    let side: SeekSide
    let text: String
    let taps: Int
    @ViewBuilder let bar: Bar

    var body: some View {
        ZStack {
            GeometryReader { proxy in
                SeekIndicator(forward: side == .forward, text: text, taps: taps)
                    .frame(width: proxy.size.width * 0.44, height: proxy.size.height)
                    .frame(maxWidth: .infinity, alignment: side == .forward ? .trailing : .leading)
            }
            .ignoresSafeArea()
            VStack {
                Spacer(minLength: 0)
                bar
            }
        }
        .allowsHitTesting(false)
        .transition(.asymmetric(insertion: .opacity.animation(.easeOut(duration: 0.08)), removal: .opacity.animation(.easeOut(duration: 0.06))))
    }
}
