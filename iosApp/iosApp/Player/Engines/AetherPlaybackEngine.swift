import AetherEngine
import AVFoundation
import AVKit
import Combine
import Foundation
import Shared
import UIKit

/// Plays with Aether (FFmpeg demux, then AVPlayer through a loopback HLS stream or software decode). Its
/// headers option carries the token, so no address trick is needed. No picture-in-picture yet.
/// All of it happens on the main thread.
@MainActor
final class AetherPlaybackEngine: PlaybackEngine {
    private let engine: AetherEngine
    private let videoView = AetherPlayerView()
    private let subtitleOverlay = ImageSubtitleOverlay()
    var events = PlaybackEngineEvents()

    private var isOpen = false
    private var generation = 0
    private var loadTask: Task<Void, Never>?
    private var subscriptions = Set<AnyCancellable>()
    private var readyReported = false
    private var rate: Float = 1
    private var wantedAudio: AudioChoice?
    private var appliedAudio: AudioChoice?
    private var wantedSubtitle: SubtitleChoice?
    private var appliedSubtitle: SubtitleChoice?
    private var diagnosticsWanted = false
    private var seeksInFlight = 0
    private weak var pip: PlayerPictureInPicture?
    private var externalPlayback: AnyCancellable?
    private var bufferAheadSeconds: Int32 = 0
    /// What the load found in the file, for the stats.
    private var videoInfo: VideoInfo?

    init?() {
        guard let engine = try? AetherEngine() else { return nil }
        EngineLog.handler = { line in
            if !line.contains("subtitle-delivery") { PlayerLog.write("engine \(line)") }
        }
        self.engine = engine
        engine.bind(view: videoView)
        subtitleOverlay.frame = videoView.bounds
        subtitleOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        videoView.addSubview(subtitleOverlay)
        videoView.isAccessibilityElement = false
        subscribe()
    }

    var currentSeconds: TimeInterval? {
        isOpen ? engine.clock.currentTime : nil
    }

    var speed: Float { rate }

    private static let probeBytes: Int64 = 10_000_000
    private static let probeMicroseconds: Int64 = 5_000_000

