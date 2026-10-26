import Foundation
import Shared
import VideoToolbox

/// Makes a downloaded video playable by AVPlayer: FFmpeg (the small LGPL build in `Vendor/ffmpeg`) rewrites an MKV as MP4, copying the
/// video and converting only audio AVPlayer cannot play. The shared code calls it on a background thread when a download ends.
final class FFmpegVideoConverter: NativeVideoConverter {
    private final class Progress {
        let report: (KotlinFloat) -> KotlinBoolean
        var lastReport = Date.distantPast
        init(_ report: @escaping (KotlinFloat) -> KotlinBoolean) { self.report = report }
    }

    /// AV1 plays only on chips with a hardware decoder (A17 Pro and later); everything else about a file can be converted.
    private func updateAV1Support() {
        ff_set_av1_playable(VTIsHardwareDecodeSupported(kCMVideoCodecType_AV1) ? 1 : 0)
    }

    func canPlayVideo(codec: String) -> Bool {
        updateAV1Support()
        return ff_can_play_video(codec) != 0
    }

    func convert(source: String, target: String, onProgress: @escaping (KotlinFloat) -> KotlinBoolean) -> Int32 {
        updateAV1Support()
        let progress = Progress(onProgress)
        var message = [CChar](repeating: 0, count: 256)
        let result = withExtendedLifetime(progress) {
            ff_remux(source, target, { context, fraction in
                guard let context else { return 0 }
                let progress = Unmanaged<Progress>.fromOpaque(context).takeUnretainedValue()
                // Often enough to stop quickly and to show movement, not on every packet.
                guard Date().timeIntervalSince(progress.lastReport) > 0.25 else { return 0 }
                progress.lastReport = Date()
                return progress.report(KotlinFloat(float: Float(fraction))).boolValue ? 1 : 0
            }, Unmanaged.passUnretained(progress).toOpaque(), &message, message.count)
        }
        if result == FFRemuxFailed || result == FFRemuxUnsupportedVideo {
            NSLog("VibeyFin remux: %@", String(cString: message))
        }
        return Int32(result.rawValue)
    }
}
