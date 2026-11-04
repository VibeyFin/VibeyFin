import AVFoundation
import Combine
import Foundation
import OSLog
import Shared
import UIKit

/// What the stats overlay needs from the player, kept as plain values so a repeat can be skipped.
/// Anything AVFoundation can't say is nil.
private struct DiagnosticsSnapshot: Equatable {
    var videoCodec: String?
    var audioCodec: String?
    var width: Int32?
    var height: Int32?
    var frameRate: Float?
    var bitrateKbps: Int32?
    var droppedFrames: Int32?
    var throughputKbps: Int32?
    var bufferMs: Int64?
    var engine: String?
}

/// The audio track the shared state marks as selected, as its place in the file's audio list and how
/// long that list is. The list is in the file's order, which is the order AVFoundation lists them in.
struct AudioChoice: Equatable {
    let position: Int
    let count: Int
}

/// The embedded image subtitle the shared state marks as selected: its place among all of the file's subtitles and how
/// many there are, and the server's stream index of the selected one. No position means none is showing.
struct SubtitleChoice: Equatable {
    let position: Int?
    let count: Int
    let index: Int?
}

/// Plays with `AVPlayer`: the system's own decoders, HDR and Dolby Vision, picture-in-picture and AirPlay.
/// All of it happens on the main thread.
final class AVPlayerEngine: PlaybackEngine {
    private let player = AVPlayer()
    var events = PlaybackEngineEvents()

    /// Timings for the slow-load and slow-seek reports.
    private static let timing = Logger(subsystem: "app.vibeyfin", category: "timing")
    private var openedAt: CFTimeInterval = 0

    /// A stream is open. `openCount` tells one stream from the next while work for the old one is still in flight.
    private var isOpen = false
    private var openCount = 0
    /// The audio to play, when the player has to choose it (a direct play). Nil: leave the player's own choice.
    private var wantedAudio: AudioChoice?
    /// What is selected on the open item, so the same choice isn't applied twice.
    private var appliedAudio: AudioChoice?
    /// Seeks the player hasn't finished or cancelled yet.
    private var seeksInFlight = 0
    /// Where the opened stream should start, until the item is ready. Over AirPlay the early seek can
    /// be dropped and the stream starts at 0:00, so `itemIsReady` checks it landed.
    private var pendingStart: TimeInterval?

    /// The stats overlay is on, so diagnostics are worth working out.
    private var diagnosticsWanted = false
    private var lastDiagnostics: DiagnosticsSnapshot?
    private var lastDiagnosticsAt: CFTimeInterval = 0
    /// What the open item's tracks say; read once per item and audio choice.
    private var trackInfo = TrackInfo()

    private struct TrackInfo {
        var videoCodec: String?
        var audioCodec: String?
        var frameRate: Float?
    }

    private var timeObserver: Any?
    private var playerSubscriptions = Set<AnyCancellable>()
    private var itemSubscriptions = Set<AnyCancellable>()