    private func subscribe() {
        engine.$playbackPhase
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] phase in self?.phaseChanged(phase) }
            .store(in: &subscriptions)
        engine.$duration
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] seconds in
                guard let self, self.isOpen, seconds > 0 else { return }
                self.events.duration(seconds)
            }
            .store(in: &subscriptions)
        engine.clock.$currentTime
            .combineLatest(engine.clock.$bufferedPosition)
            .throttle(for: .milliseconds(500), scheduler: DispatchQueue.main, latest: true)
            .sink { [weak self] time, buffered in
                guard let self, self.isOpen, self.seeksInFlight == 0 else { return }
                self.events.time(time, max(buffered, time))
                if self.diagnosticsWanted { self.pushDiagnostics() }
            }
            .store(in: &subscriptions)
        engine.$softwarePiPSource
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.attachPictureInPicture() }
            .store(in: &subscriptions)
        engine.$audioTracks
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.applyAudio() }
            .store(in: &subscriptions)
        subscribeToSubtitles()
        engine.$videoRoute
            .combineLatest(engine.$audioDelivery)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self, self.diagnosticsWanted else { return }
                self.pushDiagnostics()
            }
            .store(in: &subscriptions)
    }

    private func subscribeToSubtitles() {
        engine.$subtitleTracks
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.applySubtitle() }
            .store(in: &subscriptions)
        engine.$subtitleCues
            .combineLatest(engine.clock.$sourceTime)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] cues, time in
                guard let self else { return }
                guard self.isOpen, self.appliedSubtitle?.position != nil else { return self.subtitleOverlay.clear() }
                let images = cues.compactMap { cue -> ImageCue? in
                    guard case .image(let image) = cue.body else { return nil }
                    return ImageCue(id: cue.id, start: cue.startTime, end: cue.endTime, image: image)
                }
                self.subtitleOverlay.show(images, at: time)
            }
            .store(in: &subscriptions)
    }

    // MARK: - Opening

    func open(_ stream: PlaybackStream, url: URL, bufferAheadSeconds: Int32) {
        loadTask?.cancel()
        isOpen = true
        generation += 1
        readyReported = false
        appliedAudio = nil
        appliedSubtitle = nil
        subtitleOverlay.clear()
        seeksInFlight = 0
        let opened = generation
        var options = LoadOptions()
        options.httpHeaders = stream.headers
        // A remux with dozens of sparse subtitle tracks makes the stream probe read tens of megabytes before it settles.
        options.probesize = Self.probeBytes
        options.maxAnalyzeDuration = Self.probeMicroseconds
        // The app paints subtitles itself, so the fonts attached to an MKV (tens of MB on anime releases) are never used.
        options.skipsContainerAttachments = true
        let start = TimeInterval(stream.startPositionMs) / 1000
        self.bufferAheadSeconds = bufferAheadSeconds
        videoInfo = nil
        PlayerLog.write("aether open \(url.path) buffer=\(bufferAheadSeconds) s")
        loadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let probe = try await self.engine.load(url: url, startPosition: start > 0 ? start : nil, options: options)
                guard self.generation == opened, self.isOpen else { return }
                let audio = self.engine.audioTracks.map { "\($0.codec)/\($0.channels)ch/\($0.language ?? "?")" }
                self.videoInfo = VideoInfo(
                    codec: probe?.videoCodecName, width: probe?.videoWidth ?? 0, height: probe?.videoHeight ?? 0,
                    frameRate: probe?.videoFrameRate
                )
                PlayerLog.write("aether probe video=\(probe?.videoCodecName ?? "?") \(probe?.videoWidth ?? 0)x\(probe?.videoHeight ?? 0) dv=\(probe?.isDolbyVision ?? false) audio=\(audio) subs=\(self.engine.subtitleTracks.count)")
                PlayerLog.write("aether loaded route=\(self.engine.videoRoute) audio=\(self.engine.audioDelivery) format=\(self.engine.videoFormat)")
                self.engine.play()
                self.attachPictureInPicture()
                self.applyAudio()
                self.subtitleOverlay.videoSize = CGSize(width: CGFloat(probe?.videoWidth ?? 0), height: CGFloat(probe?.videoHeight ?? 0))
                self.applySubtitle()
            } catch is CancellationError {
                return
            } catch {
                guard self.generation == opened, self.isOpen else { return }
                PlayerLog.write("aether load failed \(error)")
                self.events.failed(Self.failure(of: error))
            }
        }
    }

    func close() {
        loadTask?.cancel()
        isOpen = false
        wantedAudio = nil
        appliedAudio = nil
        wantedSubtitle = nil
        appliedSubtitle = nil
        subtitleOverlay.clear()
        engine.stop()
    }

    func shutdown() {
        close()
        subscriptions.removeAll()
    }

    // MARK: - Commands

    func play() {
        engine.play()
    }

    func pause() {
        engine.pause()
    }

    func setSpeed(_ speed: Float, applyNow: Bool) {
        rate = speed
        engine.setRate(speed)
    }

    func seek(to seconds: TimeInterval, completion: @escaping (Bool) -> Void) {
        seeksInFlight += 1
        let opened = generation
        Task { @MainActor [weak self] in
            guard let self else { return }
            await self.engine.seek(to: seconds)
            // A reopen resets the count; a seek from the earlier stream must not take it below zero.
            if self.generation == opened { self.seeksInFlight -= 1 }
            completion(true)
        }
    }

    // MARK: - Audio track and diagnostics

    func wantAudio(_ choice: AudioChoice?) {
        guard choice != wantedAudio else { return }
        wantedAudio = choice
        applyAudio()
    }

    private func applyAudio() {
        guard isOpen, let choice = wantedAudio, choice != appliedAudio else { return }
        let tracks = engine.audioTracks
        guard tracks.count == choice.count, tracks.indices.contains(choice.position) else { return }
        engine.selectAudioTrack(index: tracks[choice.position].id)
        appliedAudio = choice
    }

    func wantSubtitle(_ choice: SubtitleChoice?) {
        guard choice != wantedSubtitle else { return }
        wantedSubtitle = choice
        applySubtitle()
    }

    /// Shows the wanted image track, matched by the file's stream index, or clears the one showing.
    private func applySubtitle() {
        guard isOpen, wantedSubtitle != appliedSubtitle else { return }
        guard let choice = wantedSubtitle, let index = choice.index else {
            if appliedSubtitle != nil { engine.clearSubtitle() }
            appliedSubtitle = wantedSubtitle
            return
        }
        guard engine.subtitleTracks.contains(where: { $0.id == index }) else { return }
        engine.selectSubtitleTrack(index: index)
        appliedSubtitle = choice
    }

    func wantDiagnostics(_ wanted: Bool) {
        guard wanted != diagnosticsWanted else { return }
        diagnosticsWanted = wanted
        if wanted { pushDiagnostics() }
    }

    /// How far the loaded range reaches past the playhead, from the same clock the progress bar draws.
    private func bufferedMilliseconds() -> KotlinLong? {
        let ahead = engine.clock.bufferedPosition - engine.clock.currentTime
        guard ahead.isFinite, ahead >= 0 else { return nil }
        return KotlinLong(value: Int64(min(ahead * 1000, Double(Int32.max))))
    }

    private func pushDiagnostics() {
        let route = engine.videoRoute == .none ? nil : Strings.shared.statsEngineAether(route: "\(engine.videoRoute)")
        let log = engine.softwarePiPSource == nil ? engine.nativePlayerLayer?.player?.currentItem?.accessLog()?.events : nil
        let latest = log?.last
        let dropped = (log ?? []).map(\.numberOfDroppedVideoFrames).filter { $0 >= 0 }
        events.diagnostics(LiveDiagnostics(
            videoCodec: videoInfo?.codec,
            audioCodec: engine.audioDelivery == .none ? nil : "\(engine.audioDelivery)",
            width: videoInfo.flatMap { $0.width > 0 ? KotlinInt(int: $0.width) : nil },
            height: videoInfo.flatMap { $0.height > 0 ? KotlinInt(int: $0.height) : nil },
            frameRate: videoInfo?.frameRate.map { KotlinFloat(float: Float($0)) },
            bitrateKbps: latest.flatMap { Self.kbps($0.indicatedBitrate) },
            droppedFrames: dropped.isEmpty ? nil : KotlinInt(int: Int32(clamping: dropped.reduce(0, +))),
            throughputKbps: latest.flatMap { Self.kbps($0.observedBitrate) },
            bufferMs: bufferedMilliseconds(),
            engine: route
        ))
    }

    /// A positive, finite bit rate in kilobits per second; AVFoundation reports -1 and NaN for what it doesn't know.
    private static func kbps(_ bitsPerSecond: Double) -> KotlinInt? {
        guard bitsPerSecond.isFinite, bitsPerSecond > 0 else { return nil }
        return KotlinInt(int: Int32(min(bitsPerSecond / 1000, Double(Int32.max))))
    }

    // MARK: - State

    private func phaseChanged(_ phase: PlaybackPhase) {
        guard isOpen else { return }
        PlayerLog.write("aether phase \(phase)")
        switch phase {
        case .idle:
            break
        case .loading, .seeking, .rebuffering, .stalled:
            events.state(true, true)
        case .playing:
            events.state(true, false)
            if !readyReported {
                readyReported = true
                events.ready()
            }
        case .paused:
            events.state(false, false)
        case .ended:
            events.state(false, false)
            events.ended()
        case .error(let message):
            PlayerLog.write("aether error \(message) info=\(String(describing: engine.errorInfo))")
            events.failed(Self.failure(of: engine.errorInfo))
        }
    }

    private static func failure(of error: Error) -> PlaybackFailure {
        let nsError = error as NSError
        return nsError.domain == NSURLErrorDomain ? .network : .unsupported
    }

    private static func failure(of info: PlaybackErrorInfo?) -> PlaybackFailure {
        guard let info else { return .unsupported }
        return info.underlyingDomain == NSURLErrorDomain || info.kind == .sourceRateLimited ? .network : .unsupported
    }

    // MARK: - Video

    func makeVideoView(pip: PlayerPictureInPicture) -> UIView {
        self.pip = pip
        pip.activeChanged = { [weak self] active in self?.engine.pictureInPictureActive = active }
        attachPictureInPicture()
        return videoView
    }

    // MARK: - Picture in picture

    /// The window follows the stream: AVPlayer's own layer on the native routes, the engine's frame layer on the software one.
    /// Called again whenever a stream comes up, since each has its own layer.
    private func attachPictureInPicture() {
        guard let pip else { return }
        if let source = engine.softwarePiPSource {
            pip.replace(with: source.layer, playback: SoftwarePiPPlayback(source: source))
        } else if let layer = engine.nativePlayerLayer {
            pip.replace(with: layer)
        }
        let player = engine.softwarePiPSource == nil ? engine.nativePlayerLayer?.player : nil
        applyBuffer(to: player)
        watchExternalPlayback(of: player)
    }

    /// The native route plays the engine's local stream in an AVPlayer, so the buffer setting reaches it as AVPlayer's forward buffer.
    /// The software route has its own read-ahead, which the engine does not expose.
    private func applyBuffer(to player: AVPlayer?) {
        guard bufferAheadSeconds > 0, let item = player?.currentItem else { return }
        item.preferredForwardBufferDuration = TimeInterval(bufferAheadSeconds)
    }

    /// The native route plays in an AVPlayer, which hands the picture to an AirPlay receiver by itself; the screen needs to know.
    private func watchExternalPlayback(of player: AVPlayer?) {
        externalPlayback = nil
        guard let player else {
            events.externalPlayback(false)
            return
        }
        externalPlayback = player.publisher(for: \.isExternalPlaybackActive)
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] active in self?.events.externalPlayback(active) }
    }
}

