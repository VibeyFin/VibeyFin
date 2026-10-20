import AVFoundation
import Combine
import Foundation
import Shared

/// What the player tells the shared playback model. Positions are milliseconds.
struct PlayerEvents {
    var playing: (Int64) -> Void = { _ in }
    var paused: (Int64) -> Void = { _ in }
    var seeked: (Int64) -> Void = { _ in }
    var position: (Int64) -> Void = { _ in }
    var ended: (Int64) -> Void = { _ in }
    var failed: (PlaybackFailure) -> Void = { _ in }
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
    private var failureReported = false
    private var endReported = false

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
        itemSubscriptions.removeAll()
        failureReported = false
        endReported = false
        lastPushedSecond = -1
        appliedAudio = nil
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
        }
    }

    // MARK: - Commands

    func play() {
        player.play()
    }

    func pause() {
        player.pause()
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { play() }
    }

    /// Moves to `seconds` exactly. `onSeeked` follows once the player got there.
    func seek(to seconds: TimeInterval) {
        let limit = duration > 0 ? duration : seconds
        let target = CMTime(seconds: min(max(0, seconds), limit), preferredTimescale: 600)
        position = target.seconds
        player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] finished in
            // A seek that a newer one cut short is not reported; the newer one will be.
            guard finished else { return }
            DispatchQueue.main.async {
                guard let self else { return }
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
        if item.duration.isNumeric, item.duration.seconds > 0 { duration = item.duration.seconds }
        applyAudio(to: item)
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
        position = time.seconds
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
