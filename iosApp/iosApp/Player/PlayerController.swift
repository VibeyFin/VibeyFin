import AVFoundation
import Combine
import Foundation
import MediaPlayer
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

/// Drives one playback screen: opens the stream the shared state names on its `PlaybackEngine`, keeps
/// the position and duration the controls draw, and turns what the engine does into `PlayerEvents`.
/// All of it happens on the main thread.
final class PlayerController: ObservableObject {
    let engine: PlaybackEngine

    /// The position and buffered position, which tick twice a second. They live in their own object so only the
    /// views that draw them re-evaluate on a tick, not the whole player.
    let clock = PlayerClock()

    /// Seconds. Follows the player about twice a second.
    private(set) var position: TimeInterval {
        get { clock.position }
        set { clock.position = newValue }
    }
    /// Seconds; 0 until the item knows its length.
    @Published private(set) var duration: TimeInterval = 0
    /// Seconds. The end of the loaded range holding the position; the position itself when none does.
    private(set) var bufferedPosition: TimeInterval {
        get { clock.bufferedPosition }
        set { clock.bufferedPosition = newValue }
    }
    /// The player means to play: it is playing, or waiting for data to. A stall is not a pause.
    @Published private(set) var isPlaying = false
    /// Waiting for data, on a first load or after a stall or a seek.
    @Published private(set) var isBuffering = false
    /// The video plays on another screen over AirPlay; the phone shows the controls over a backdrop.
    @Published private(set) var isExternalPlayback = false
    /// The receiver's name while `isExternalPlayback`; nil when the system gave none.
    @Published private(set) var externalDeviceName: String?

    var events = PlayerEvents()

    private var openedStreamId: Int32?
    private var lastPushedSecond = -1
    /// Where the opened stream should start, until the engine says it is ready.
    private var pendingStart: TimeInterval?
    private let perf = PerfProbe()
    private var failureReported = false
    private var airPlayWatch: DispatchWorkItem?
    /// What the server says the item runs, in seconds.
    private var serverRuntime: TimeInterval = 0
    private var endReported = false

    private var playerSubscriptions = Set<AnyCancellable>()

    private func engineDuration(_ seconds: TimeInterval) {
        duration = max(seconds, serverRuntime)
    }

