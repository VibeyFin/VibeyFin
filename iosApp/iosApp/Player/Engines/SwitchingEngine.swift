import Foundation
import Shared
import UIKit

/// Plays each stream in the player the shared model chose for it (`PlaybackStream.engine`): AVPlayer, or Aether for a file
/// AVPlayer can't take. The controls talk to this one object; it passes everything on to the player in use, and mounts that
/// player's picture in the one view the screen holds. A player is only built when a stream needs it.
@MainActor
final class SwitchingEngine: PlaybackEngine {
    var events = PlaybackEngineEvents() {
        didSet { forEachEngine { $0.events = events } }
    }

    private let native: PlaybackEngine = AVPlayerEngine()
    private var aether: PlaybackEngine?
    private var current: PlaybackEngine
    private let container = UIView()
    private weak var pip: PlayerPictureInPicture?

    // What the controls asked for, to hand to a player that starts later.
    private var rate: Float = 1
    private var audio: AudioChoice?
    private var subtitle: SubtitleChoice?
    private var diagnostics = false

    init() {
        current = native
        container.backgroundColor = .black
    }

    var currentSeconds: TimeInterval? { current.currentSeconds }
    var speed: Float { current.speed }

    // MARK: - Opening

    func open(_ stream: PlaybackStream, url: URL, bufferAheadSeconds: Int32) {
        let next = engine(for: stream.engine)
        if next !== current {
            current.close()
            current = next
            mount()
            next.events = events
            next.setSpeed(rate, applyNow: false)
            next.wantDiagnostics(diagnostics)
        }
        PlayerLog.write("engine \(type(of: current)) for \(url.path)")
        current.open(stream, url: url, bufferAheadSeconds: bufferAheadSeconds)
        current.wantAudio(audio)
        current.wantSubtitle(subtitle)
    }

    func close() { forEachEngine { $0.close() } }

    func shutdown() { forEachEngine { $0.shutdown() } }

    private func engine(for choice: PlaybackEngineChoice) -> PlaybackEngine {
        guard choice == .aether else { return native }
        if aether == nil { aether = AetherPlaybackEngine() }
        return aether ?? native
    }

    private func forEachEngine(_ body: (PlaybackEngine) -> Void) {
        body(native)
        if let aether { body(aether) }
    }

    // MARK: - Commands

    func play() { current.play() }
    func pause() { current.pause() }

    func setSpeed(_ speed: Float, applyNow: Bool) {
        rate = speed
        current.setSpeed(speed, applyNow: applyNow)
    }

    func seek(to seconds: TimeInterval, completion: @escaping (Bool) -> Void) {
        current.seek(to: seconds, completion: completion)
    }

    func wantAudio(_ choice: AudioChoice?) {
        audio = choice
        current.wantAudio(choice)
    }

    func wantSubtitle(_ choice: SubtitleChoice?) {
        subtitle = choice
        current.wantSubtitle(choice)
    }

    func wantDiagnostics(_ wanted: Bool) {
        diagnostics = wanted
        current.wantDiagnostics(wanted)
    }

    // MARK: - Video

    func makeVideoView(pip: PlayerPictureInPicture) -> UIView {
        self.pip = pip
        mount()
        return container
    }

    /// Puts the picture of the player in use into the container, in place of the last one.
    private func mount() {
        container.subviews.forEach { $0.removeFromSuperview() }
        guard let pip else { return }
        let view = current.makeVideoView(pip: pip)
        view.frame = container.bounds
        view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        container.addSubview(view)
    }
}