/// Answers the picture-in-picture window's transport for frames Aether draws itself.
private final class SoftwarePiPPlayback: NSObject, AVPictureInPictureSampleBufferPlaybackDelegate {
    private let source: SoftwarePiPSource

    init(source: SoftwarePiPSource) {
        self.source = source
    }

    func pictureInPictureController(_ controller: AVPictureInPictureController, setPlaying playing: Bool) {
        MainActor.assumeIsolated { source.setPlaying(playing) }
    }

    func pictureInPictureControllerTimeRangeForPlayback(_ controller: AVPictureInPictureController) -> CMTimeRange {
        MainActor.assumeIsolated { source.timeRange() }
    }

    func pictureInPictureControllerIsPlaybackPaused(_ controller: AVPictureInPictureController) -> Bool {
        MainActor.assumeIsolated { source.isPaused }
    }

    func pictureInPictureController(_ controller: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {}

    func pictureInPictureController(_ controller: AVPictureInPictureController, skipByInterval skipInterval: CMTime, completion completionHandler: @escaping () -> Void) {
        MainActor.assumeIsolated { source.skip(by: skipInterval.seconds) }
        completionHandler()
    }
}

/// The video the load found, for the stats.
private struct VideoInfo {
    let codec: String?
    let width: Int32
    let height: Int32
    let frameRate: Double?
}