    init() {
        player.publisher(for: \.timeControlStatus)
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                self?.events.state(status != .paused, status == .waitingToPlayAtSpecifiedRate)
            }
            .store(in: &playerSubscriptions)
        player.publisher(for: \.isExternalPlaybackActive)
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] active in self?.events.externalPlayback(active) }
            .store(in: &playerSubscriptions)
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            MainActor.assumeIsolated { self?.tick(time) }
        }
    }

    deinit {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
    }

    var currentSeconds: TimeInterval? {
        let seconds = player.currentTime().seconds
        return seconds.isFinite ? seconds : nil
    }

    var speed: Float { player.defaultRate }

    // MARK: - Opening

    func open(_ stream: PlaybackStream, url: URL, bufferAheadSeconds: Int32) {
        openedAt = CACurrentMediaTime()
        isOpen = true
        openCount += 1
        itemSubscriptions.removeAll()
        appliedAudio = nil
        pendingStart = nil
        lastDiagnostics = nil
        trackInfo = TrackInfo()

        // AVPlayer passes HDR through by itself; nothing here changes that. The log shows what it was given and what
        // the display offers, since neither simulator can show real HDR.
        Self.timing.info("picture \(String(describing: stream.dynamicRange), privacy: .public); display HDR: \(Self.availableHDRModes(), privacy: .public)")
        // The server wants its Authorization header on every request the player makes for this stream.
        let asset = AVURLAsset(url: url, options: ["AVURLAssetHTTPHeaderFieldsKey": stream.headers])
        let item = AVPlayerItem(asset: asset)
        // 0 is Auto: leave AVPlayer's own choice. A change applies from the next video.
        if bufferAheadSeconds > 0 { item.preferredForwardBufferDuration = TimeInterval(bufferAheadSeconds) }
        observe(item)
        player.replaceCurrentItem(with: item)

        let start = TimeInterval(stream.startPositionMs) / 1000
        if start > 0 {
            pendingStart = start
            // The seek goes in before the item is ready; the player applies it once it can, so the first
            // frames aren't shown and a transcode starts at the resume point. Seeking after the item is
            // ready makes the server drop the transcode it began at 0 and restart it, which takes seconds.
            seeksInFlight += 1
            player.seek(
                to: CMTime(seconds: start, preferredTimescale: 600),
                toleranceBefore: .zero,
                toleranceAfter: .zero
            ) { [weak self] _ in
                DispatchQueue.main.async { self?.seeksInFlight -= 1 }
            }
        }
        player.play()
    }

    func close() {
        itemSubscriptions.removeAll()
        isOpen = false
        pendingStart = nil
        wantedAudio = nil
        appliedAudio = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    func shutdown() {
        close()
        playerSubscriptions.removeAll()
    }

    // MARK: - Audio track

    func wantAudio(_ choice: AudioChoice?) {
        guard choice != wantedAudio else { return }
        wantedAudio = choice
        if let item = player.currentItem, item.status == .readyToPlay {
            applyAudio(to: item)
        }
    }

    private func applyAudio(to item: AVPlayerItem) {
        guard let choice = wantedAudio, choice != appliedAudio else { return }
        let opened = openCount
        Task { @MainActor [weak self, weak item] in
            guard let item,
                  let group = try? await item.asset.loadMediaSelectionGroup(for: .audible) else { return }
            // Gone, or replaced by another stream or another wish, while the group was loading.
            guard let self, self.player.currentItem === item, self.isOpen, self.openCount == opened,
                  self.wantedAudio == choice else { return }
            // The group lists every audio track in the file's order. If it doesn't have as many as the
            // server counted, there is no telling which is which, so none is chosen.
            let options = group.options
            guard options.count == choice.count, options.indices.contains(choice.position) else { return }
            item.select(options[choice.position], in: group)
            self.appliedAudio = choice
            self.loadTrackInfo(for: item)
        }
    }

    // MARK: - Diagnostics

    func wantDiagnostics(_ wanted: Bool) {
        guard wanted != diagnosticsWanted else { return }
        diagnosticsWanted = wanted
        lastDiagnostics = nil
        if wanted {
            if let item = player.currentItem, item.status == .readyToPlay { loadTrackInfo(for: item) }
            pushDiagnostics()
        }
    }

    /// Codecs and frame rate come from the format descriptions of the tracks that are playing.
    private func loadTrackInfo(for item: AVPlayerItem) {
        guard diagnosticsWanted else { return }
        let tracks = item.tracks.filter { $0.isEnabled }.compactMap { $0.assetTrack }
        Task { @MainActor [weak self, weak item] in
            var info = TrackInfo()
            for track in tracks {
                guard let descriptions = try? await track.load(.formatDescriptions) else { continue }
                let fourCC = descriptions.first.map { Self.fourCC(CMFormatDescriptionGetMediaSubType($0)) }
                switch track.mediaType {
                case .video where info.videoCodec == nil:
                    info.videoCodec = Strings.shared.codecName(code: fourCC)
                    if let rate = try? await track.load(.nominalFrameRate), rate > 0 { info.frameRate = rate }
                case .audio where info.audioCodec == nil:
                    info.audioCodec = Strings.shared.codecName(code: fourCC)
                default: break
                }
            }
            guard let self, let item, self.player.currentItem === item else { return }
            self.trackInfo = info
            self.pushDiagnostics()
        }
    }

    /// The HDR formats the display and device can show, by name, for the log.
    private static func availableHDRModes() -> String {
        let modes = AVPlayer.availableHDRModes
        var names: [String] = []
        if modes.contains(.hdr10) { names.append("HDR10") }
        if modes.contains(.hlg) { names.append("HLG") }
        if modes.contains(.dolbyVision) { names.append("Dolby Vision") }
        return names.isEmpty ? "none" : names.joined(separator: ", ")
    }

    private static func fourCC(_ code: FourCharCode) -> String {
        let bytes = [24, 16, 8, 0].map { UInt8((code >> $0) & 0xFF) }
        return String(bytes: bytes, encoding: .utf8) ?? ""
    }

    /// Reads the item's access log, size and buffer, and tells the controller when something changed.
    private func pushDiagnostics() {
        guard diagnosticsWanted, let item = player.currentItem else { return }
        lastDiagnosticsAt = CACurrentMediaTime()
        let events = item.accessLog()?.events ?? []
        let latest = events.last
        let dropped = events.map(\.numberOfDroppedVideoFrames).filter { $0 >= 0 }
        let size = item.presentationSize
        let now = player.currentTime()
        let buffered = item.loadedTimeRanges
            .map(\.timeRangeValue)
            .first { $0.containsTime(now) }
            .map { CMTimeSubtract($0.end, now).seconds }
        let snapshot = DiagnosticsSnapshot(
            videoCodec: trackInfo.videoCodec,
            audioCodec: trackInfo.audioCodec,
            width: Self.positiveInt32(Double(size.width)),
            height: Self.positiveInt32(Double(size.height)),
            frameRate: trackInfo.frameRate,
            bitrateKbps: latest.flatMap { Self.kbps($0.indicatedBitrate) },
            droppedFrames: dropped.isEmpty ? nil : Int32(clamping: dropped.reduce(0, &+)),
            throughputKbps: latest.flatMap { Self.kbps($0.observedBitrate) },
            bufferMs: buffered.flatMap { $0.isFinite ? Int64(min(max(0, $0) * 1000, Double(Int32.max))) : nil },
            engine: Strings.shared.statsEngineAvPlayer
        )
        guard snapshot != lastDiagnostics else { return }
        lastDiagnostics = snapshot
        self.events.diagnostics(LiveDiagnostics(
            videoCodec: snapshot.videoCodec,
            audioCodec: snapshot.audioCodec,
            width: snapshot.width.map(KotlinInt.init),
            height: snapshot.height.map(KotlinInt.init),
            frameRate: snapshot.frameRate.map(KotlinFloat.init),
            bitrateKbps: snapshot.bitrateKbps.map(KotlinInt.init),
            droppedFrames: snapshot.droppedFrames.map(KotlinInt.init),
            throughputKbps: snapshot.throughputKbps.map(KotlinInt.init),
            bufferMs: snapshot.bufferMs.map(KotlinLong.init),
            engine: snapshot.engine
        ))
    }

    /// AVFoundation uses -1 or 0 for "not known".
    private static func kbps(_ bitsPerSecond: Double) -> Int32? {
        positiveInt32(bitsPerSecond / 1000)
    }

    /// `value` as an `Int32` when it is a positive, finite number; nil otherwise, and `Int32.max` above it.
    /// `Int32(_:)` traps on infinity, NaN or anything out of range, and AVFoundation can report those.
    private static func positiveInt32(_ value: Double) -> Int32? {
        guard value.isFinite, value > 0 else { return nil }
        return value >= Double(Int32.max) ? Int32.max : Int32(value)
    }

    // MARK: - Commands

    func play() {
        player.play()
    }

    func pause() {
        player.pause()
    }

    /// `defaultRate` is what `play()` starts at, so a pause and resume (or a seek that resumes) keeps it; setting
    /// `rate` straight away would start a paused video, so it only changes the live rate while playing.
    func setSpeed(_ speed: Float, applyNow: Bool) {
        guard player.defaultRate != speed else { return }
        player.defaultRate = speed
        if applyNow { player.rate = speed }
    }

    func seek(to seconds: TimeInterval, completion: @escaping (Bool) -> Void) {
        seeksInFlight += 1
        let startedAt = CACurrentMediaTime()
        player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600)) { [weak self] finished in
            DispatchQueue.main.async {
                guard let self else { return }
                self.seeksInFlight -= 1
                // A seek that a newer one cut short is not reported; the newer one will be.
                guard finished else { return completion(false) }
                Self.timing.debug("seek finished after \(Int((CACurrentMediaTime() - startedAt) * 1000)) ms")
                completion(true)
            }
        }
    }

    // MARK: - Item state

    private func observe(_ item: AVPlayerItem) {
        item.publisher(for: \.status)
            .receive(on: DispatchQueue.main)
            .sink { [weak self, weak item] status in
                guard let self, let item else { return }
                switch status {
                case .readyToPlay: self.itemIsReady(item)
                case .failed: self.events.failed(PlaybackFailure.of(item.error, item: item))
                default: break
                }
            }
            .store(in: &itemSubscriptions)
        item.publisher(for: \.duration)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] time in
                if time.isNumeric, time.seconds > 0 { self?.events.duration(time.seconds) }
            }
            .store(in: &itemSubscriptions)
        NotificationCenter.default
            .publisher(for: AVPlayerItem.newAccessLogEntryNotification, object: item)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.pushDiagnostics() }
            .store(in: &itemSubscriptions)
        item.publisher(for: \.presentationSize)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.pushDiagnostics() }
            .store(in: &itemSubscriptions)
        NotificationCenter.default
            .publisher(for: .AVPlayerItemDidPlayToEndTime, object: item)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.events.ended() }
            .store(in: &itemSubscriptions)
        NotificationCenter.default
            .publisher(for: .AVPlayerItemFailedToPlayToEndTime, object: item)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] notification in
                let error = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error
                self?.events.failed(PlaybackFailure.of(error, item: notification.object as? AVPlayerItem))
            }
            .store(in: &itemSubscriptions)
    }

    private func itemIsReady(_ item: AVPlayerItem) {
        Self.timing.debug("item ready \(Int((CACurrentMediaTime() - self.openedAt) * 1000)) ms after the stream was opened")
        if item.duration.isNumeric, item.duration.seconds > 0 { events.duration(item.duration.seconds) }
        confirmStartPosition()
        events.ready()
        applyAudio(to: item)
        loadTrackInfo(for: item)
    }

    /// Seeks again to the wanted start when the early seek didn't land. Only over AirPlay: locally the early seek
    /// is what keeps the transcode from restarting.
    private func confirmStartPosition() {
        guard let start = pendingStart else { return }
        pendingStart = nil
        guard Self.needsStartSeek(start: start, current: player.currentTime().seconds, external: player.isExternalPlaybackActive) else { return }
        seeksInFlight += 1
        player.seek(
            to: CMTime(seconds: start, preferredTimescale: 600),
            toleranceBefore: .zero,
            toleranceAfter: .zero
        ) { [weak self] _ in
            DispatchQueue.main.async { self?.seeksInFlight -= 1 }
        }
    }

    nonisolated static func needsStartSeek(start: TimeInterval, current: TimeInterval, external: Bool) -> Bool {
        guard external, start > 0 else { return false }
        return !current.isFinite || abs(current - start) > 1
    }

    /// The end of the loaded range that holds `time`; `time` itself when none does.
    private func bufferedPosition(at time: CMTime) -> TimeInterval {
        let end = player.currentItem?.loadedTimeRanges
            .map(\.timeRangeValue)
            .first { $0.containsTime(time) }
            .map { CMTimeRangeGetEnd($0).seconds }
        return max(end.flatMap { $0.isFinite ? $0 : nil } ?? time.seconds, time.seconds)
    }

    private func tick(_ time: CMTime) {
        guard time.isNumeric, isOpen else { return }
        // While a seek is pending the player still reports the old time; keep showing the target.
        guard seeksInFlight == 0 else { return }
        events.time(time.seconds, bufferedPosition(at: time))
        if diagnosticsWanted, CACurrentMediaTime() - lastDiagnosticsAt >= 1 { pushDiagnostics() }
    }

    // MARK: - Video

    func makeVideoView(pip: PlayerPictureInPicture) -> UIView {
        let view = PlayerLayerView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        view.backgroundColor = .systemBackground
        // The controls carry the accessibility; the picture is not an element of its own.
        view.isAccessibilityElement = false
        pip.replace(with: view.playerLayer)
        return view
    }
}

/// A view whose layer is an `AVPlayerLayer`.
final class PlayerLayerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer {
        // `layerClass` makes this always an AVPlayerLayer.
        unsafeDowncast(layer, to: AVPlayerLayer.self)
    }
}
