import Foundation
import Shared
import UIKit

/// What an engine tells `PlayerController`. Every callback arrives on the main thread. Times are seconds.
struct PlaybackEngineEvents {
    /// The player means to play (it plays, or waits for data to) and whether it is waiting for data right now.
    var state: (_ intendsToPlay: Bool, _ waitingForData: Bool) -> Void = { _, _ in }
    /// The opened stream can play: its length and tracks are known.
    var ready: () -> Void = {}
    var duration: (TimeInterval) -> Void = { _ in }
    /// The position and the end of the buffered range holding it, about twice a second. Not sent while a seek is pending.
    var time: (_ position: TimeInterval, _ buffered: TimeInterval) -> Void = { _, _ in }
    var ended: () -> Void = {}
    var failed: (PlaybackFailure) -> Void = { _ in }
    /// The picture is on, or no longer on, another screen over AirPlay.
    var externalPlayback: (Bool) -> Void = { _ in }
    var diagnostics: (LiveDiagnostics) -> Void = { _ in }
}

/// The part of playback that differs between engines: decoding and the video surface. Controls, the shared
/// model's events, Now Playing and the position clock stay in `PlayerController`, so every engine gets them alike.
protocol PlaybackEngine: AnyObject {
    var events: PlaybackEngineEvents { get set }

    /// The position in seconds; nil until the engine knows it.
    var currentSeconds: TimeInterval? { get }
    /// The rate `play()` starts at.
    var speed: Float { get }

    /// Opens `stream`, starts at its start position and plays. `url` is the stream's address.
    func open(_ stream: PlaybackStream, url: URL, bufferAheadSeconds: Int32)
    /// Drops the open stream and stops. The engine can open another one afterwards.
    func close()
    /// Stops watching the player itself. Called when the screen goes away; the controller silences the events.
    func shutdown()

    func play()
    func pause()
    /// Plays at `speed` from now on. `applyNow` says the player is playing, so the live rate follows too.
    func setSpeed(_ speed: Float, applyNow: Bool)
    /// Moves to `seconds`, or the nearest keyframe. `completion` gets whether the seek finished or a newer one cut it short.
    func seek(to seconds: TimeInterval, completion: @escaping (Bool) -> Void)

    /// Plays this audio track of the open file, now or once it is ready.
    func wantAudio(_ choice: AudioChoice?)
    /// Shows this embedded subtitle of the open file, now or once it is ready; nil when the engine has none to show itself.
    func wantSubtitle(_ choice: SubtitleChoice?)
    /// Turns the stats on or off.
    func wantDiagnostics(_ wanted: Bool)

    /// The view the video is drawn in. Ties picture-in-picture to it where the engine can.
    func makeVideoView(pip: PlayerPictureInPicture) -> UIView
}

extension PlaybackEngine {
    /// Engines that don't draw subtitles themselves have nothing to show.
    func wantSubtitle(_ choice: SubtitleChoice?) {}
}