    init(engine: PlaybackEngine = AVPlayerEngine()) {
        self.engine = engine
        engine.events = PlaybackEngineEvents(
            state: { [weak self] intendsToPlay, waiting in self?.stateChanged(intendsToPlay: intendsToPlay, waiting: waiting) },
            ready: { [weak self] in self?.pendingStart = nil },
            duration: { [weak self] seconds in self?.engineDuration(seconds) },
            time: { [weak self] position, buffered in self?.tick(position: position, buffered: buffered) },
            ended: { [weak self] in self?.reportEnded() },
            failed: { [weak self] failure in self?.reportFailure(failure) },
            externalPlayback: { [weak self] active in self?.externalPlaybackChanged(active) },
            diagnostics: { [weak self] diagnostics in self?.events.diagnostics(diagnostics) }
        )
        NotificationCenter.default.publisher(for: AVAudioSession.routeChangeNotification)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.refreshExternalDevice() }
            .store(in: &playerSubscriptions)
    }

    /// The current position in milliseconds, for the shared model.
    var currentMilliseconds: Int64 {
        // Until the item is ready the player says 0:00, which would overwrite the position being resumed.
        if let pendingStart { return Int64(pendingStart * 1000) }
        let seconds = engine.currentSeconds.map { max(0, $0) } ?? position
        return Int64(seconds * 1000)
    }

    // MARK: - Opening

    /// Opens `stream` unless it is the one already open. A new `id` is a new stream: the first,
    /// a retry, or the transcode fallback after a direct play that failed.
    func open(_ stream: PlaybackStream, bufferAheadSeconds: Int32 = 0) {
        guard stream.id != openedStreamId else { return }
        openedStreamId = stream.id
        failureReported = false
        endReported = false
        lastPushedSecond = -1
        pendingStart = nil
        // The player may still say "paused" for the old item; the new stream starts from "not playing".
        isPlaying = false

        guard let url = URL(string: stream.url) else {
            engine.close()
            reportFailure(.unsupported)
            return
        }
        let start = TimeInterval(stream.startPositionMs) / 1000
        position = start
        bufferedPosition = start
        serverRuntime = TimeInterval(stream.runtimeMs?.int64Value ?? 0) / 1000
        duration = 0
        if start > 0 { pendingStart = start }
        perf.start(engine: String(describing: type(of: engine)), title: url.path)
        engine.open(stream, url: url, bufferAheadSeconds: bufferAheadSeconds)
    }

    /// Drops the stream, e.g. after the last attempt failed and the shared state shows a message.
    func clear() {
        openedStreamId = nil
        pendingStart = nil
        isPlaying = false
        isBuffering = false
        engine.close()
    }

    /// Stops everything and silences the events. Called when the screen goes away.
    func tearDown() {
        events = PlayerEvents()
        nowPlayingTitle = nil
        artworkTask?.cancel()
        nowPlayingArtworkUrl = nil
        nowPlayingArtwork = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        clear()
        perf.stop()
        engine.shutdown()
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

    // MARK: - Audio track and diagnostics

    /// Plays this audio track of the open file, now or once the item is ready. Pass nil when the
    /// stream already has the right audio (the server chose it), so the player doesn't touch it.
    /// Nothing here tells the shared model: the choice only ever flows from the model to the player.
    func wantAudio(_ choice: AudioChoice?) {
        engine.wantAudio(choice)
    }

    /// Shows this embedded subtitle of the open file; nil when the app paints the subtitle or the server burns it in.
    func wantSubtitle(_ choice: SubtitleChoice?) {
        wantedImageSubtitle = choice?.position != nil
        engine.wantSubtitle(choice)
        burnInForAirPlay()
    }

    /// Whether the player draws an embedded image subtitle itself, which an AirPlay receiver can't be given.
    private var wantedImageSubtitle = false

    /// The app paints image subtitles over the video, which stays on the phone; an AirPlay receiver only gets the picture.
    /// So with one showing, the title moves to the server's stream, which burns the subtitle in.
    private func burnInForAirPlay() {
        guard isExternalPlayback, wantedImageSubtitle, isPlaying else { return }
        PocLog.write("airplay with an image subtitle, falling back to the server stream")
        wantedImageSubtitle = false
        reportFailure(.unsupported)
    }

    /// Turns the stats on or off. Switching on works out the values at once.
    func wantDiagnostics(_ wanted: Bool) {
        engine.wantDiagnostics(wanted)
    }

    // MARK: - Commands

    func play() {
        engine.play()
    }

    func pause() {
        engine.pause()
    }

    /// Plays at `speed` from now on; a pause and resume (or a seek that resumes) keeps it.
    func setSpeed(_ speed: Float) {
        engine.setSpeed(speed, applyNow: isPlaying)
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { play() }
    }

    /// Moves to `seconds`, or the nearest keyframe, which is much quicker than decoding up to the exact
    /// frame. `onSeeked` follows once the player got there.
    func seek(to seconds: TimeInterval) {
        let limit = duration > 0 ? duration : seconds
        let target = min(max(0, seconds), limit)
        position = target
        updateNowPlayingInfo()
        engine.seek(to: target) { [weak self] finished in
            guard finished, let self else { return }
            self.events.seeked(self.currentMilliseconds)
        }
    }

    // MARK: - Engine state

    private func externalPlaybackChanged(_ active: Bool) {
        isExternalPlayback = active
        refreshExternalDevice()
        watchAirPlayStart()
        burnInForAirPlay()
    }

    /// A stream the receiver can't take (a codec it lacks) leaves the picture stuck without an error the app is told about.
    /// If the player's own clock hasn't moved a while after AirPlay started (the reported position can pause while a stream is rebuilt), the player reports the stream as unsupported, which
    /// makes the shared model fall back to the server's stream.
    private func watchAirPlayStart() {
        airPlayWatch?.cancel()
        airPlayWatch = nil
        guard isExternalPlayback, isPlaying else { return }
        let startedAt = engine.currentSeconds ?? position
        let stream = openedStreamId
        let watch = DispatchWorkItem { [weak self] in
            guard let self, self.isExternalPlayback, self.isPlaying, self.openedStreamId == stream,
                  (self.engine.currentSeconds ?? self.position) - startedAt < 1 else { return }
            PocLog.write("airplay stalled for \(Int(Self.airPlayStallSeconds)) s, falling back to the server stream")
            self.reportFailure(.unsupported)
        }
        airPlayWatch = watch
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.airPlayStallSeconds, execute: watch)
    }

    private static let airPlayStallSeconds: TimeInterval = 12

    private func refreshExternalDevice() {
        let name = isExternalPlayback ? ExternalPlayback.deviceName(fromPortNames: ExternalPlayback.currentPortNames()) : nil
        let outputs = AVAudioSession.sharedInstance().currentRoute.outputs.map { "\($0.portType.rawValue)=\($0.portName)" }
        PocLog.write("airplay external=\(isExternalPlayback) name=\(name ?? "nil") outputs=\(outputs)")
        if name != externalDeviceName { externalDeviceName = name }
    }

    private func stateChanged(intendsToPlay: Bool, waiting: Bool) {
        isBuffering = waiting && openedStreamId != nil
        guard intendsToPlay != isPlaying else { return }
        isPlaying = intendsToPlay
        updateNowPlayingInfo()
        guard openedStreamId != nil else { return }
        if intendsToPlay {
            events.playing(currentMilliseconds)
        } else {
            events.paused(currentMilliseconds)
        }
    }

    // MARK: - Now playing

    private var nowPlayingTitle: String?
    private var nowPlayingSubtitle: String?
    private var nowPlayingArtworkUrl: String?
    private var nowPlayingArtwork: MPMediaItemArtwork?
    private var artworkTask: Task<Void, Never>?

    /// What the lock screen, Control Center and the picture-in-picture window call the video, and the picture beside it.
    func setNowPlaying(title: String?, subtitle: String?, artworkUrl: String?) {
        if artworkUrl != nowPlayingArtworkUrl { loadArtwork(artworkUrl) }
        guard title != nowPlayingTitle || subtitle != nowPlayingSubtitle else { return }
        nowPlayingTitle = title
        nowPlayingSubtitle = subtitle
        updateNowPlayingInfo()
    }

    /// Fetches the picture for the card. A failed load leaves the card without one.
    private func loadArtwork(_ urlString: String?) {
        artworkTask?.cancel()
        nowPlayingArtworkUrl = urlString
        nowPlayingArtwork = nil
        guard let urlString, let url = URL(string: urlString) else { return }
        artworkTask = Task { @MainActor [weak self] in
            guard let (data, _) = try? await URLSession.shared.data(from: url), !Task.isCancelled,
                  let image = UIImage(data: data), let self, self.nowPlayingArtworkUrl == urlString else { return }
            self.nowPlayingArtwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            self.updateNowPlayingInfo()
        }
    }

    private func updateNowPlayingInfo() {
        guard let title = nowPlayingTitle, !title.isEmpty, openedStreamId != nil else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.video.rawValue,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: Double(engine.speed),
            MPMediaItemPropertyTitle: title,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? Double(engine.speed) : 0,
        ]
        if let subtitle = nowPlayingSubtitle { info[MPMediaItemPropertyArtist] = subtitle }
        if duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = duration }
        if let nowPlayingArtwork { info[MPMediaItemPropertyArtwork] = nowPlayingArtwork }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func tick(position time: TimeInterval, buffered: TimeInterval) {
        guard openedStreamId != nil else { return }
        position = time
        if abs(buffered - bufferedPosition) >= 0.1 { bufferedPosition = buffered }
        // The shared model hears the position about once a second.
        let second = Int(time)
        if second != lastPushedSecond {
            lastPushedSecond = second
            events.position(currentMilliseconds)
            updateNowPlayingInfo()
        }
    }

    private func reportFailure(_ failure: PlaybackFailure) {
        guard !failureReported else { return }
        failureReported = true
        events.failed(failure)
    }

    private func reportEnded() {
        guard !endReported else { return }
        endReported = true
        events.ended(currentMilliseconds)
    }
}
