import AVFoundation
import Combine
import Foundation
import OSLog
import Shared

/// What the player tells the shared playback model. Positions are milliseconds.
struct PlayerEvents {
    var playing: (Int64) -> Void = { _ in }
    var paused: (Int64) -> Void = { _ in }
    var seeked: (Int64) -> Void = { _ in }
    var position: (Int64) -> Void = { _ in }
    var ended: (Int64) -> Void = { _ in }
    var failed: (PlaybackFailure) -> Void = { _ in }
    var diagnostics: (LiveDiagnostics) -> Void = { _ in }
}

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
}

/// The audio track the shared state marks as selected, as its place in the file's audio list and how
/// long that list is. The list is in the file's order, which is the order AVFoundation lists them in.
struct AudioChoice: Equatable {
    let position: Int
    let count: Int
}

/// Owns the `AVPlayer` for one playback screen: opens the stream the shared state names, keeps
/// the position and duration the controls draw, and turns what the player does into `PlayerEvents`.
/// All of it happens on the main thread.
final class PlayerController: ObservableObject {
    let player = AVPlayer()

    /// Timings for the slow-load and slow-seek reports.
    private static let timing = Logger(subsystem: "com.liamgleeson.vibeyfin", category: "timing")
    private var openedAt: CFTimeInterval = 0

    /// Seconds. Follows the player about twice a second.
    @Published private(set) var position: TimeInterval = 0
    /// Seconds; 0 until the item knows its length.
    @Published private(set) var duration: TimeInterval = 0
    /// The player means to play: it is playing, or waiting for data to. A stall is not a pause.
    @Published private(set) var isPlaying = false
    /// Waiting for data, on a first load or after a stall or a seek.
    @Published private(set) var isBuffering = false

    var events = PlayerEvents()

