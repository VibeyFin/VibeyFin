import AVFoundation
import SwiftUI

/// One colour of the picture, each channel 0...1.
struct GlowColour: Equatable {
    let red: Double
    let green: Double
    let blue: Double

    var color: Color { Color(.sRGB, red: red, green: green, blue: blue, opacity: 1) }
}

/// The colours read from one frame, left to right. `generation` tells one reading from the next so the view can crossfade.
struct PictureGlow: Equatable {
    let generation: Int
    let colours: [GlowColour]
}

enum PictureColours {
    static let stopCount: Int = 4

    private static let bottomShare: Double = 0.3
    private static let pixelStep: Int = 4
    private static let blackFloor: UInt8 = 20
    private static let brightnessCap: Double = 0.85
    private static let changeThreshold: Double = 0.03
    private static let bytesPerPixel: Int = 4

    /// The average colour of four columns across the lower third of a BGRA frame. Near-black pixels are left out so the bars
    /// baked into a wide film don't turn the glow black. Brightness is capped, hue and saturation are kept.
    static func bottomEdge(bgra: UnsafePointer<UInt8>, width: Int, height: Int, bytesPerRow: Int) -> [GlowColour] {
        guard width >= stopCount, height > 0 else { return [] }

        let firstRow: Int       = height - max(1, Int(Double(height) * bottomShare))
        var sums: [[Double]]    = Array(repeating: [0, 0, 0], count: stopCount)
        var counts: [Int]       = Array(repeating: 0, count: stopCount)

        for row in stride(from: firstRow, to: height, by: pixelStep) {
            for column in stride(from: 0, to: width, by: pixelStep) {
                let offset: Int = row * bytesPerRow + column * bytesPerPixel
                let blue: UInt8  = bgra[offset]
                let green: UInt8 = bgra[offset + 1]
                let red: UInt8   = bgra[offset + 2]
                guard max(red, green, blue) > blackFloor else { continue }
                let stop: Int = min(column * stopCount / width, stopCount - 1)
                sums[stop][0] += Double(red)
                sums[stop][1] += Double(green)
                sums[stop][2] += Double(blue)
                counts[stop] += 1
            }
        }

        return (0..<stopCount).map { stop in
            guard counts[stop] > 0 else { return GlowColour(red: 0, green: 0, blue: 0) }
            let share: Double = 255 * Double(counts[stop])
            return capped(GlowColour(red: sums[stop][0] / share, green: sums[stop][1] / share, blue: sums[stop][2] / share))
        }
    }

    static func bottomEdge(of buffer: CVPixelBuffer) -> [GlowColour] {
        CVPixelBufferLockBaseAddress(buffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(buffer) else { return [] }
        return bottomEdge(
            bgra: base.assumingMemoryBound(to: UInt8.self),
            width: CVPixelBufferGetWidth(buffer),
            height: CVPixelBufferGetHeight(buffer),
            bytesPerRow: CVPixelBufferGetBytesPerRow(buffer)
        )
    }

    /// Whether a new reading differs enough from the last one to be worth a crossfade.
    static func differs(_ colours: [GlowColour], from last: [GlowColour]?) -> Bool {
        guard let last, last.count == colours.count else { return true }
        return zip(colours, last).contains { new, old in
            max(abs(new.red - old.red), abs(new.green - old.green), abs(new.blue - old.blue)) > changeThreshold
        }
    }

    private static func capped(_ colour: GlowColour) -> GlowColour {
        let brightest: Double = max(colour.red, colour.green, colour.blue)
        guard brightest > brightnessCap else { return colour }
        let scale: Double = brightnessCap / brightest
        return GlowColour(red: colour.red * scale, green: colour.green * scale, blue: colour.blue * scale)
    }
}

/// Reads the colour of the lower edge of the picture an AVPlayer shows, about every two seconds, while `run` is going.
/// Without a source (HDR, or a software-decoded title) there is nothing to read and `glow` stays nil.
@MainActor
final class PictureColourSampler: ObservableObject {
    @Published private(set) var glow: PictureGlow?

    private static let interval: Duration = .seconds(2)
    private static let firstReadingInterval: Duration = .milliseconds(500)
    private static let missesBeforeNudge: Int = 3

    private let source: @MainActor () -> AVPlayerItem?
    private var output: AVPlayerItemVideoOutput?
    private weak var attachedItem: AVPlayerItem?
    private var generation: Int = 0
    private var missedReadings: Int = 0

    init(source: @escaping @MainActor () -> AVPlayerItem?) {
        self.source = source
    }

    static var isThermallyLimited: Bool {
        ProcessInfo.processInfo.thermalState.rawValue >= ProcessInfo.ThermalState.serious.rawValue
    }

    /// Reads until cancelled. `oneShot` stops after the first reading, for Reduce Motion.
    func run(oneShot: Bool) async {
        defer { detach() }
        while !Task.isCancelled {
            if !Self.isThermallyLimited {
                sample()
                if oneShot && glow != nil { return }
            }
            try? await Task.sleep(for: glow == nil ? Self.firstReadingInterval : Self.interval)
        }
    }

    private func sample() {
        guard let item = source() else {
            detach()
            glow = nil
            return
        }
        let videoOutput: AVPlayerItemVideoOutput = output(for: item)
        let time: CMTime = videoOutput.itemTime(forHostTime: CACurrentMediaTime())
        guard videoOutput.hasNewPixelBuffer(forItemTime: time),
              let buffer = videoOutput.copyPixelBuffer(forItemTime: time, itemTimeForDisplay: nil) else {
            nudgeIfPaused(item)
            return
        }
        missedReadings = 0
        let colours: [GlowColour] = PictureColours.bottomEdge(of: buffer)
        guard colours.count == PictureColours.stopCount, PictureColours.differs(colours, from: glow?.colours) else { return }
        generation += 1
        glow = PictureGlow(generation: generation, colours: colours)
    }

    /// A paused item hands a newly attached output no frame; seeking in place makes it draw the current one.
    private func nudgeIfPaused(_ item: AVPlayerItem) {
        guard glow == nil, item.status == .readyToPlay else { return }
        missedReadings += 1
        guard missedReadings == Self.missesBeforeNudge else { return }
        item.seek(to: item.currentTime(), toleranceBefore: .zero, toleranceAfter: .zero, completionHandler: nil)
    }

    private func output(for item: AVPlayerItem) -> AVPlayerItemVideoOutput {
        if let output, attachedItem === item { return output }
        detach()
        missedReadings = 0
        let attributes: [String: Any] = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        let made = AVPlayerItemVideoOutput(pixelBufferAttributes: attributes)
        item.add(made)
        output = made
        attachedItem = item
        return made
    }

    private func detach() {
        if let output { attachedItem?.remove(output) }
        output = nil
        attachedItem = nil
    }
}
