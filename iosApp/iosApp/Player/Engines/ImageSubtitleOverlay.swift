import AetherEngine
import UIKit

/// One image subtitle and when it shows, in the source's time.
struct ImageCue {
    let id: Int
    let start: Double
    let end: Double
    let image: SubtitleImage
}

/// Paints the image subtitles (PGS, DVD, DVB) Aether decodes over the video, one layer per cue.
/// Cue positions are fractions of the subtitle canvas, which is laid width-aligned and centred on the video.
final class ImageSubtitleOverlay: UIView {
    /// The picture's size in pixels; the canvas stands in for it when the cue does not say.
    var videoSize: CGSize = .zero {
        didSet { if videoSize != oldValue { setNeedsLayout() } }
    }

    private var cues: [ImageCue] = []
    private var shownIDs: [Int] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        // The engine adds its video layer to the host view after this one; the subtitles stay above it.
        layer.zPosition = 100
    }

    required init?(coder: NSCoder) { nil }

    /// Shows the image cues that cover `time`; nothing when none do.
    func show(_ all: [ImageCue], at time: Double) {
        let active = all.filter { $0.start <= time && time < $0.end }
        let ids = active.map(\.id)
        guard ids != shownIDs else { return }
        shownIDs = ids
        cues = active
        setNeedsLayout()
    }

    func clear() {
        guard !shownIDs.isEmpty else { return }
        shownIDs = []
        cues = []
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.sublayers?.forEach { $0.removeFromSuperlayer() }
        let video = videoRect()
        for cue in cues {
            let piece = CALayer()
            piece.contents = cue.image.cgImage
            piece.contentsGravity = .resize
            piece.frame = frame(of: cue.image, in: video)
            layer.addSublayer(piece)
        }
    }

    /// Where the picture sits in the view, aspect-fit.
    private func videoRect() -> CGRect {
        guard videoSize.width > 0, videoSize.height > 0, bounds.width > 0, bounds.height > 0 else { return bounds }
        let scale = min(bounds.width / videoSize.width, bounds.height / videoSize.height)
        let size = CGSize(width: videoSize.width * scale, height: videoSize.height * scale)
        return CGRect(x: bounds.midX - size.width / 2, y: bounds.midY - size.height / 2, width: size.width, height: size.height)
    }

    private func frame(of image: SubtitleImage, in video: CGRect) -> CGRect {
        let canvas = image.canvasSize
        let area: CGRect
        if canvas.width > 0, canvas.height > 0 {
            let height = canvas.height * video.width / canvas.width
            area = CGRect(x: video.minX, y: video.midY - height / 2, width: video.width, height: height)
        } else {
            area = video
        }
        let place = image.position
        return CGRect(x: area.minX + place.minX * area.width, y: area.minY + place.minY * area.height,
                      width: place.width * area.width, height: place.height * area.height)
    }
}