    private var openedStreamId: Int32?
    private var pendingStart: TimeInterval?
    /// The audio to play, when the player has to choose it (a direct play). Nil: leave the player's own choice.
    private var wantedAudio: AudioChoice?
    /// What is selected on the open item, so the same choice isn't applied twice.
    private var appliedAudio: AudioChoice?
    private var lastPushedSecond = -1
    /// Seeks the player hasn't finished or cancelled yet.
    private var seeksInFlight = 0
    private var failureReported = false
    private var endReported = false

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
            .sink { [weak self] status in self?.timeControlChanged(status) }
            .store(in: &playerSubscriptions)
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            self?.tick(time)
        }
    }

    deinit {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
    }

    /// The current position in milliseconds, for the shared model.
    var currentMilliseconds: Int64 {
        let seconds = player.currentTime().seconds
        return Int64((seconds.isFinite ? max(0, seconds) : position) * 1000)
    }

    // MARK: - Opening

    /// Opens `stream` unless it is the one already open. A new `id` is a new stream: the first,
    /// a retry, or the transcode fallback after a direct play that failed.
    func open(_ stream: PlaybackStream) {
        guard stream.id != openedStreamId else { return }
        openedStreamId = stream.id
        openedAt = CACurrentMediaTime()
        itemSubscriptions.removeAll()
        failureReported = false
        endReported = false
        lastPushedSecond = -1
        appliedAudio = nil
        lastDiagnostics = nil
        trackInfo = TrackInfo()
        // The player may still say "paused" for the old item; the new stream starts from "not playing".
        isPlaying = false

        guard let url = URL(string: stream.url) else {
            player.replaceCurrentItem(with: nil)
            reportFailure(nil, item: nil)
            return
        }
        // The server wants its Authorization header on every request the player makes for this stream.
        let asset = AVURLAsset(url: url, options: ["AVURLAssetHTTPHeaderFieldsKey": stream.headers])
        let item = AVPlayerItem(asset: asset)
        observe(item)
        player.replaceCurrentItem(with: item)

        let start = TimeInterval(stream.startPositionMs) / 1000
        position = start
        duration = 0
        if start > 0 {
            // Seek first and play once there, so a resume doesn't flash the first frames.
            pendingStart = start
        } else {
            pendingStart = nil
            player.play()
        }
    }

    /// Drops the stream, e.g. after the last attempt failed and the shared state shows a message.
    func clear() {
        itemSubscriptions.removeAll()
        pendingStart = nil
        openedStreamId = nil
        wantedAudio = nil
        appliedAudio = nil
        isPlaying = false
        isBuffering = false
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    /// Stops everything and silences the events. Called when the screen goes away.
    func tearDown() {
        events = PlayerEvents()
        clear()
        playerSubscriptions.removeAll()
        position = 0
        duration = 0
        // Lets music or a podcast that was playing underneath carry on.
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: - Audio session

    /// Plays with sound whatever the silent switch says, as a video player does.
    func activateAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback)
        try? session.setActive(true)
    }

    // MARK: - Audio track

    /// Plays this audio track of the open file, now or once the item is ready. Pass nil when the
    /// stream already has the right audio (the server chose it), so the player doesn't touch it.
    /// Nothing here tells the shared model: the choice only ever flows from the model to the player.
    func wantAudio(_ choice: AudioChoice?) {
        guard choice != wantedAudio else { return }
        wantedAudio = choice
        if let item = player.currentItem, item.status == .readyToPlay {
            applyAudio(to: item)
        }
    }

    private func applyAudio(to item: AVPlayerItem) {
        guard let choice = wantedAudio, choice != appliedAudio else { return }
        let streamId = openedStreamId
        Task { @MainActor [weak self, weak item] in
            guard let item,
                  let group = try? await item.asset.loadMediaSelectionGroup(for: .audible) else { return }
            // Gone, or replaced by another stream or another wish, while the group was loading.
            guard let self, self.player.currentItem === item, self.openedStreamId == streamId,
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

    /// Turns the stats on or off. Switching on works out the values at once.
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
                    info.videoCodec = fourCC.map(Self.codecName)
                    if let rate = try? await track.load(.nominalFrameRate), rate > 0 { info.frameRate = rate }
                case .audio where info.audioCodec == nil:
                    info.audioCodec = fourCC.map(Self.codecName)
                default: break
                }
            }
            guard let self, let item, self.player.currentItem === item else { return }
            self.trackInfo = info
            self.pushDiagnostics()
        }
    }

    private static func fourCC(_ code: FourCharCode) -> String {
        let bytes = [24, 16, 8, 0].map { UInt8((code >> $0) & 0xFF) }
        return String(decoding: bytes, as: UTF8.self)
    }

    /// The short names the Android player shows: `avc1` is "h264", `mp4a` is "aac".
    private static func codecName(_ fourCC: String) -> String {
        switch fourCC.lowercased() {
        case "avc1", "avc3": return "h264"
        case "hvc1", "hev1": return "hevc"
        case "dvh1", "dvhe": return "dolby vision"
        case "av01": return "av1"
        case "vp09": return "vp9"
        case "vp08": return "vp8"
        case "mp4v": return "mpeg4"
        case "mp4a": return "aac"
        case "ac-3": return "ac3"
        case "ec-3": return "eac3"
        case "alac": return "alac"
        case "flac": return "flac"
        case "opus": return "opus"
        case ".mp3": return "mp3"
        default: return fourCC.trimmingCharacters(in: .whitespaces).lowercased()
        }
    }

    /// Reads the item's access log, size and buffer, and tells the shared model when something changed.
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
            width: size.width > 0 ? Int32(size.width) : nil,
            height: size.height > 0 ? Int32(size.height) : nil,
            frameRate: trackInfo.frameRate,
            bitrateKbps: latest.flatMap { Self.kbps($0.indicatedBitrate) },
            droppedFrames: dropped.isEmpty ? nil : Int32(dropped.reduce(0, +)),
            throughputKbps: latest.flatMap { Self.kbps($0.observedBitrate) },
            bufferMs: buffered.flatMap { $0.isFinite ? Int64(max(0, $0) * 1000) : nil }
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
            bufferMs: snapshot.bufferMs.map(KotlinLong.init)
        ))
    }

    /// AVFoundation uses -1 or 0 for "not known".
    private static func kbps(_ bitsPerSecond: Double) -> Int32? {
        bitsPerSecond > 0 ? Int32(bitsPerSecond / 1000) : nil
    }

    // MARK: - Commands

    func play() {
        player.play()
    }

    func pause() {
        player.pause()
    }

    /// Plays at `speed` from now on. `defaultRate` is what `play()` starts at, so a pause and resume (or a
    /// seek that resumes) keeps it; setting `rate` straight away would start a paused video, so it only
    /// changes the live rate while playing.
    func setSpeed(_ speed: Float) {
        guard player.defaultRate != speed else { return }
        player.defaultRate = speed
        if isPlaying { player.rate = speed }
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { play() }
    }

    /// Moves to `seconds`, or the nearest keyframe, which is much quicker than decoding up to the exact
    /// frame. `onSeeked` follows once the player got there.
    func seek(to seconds: TimeInterval) {
        let limit = duration > 0 ? duration : seconds
        let target = CMTime(seconds: min(max(0, seconds), limit), preferredTimescale: 600)
        position = target.seconds
        seeksInFlight += 1
        let startedAt = CACurrentMediaTime()
        player.seek(to: target) { [weak self] finished in
            DispatchQueue.main.async {
                guard let self else { return }
                self.seeksInFlight -= 1
                // A seek that a newer one cut short is not reported; the newer one will be.
                guard finished else { return }
                Self.timing.debug("seek finished after \(Int((CACurrentMediaTime() - startedAt) * 1000)) ms")
                self.events.seeked(self.currentMilliseconds)
            }
        }
    }

    // MARK: - Player and item state

    private func timeControlChanged(_ status: AVPlayer.TimeControlStatus) {
        isBuffering = status == .waitingToPlayAtSpecifiedRate && openedStreamId != nil
        let intendsToPlay = status != .paused
        guard intendsToPlay != isPlaying else { return }
        isPlaying = intendsToPlay
        guard openedStreamId != nil else { return }
        if intendsToPlay {
            events.playing(currentMilliseconds)
        } else {
            events.paused(currentMilliseconds)
        }
    }

    private func observe(_ item: AVPlayerItem) {
        item.publisher(for: \.status)
            .receive(on: DispatchQueue.main)
            .sink { [weak self, weak item] status in
                guard let self, let item else { return }
                switch status {
                case .readyToPlay: self.itemIsReady(item)
                case .failed: self.reportFailure(item.error, item: item)
                default: break
                }
            }
            .store(in: &itemSubscriptions)
        item.publisher(for: \.duration)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] time in
                if time.isNumeric, time.seconds > 0 { self?.duration = time.seconds }
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
            .sink { [weak self] _ in self?.reportEnded() }
            .store(in: &itemSubscriptions)
        NotificationCenter.default
            .publisher(for: .AVPlayerItemFailedToPlayToEndTime, object: item)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] notification in
                let error = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error
                self?.reportFailure(error, item: notification.object as? AVPlayerItem)
            }
            .store(in: &itemSubscriptions)
    }

    private func itemIsReady(_ item: AVPlayerItem) {
        Self.timing.debug("item ready \(Int((CACurrentMediaTime() - self.openedAt) * 1000)) ms after the stream was opened")
        if item.duration.isNumeric, item.duration.seconds > 0 { duration = item.duration.seconds }
        applyAudio(to: item)
        loadTrackInfo(for: item)
        guard let start = pendingStart else { return }
        pendingStart = nil
        player.seek(
            to: CMTime(seconds: start, preferredTimescale: 600),
            toleranceBefore: .zero,
            toleranceAfter: .zero
        ) { [weak self] _ in
            DispatchQueue.main.async { self?.player.play() }
        }
    }

    private func tick(_ time: CMTime) {
        guard time.isNumeric, openedStreamId != nil else { return }
        // While a seek is pending the player still reports the old time; keep showing the target.
        guard seeksInFlight == 0 else { return }
        position = time.seconds
        if diagnosticsWanted, CACurrentMediaTime() - lastDiagnosticsAt >= 1 { pushDiagnostics() }
        // The shared model hears the position about once a second.
        let second = Int(time.seconds)
        if second != lastPushedSecond {
            lastPushedSecond = second
            events.position(currentMilliseconds)
        }
    }

    private func reportFailure(_ error: Error?, item: AVPlayerItem?) {
        guard !failureReported else { return }
        failureReported = true
        events.failed(PlaybackFailure.of(error, item: item))
    }

    private func reportEnded() {
        guard !endReported else { return }
        endReported = true
        events.ended(currentMilliseconds)
    }
}
