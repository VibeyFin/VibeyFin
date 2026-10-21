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
                    SkipButton(label: Strings.shared.stillWatchingStop, action: onStop)
                    PrimaryButton(action: onContinue) {
                        Text(Strings.shared.stillWatchingContinue.titleCased())
                    }
                    .fixedSize()
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

/// The scrub-preview thumbnails of one playback: fetches the tile images with the stream's headers,
/// keeps them decoded in a small in-memory cache, and crops one thumbnail out by the shared maths.
/// Make a new one for a new track (the next episode); letting go of it drops the cache. A tile that
/// can't be loaded is remembered as missing, so scrubbing carries on with the timestamp alone.
@MainActor
final class TrickplayTiles {
    private let track: TrickplayTrack
    private let headers: [String: String]
    private let tiles = NSCache<NSNumber, TileBox>()
    private var failed = Set<Int>()
    private var loading: [Int: Task<CGImage?, Never>] = [:]

    init(track: TrickplayTrack, headers: [String: String]) {
        self.track = track
        self.headers = headers
        // About 32 MB of decoded tiles; the cache drops the rest and a tile is simply fetched again.
        tiles.totalCostLimit = 32 * 1024 * 1024
    }

    /// Whether this is the set of `track`, so a new one is only made when the item changes.
    func isFor(_ other: TrickplayTrack) -> Bool { track.tilesUrl == other.tilesUrl }

    /// The thumbnail for `positionMs`, or nil when its tile can't be had.
    func thumbnail(atMs positionMs: Int64) async -> UIImage? {
        let spot = track.locate(positionMs: positionMs)
        guard let tile = await tile(Int(spot.tileIndex)) else { return nil }
        let width = Int(track.thumbWidth)
        let height = Int(track.thumbHeight)
        let rect = CGRect(x: Int(spot.column) * width, y: Int(spot.row) * height, width: width, height: height)
        guard CGRect(x: 0, y: 0, width: tile.width, height: tile.height).contains(rect),
              let cropped = tile.cropping(to: rect) else { return nil }
        return UIImage(cgImage: cropped)
    }

    private func tile(_ index: Int) async -> CGImage? {
        if let cached = tiles.object(forKey: NSNumber(value: index)) { return cached.image }
        if failed.contains(index) { return nil }
        if let running = loading[index] { return await running.value }
        // The download and the JPEG decode (a tile is a few megapixels) happen off the main actor.
        let task = Task.detached { [track, headers] () -> CGImage? in
            guard let url = URL(string: track.tileUrl(tileIndex: Int32(index))) else { return nil }
            var request = URLRequest(url: url)
            headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
            guard let (data, response) = try? await URLSession.shared.data(for: request),
                  (response as? HTTPURLResponse)?.statusCode == 200,
                  let image = UIImage(data: data)?.preparingForDisplay() else { return nil }
            return image.cgImage
        }
        loading[index] = task
        let image = await task.value
        loading[index] = nil
        if let image {
            tiles.setObject(TileBox(image), forKey: NSNumber(value: index), cost: image.bytesPerRow * image.height)
        } else {
            failed.insert(index)
        }
        return image
    }
}

private final class TileBox {
    let image: CGImage
    init(_ image: CGImage) { self.image = image }
}

/// The thumbnail with the time under it, on the scrim, drawn above the scrubber's thumb.
struct ScrubPreview: View {
    let thumbnail: UIImage
    let time: String

    /// The width the preview takes, for keeping it on screen.
    static let width: CGFloat = 160 + 2 * Space.s1

    var body: some View {
        VStack(spacing: Space.s1) {
            Image(uiImage: thumbnail)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 160)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork / 2, style: .continuous))
            Text(time)
                .font(.footnote)
                .monospacedDigit()
                .foregroundStyle(.primary)
        }
        .padding(Space.s1)
        .background(Brand.scrim, in: RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
