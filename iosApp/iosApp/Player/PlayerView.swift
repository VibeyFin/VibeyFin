import Shared
import SwiftUI
import UIKit

/// Plays one item full screen: custom controls over the video, the shared model deciding what to
/// play and reporting to the server. Presented over the item's page.
struct PlayerView: View {
    @StateObject private var model: PlaybackObservable
    @StateObject private var controller = PlayerController()
    @StateObject private var pip = PlayerPictureInPicture()

    /// Called when the session ended and the player has closed.
    let onExit: (HomeExit) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var controlsVisible = true
    /// The second under the finger while the scrubber is dragged.
    @State private var scrubPosition: TimeInterval?
    @State private var hideTask: Task<Void, Never>?
    /// The video was playing when "Are you still watching?" paused it.
    @State private var pausedForPrompt = false
    /// The skip seek handed to the player last, until the model's state has cleared it.
    @State private var lastSkipSeekMs: Int64?
    /// The scrub-preview tiles of the playing item; a new item gets its own, which drops the old cache.
    @State private var trickplayTiles: TrickplayTiles?
    /// Going to the background, held back while automatic picture-in-picture may still announce itself.
    @State private var pendingBackground: Task<Void, Never>?
    /// The side of the last double-tap seek, while its "10s" shows.
    @State private var seekFlash: SeekSide?
    @State private var flashTask: Task<Void, Never>?

    /// How long the controls stay after the last touch while playing.
    private let hideDelay: UInt64 = 3_000_000_000
    /// How long the background waits for automatic picture-in-picture to start before it pauses.
    private let pictureInPictureGrace: UInt64 = 500_000_000

    /// Resumes where the server says the user stopped, unless `startOver`.
    init(itemId: String, startOver: Bool = false, onExit: @escaping (HomeExit) -> Void) {
        _model = StateObject(wrappedValue: PlaybackObservable(itemId: itemId, startOver: startOver))
        self.onExit = onExit
    }

    private var state: PlaybackUiState? { model.state }
    private var hasStream: Bool { state?.stream != nil }
    private var isWaiting: Bool { state?.message == nil && (state?.loading ?? true || controller.isBuffering) }
    private var shownPosition: TimeInterval { scrubPosition ?? controller.position }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            PlayerSurface(player: controller.player, onLayer: { pip.attach(to: $0) }).ignoresSafeArea()
            // A touch anywhere on the video shows or hides the controls; a double tap on the left or
            // right third seeks. The double tap comes first so a single tap isn't taken for half of it.
            GeometryReader { proxy in
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        SpatialTapGesture(count: 2)
                            .onEnded { doubleTap(at: $0.location.x, width: proxy.size.width) }
                            .exclusively(before: TapGesture().onEnded { toggleControls() })
                    )
            }
            .ignoresSafeArea()
            .accessibilityHidden(true)
            if let side = seekFlash, let seconds = state.map({ side == .back ? $0.skipBackSeconds : $0.skipForwardSeconds }) {
                seekFlashView(side: side, seconds: Int(seconds))
            }
            if let message = state?.message {
                errorPanel(message)
                closeOverlay
            } else {
                if isWaiting {
                    ProgressView().controlSize(.large)
                }
                if controlsVisible {
                    controls
                        .transition(.opacity)
                }
                // Offered for the whole segment, controls up or not; above the scrubber while that shows.
                if let offer = state?.skipOffer {
                    skipButton(offer)
                }
                // Stays up when the controls hide; an error fills the same part of the screen.
                if state?.showDiagnostics == true, let diagnostics = state?.diagnostics {
                    statsOverlay(diagnostics)
                }
            }
            if state?.stillWatching == true {
                StillWatchingPrompt(
                    onContinue: { model.continueWatching() },
                    onStop: { model.stopWatching() }
                )
                .transition(.opacity)
            }
            // The next episode, in the last seconds of this one, whether or not the controls are up.
            if let upNext = state?.upNext, state?.message == nil {
                UpNextCard(
                    upNext: upNext,
                    onPlayNow: { model.playNow() },
                    onWatchCredits: { model.watchCredits() }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .screenMargin()
                .padding(.bottom, Space.s7 + Space.s5)
                // Grows in and fades like a screen opening, and goes the same way (none of it under Reduce Motion).
                .transition(reduceMotion ? .opacity : .scale(scale: 0.92).combined(with: .opacity))
            }
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.26), value: state?.upNext != nil)
        // The app tints aqua; in the player everything that takes a tint (the spinner, glass buttons,
        // menus) is white. Aqua stays on the scrubber's fill, which sets its own colour.
        .tint(.white)
        .statusBarHidden(!controlsVisible)
        .persistentSystemOverlays(controlsVisible ? .automatic : .hidden)
        .onAppear {
            bindPlayer()
            bindPictureInPicture()
            model.start()
            controller.activateAudioSession()
            OrientationLock.lockLandscape()
        }
        .onDisappear {
            hideTask?.cancel()
            flashTask?.cancel()
            pendingBackground?.cancel()
            // The window plays on without this screen being visible; it is torn down when the window closes.
            if !pip.isActive { controller.tearDown() }
            OrientationLock.restore()
        }
        .onReceive(model.$state) { state in
            if let stream = state?.stream {
                controller.open(stream, bufferAheadSeconds: state?.bufferAheadSeconds ?? 0)
            } else if state?.message != nil {
                controller.clear()
            }
            // A skip: the shared model asks for a seek. It goes through the same path as the scrubber, so
            // the player's seek callback reports it to the server.
            // Each request is acted on once: a state that arrives before the model has cleared it carries
            // the same value again, which is skipped.
            if let seekTo = state?.pendingSeekMs?.int64Value {
                if seekTo != lastSkipSeekMs {
                    lastSkipSeekMs = seekTo
                    controller.seek(to: TimeInterval(seekTo) / 1000)
                    model.seekHandled()
                }
            } else {
                lastSkipSeekMs = nil
            }
            updateTrickplay(state)
            controller.wantAudio(audioChoice(in: state))
            controller.wantDiagnostics(state?.showDiagnostics == true)
            controller.setSpeed(state?.speed ?? 1)
            applyStillWatching(state?.stillWatching == true)
            controller.setNowPlaying(title: state?.title, subtitle: state?.subtitle)
        }
        .onChange(of: controller.isPlaying) { isPlaying in
            if isPlaying {
                scheduleHide()
            } else {
                // Paused: the controls stay up.
                hideTask?.cancel()
                showControls()
            }
        }
        .onChange(of: scenePhase) { phase in
            // The home screen or the lock button: the video stops and the server is told. Only the
            // background counts; a pulled-down notification centre is `.inactive` and playback goes on.
            if phase == .background && !pip.isActive {
                if pip.mayStartAutomatically(whilePlaying: controller.isPlaying) {
                    // Automatic picture-in-picture may announce itself just before or just after the
                    // background (the order isn't documented), so the pause waits for it a moment.
                    pendingBackground?.cancel()
                    pendingBackground = Task { @MainActor in
                        try? await Task.sleep(nanoseconds: pictureInPictureGrace)
                        guard !Task.isCancelled else { return }
                        pendingBackground = nil
                        if !pip.isActive && UIApplication.shared.applicationState == .background { leaveForBackground() }
                    }
                } else {
                    leaveForBackground()
                }
            } else if phase == .active {
                pendingBackground?.cancel()
                pendingBackground = nil
                showControls()
            }
        }
        .onChange(of: model.state?.finished == true) { finished in
            if finished {
                pip.stop()
                dismiss()
            }
        }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit {
                pip.stop()
                dismiss()
                onExit(exit)
            }
        }
    }

    // MARK: - Controls

    private var controls: some View {
        ZStack {
            scrims
            PlayerGlassGroup {
                ZStack {
                    VStack(spacing: Space.s3) {
                        topBar
                        Spacer(minLength: 0)
                        bottomBar
                    }
                    if hasStream && !isWaiting {
                        PlayerTransport(
                            isPlaying: controller.isPlaying,
                            skipBackSeconds: Int(state?.skipBackSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_BACK_SECONDS),
                            skipForwardSeconds: Int(state?.skipForwardSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_FORWARD_SECONDS),
                            onSkipBack: { skip(.back) },
                            onTogglePlay: {
                                model.userInteraction()
                                controller.togglePlayPause()
                            },
                            onSkipForward: { skip(.forward) }
                        )
                    }
                }
                .screenMargin()
                .padding(.vertical, Space.s3)
            }
        }
    }

    /// Everything over the video sits on the scrim.
    private var scrims: some View {
        VStack(spacing: 0) {
            LinearGradient(colors: [Brand.scrim, .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: 120)
            Spacer(minLength: 0)
            LinearGradient(colors: [.clear, Brand.scrim], startPoint: .top, endPoint: .bottom)
                .frame(height: 160)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    private var topBar: some View {
        HStack(alignment: .top, spacing: Space.s3) {
            PlayerIconButton(systemName: "xmark", label: Strings.shared.close, action: close)
            VStack(alignment: .leading, spacing: Space.s1) {
                if let title = state?.title, !title.isEmpty {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
                if !metaLine.isEmpty {
                    Text(metaLine)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .padding(.top, Space.s1)
            Spacer(minLength: 0)
            PlayerControlCluster {
                if let state {
                    PlayerSpeedMenu(
                        speeds: state.speeds.map { $0.floatValue },
                        selected: state.speed,
                        onSelect: { speed in
                            model.selectSpeed(speed)
                            scheduleHide()
                        },
                        onTouch: {
                            model.userInteraction()
                            hideTask?.cancel()
                        }
                    )
                }
                if let state, state.hasTrackChoices {
                    PlayerTrackMenu(
                        audio: state.audioTracks,
                        subtitles: state.subtitleTracks,
                        onSelectAudio: { index in
                            model.selectAudio(index)
                            scheduleHide()
                        },
                        onSelectSubtitle: { index in
                            model.selectSubtitle(index)
                            scheduleHide()
                        },
                        onTouch: {
                            model.userInteraction()
                            hideTask?.cancel()
                        }
                    )
                }
                if pip.isSupported {
                    PlayerIconButton(
                        systemName: "pip.enter",
                        label: Strings.shared.pictureInPicture,
                        action: {
                            model.userInteraction()
                            pip.start()
                        }
                    )
                }
                AirPlayButton()
            }
        }
    }

    /// The subtitle then the picture badge, as plain text.
    private var metaLine: String {
        Strings.shared.metaLine(subtitle: state?.subtitle, badge: state?.badge) ?? ""
    }

    /// A new item's trickplay gets new tiles, so the previous episode's thumbnails never show; none clears them.
    private func updateTrickplay(_ state: PlaybackUiState?) {
        guard let track = state?.trickplay, !track.tilesUrl.isEmpty else {
            if trickplayTiles != nil { trickplayTiles = nil }
            return
        }
        guard let headers = state?.stream?.headers, trickplayTiles?.isFor(track) != true else { return }
        trickplayTiles = TrickplayTiles(track: track, headers: headers)
    }

    private var bottomBar: some View {
        VStack(spacing: 0) {
            PlayerScrubber(
                position: shownPosition,
                duration: controller.duration,
                bufferedPosition: controller.bufferedPosition,
                onScrub: { seconds in
                    model.userInteraction()
                    scrubPosition = seconds
                    hideTask?.cancel()
                },
                onCommit: { seconds in
                    scrubPosition = nil
                    controller.seek(to: seconds)
                    scheduleHide()
                },
                trickplay: trickplayTiles
            )
            HStack {
                Text(Strings.shared.clock(milliseconds: PlayerTime.milliseconds(shownPosition)))
                Spacer()
                if controller.duration > 0 {
                    Text(Strings.shared.timeLeft(
                        positionMs: PlayerTime.milliseconds(shownPosition),
                        durationMs: PlayerTime.milliseconds(controller.duration)
                    ))
                }
            }
            .font(.footnote)
            .monospacedDigit()
            .foregroundStyle(.secondary)
        }
    }

    /// Top leading, under the close button and title row.
    private func statsOverlay(_ diagnostics: PlaybackDiagnostics) -> some View {
        VStack {
            HStack {
                StatsOverlay(diagnostics: diagnostics)
                Spacer(minLength: 0)
            }
            Spacer(minLength: 0)
        }
        .screenMargin()
        .padding(.top, Space.s7 + Space.s4)
        .allowsHitTesting(false)
    }

    /// Bottom trailing; rises above the scrubber and the times while the controls show.
    private func skipButton(_ offer: SkipOffer) -> some View {
        VStack {
            Spacer(minLength: 0)
            HStack {
                Spacer(minLength: 0)
                SkipButton(label: offer.label) { model.skip() }
            }
        }
        .screenMargin()
        .padding(.bottom, controlsVisible ? Space.s7 + Space.s6 : Space.s4)
    }

    // MARK: - Errors

    /// A failed load or a video that wouldn't play: what happened and Try again. The close button is on top.
    private func errorPanel(_ message: String) -> some View {
        LoadErrorView(message: message, onRetry: { model.retry() })
            .frame(maxWidth: 420)
            .padding(Space.s5)
    }

    private var closeOverlay: some View {
        VStack {
            HStack {
                PlayerGlassGroup {
                    PlayerIconButton(systemName: "xmark", label: Strings.shared.close, action: close)
                }
                Spacer()
            }
            Spacer()
        }
        .screenMargin()
        .padding(.vertical, Space.s3)
    }

    // MARK: - Behaviour

    /// Gives the shared model what the player does. Done on appear, after both objects exist.
    private func bindPlayer() {
        controller.events = PlayerEvents(
            playing: { model.playing($0) },
            paused: { model.paused($0) },
            seeked: { model.seeked($0) },
            position: { model.position($0) },
            ended: { model.ended($0) },
            failed: { model.failed($0) },
            diagnostics: { model.diagnostics($0) }
        )
    }

    /// "Are you still watching?" pauses the video; "Keep watching" plays on. A pause the viewer made is left alone.
    private func applyStillWatching(_ asking: Bool) {
        guard asking != pausedForPrompt else { return }
        if asking {
            pausedForPrompt = controller.isPlaying
            controller.pause()
        } else if pausedForPrompt {
            pausedForPrompt = false
            controller.play()
        }
    }

    /// Tells the shared model when the picture-in-picture window starts and how it ends. Dismissing the
    /// window ends the session, so the screen goes with it; expanding it leaves everything as it is.
    private func bindPictureInPicture() {
        pip.entered = { model.enteredPictureInPicture(controller.currentMilliseconds) }
        pip.left = { closed in
            model.leftPictureInPicture(controller.currentMilliseconds, closed: closed)
            if closed { dismiss() }
        }
        // The window never came up: the model must stop treating the player as in picture-in-picture,
        // and if the app already went to the background meanwhile, that is handled now as it would have been.
        pip.failedToStart = {
            model.leftPictureInPicture(controller.currentMilliseconds, closed: false)
            if UIApplication.shared.applicationState == .background { leaveForBackground() }
        }
    }

    /// The app went to the background without a picture-in-picture window: the video stops and the server is told.
    private func leaveForBackground() {
        controller.pause()
        model.backgrounded(controller.currentMilliseconds)
    }

    /// What the player has to choose itself: the selected audio track of a direct play. The server
    /// chooses the audio of every other stream, and the player then leaves it alone.
    private func audioChoice(in state: PlaybackUiState?) -> AudioChoice? {
        guard let state, state.stream?.audioInPlayer == true,
              let position = state.audioTracks.firstIndex(where: { $0.selected }) else { return nil }
        return AudioChoice(position: position, count: state.audioTracks.count)
    }

    private func close() {
        controller.pause()
        model.closed(controller.currentMilliseconds)
        dismiss()
    }

    /// Skips by the profile's seconds: the model answers with a seek that `onReceive` hands to the player.
    private func skip(_ side: SeekSide) {
        model.userInteraction()
        let position = PlayerTime.milliseconds(controller.position)
        switch side {
        case .back:
            model.skipBack(position)
        case .forward:
            model.skipForward(position, durationMs: PlayerTime.milliseconds(controller.duration))
        }
        scheduleHide()
    }

    /// The left third skips back, the right third forward, the middle does nothing.
    private func doubleTap(at x: CGFloat, width: CGFloat) {
        guard hasStream, !isWaiting, state?.message == nil, width > 0 else { return }
        let side: SeekSide
        if x < width / 3 {
            side = .back
        } else if x > width * 2 / 3 {
            side = .forward
        } else {
            return
        }
        skip(side)
        withAnimation(.easeOut(duration: 0.12)) { seekFlash = side }
        flashTask?.cancel()
        flashTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 600_000_000)
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.2)) { seekFlash = nil }
        }
    }

    /// Near the tapped side; fades in and out, and under Reduce Motion does nothing else.
    private func seekFlashView(side: SeekSide, seconds: Int) -> some View {
        HStack {
            if side == .forward { Spacer(minLength: 0) }
            SeekFlash(text: Strings.shared.seekFlash(seconds: Int32(seconds)))
                .padding(.horizontal, Space.s7)
            if side == .back { Spacer(minLength: 0) }
        }
        .allowsHitTesting(false)
        .transition(.opacity)
    }

    private func toggleControls() {
        model.userInteraction()
        if controlsVisible {
            hideTask?.cancel()
            setControlsVisible(false)
        } else {
            showControls()
        }
    }

    private func showControls() {
        setControlsVisible(true)
        scheduleHide()
    }

    /// Hides the controls after a quiet moment, but only while playing, never mid-drag and not for
    /// someone using VoiceOver, who would lose them.
    private func scheduleHide() {
        hideTask?.cancel()
        guard controller.isPlaying, scrubPosition == nil, state?.message == nil,
              !UIAccessibility.isVoiceOverRunning else { return }
        hideTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: hideDelay)
            guard !Task.isCancelled else { return }
            setControlsVisible(false)
        }
    }

    private func setControlsVisible(_ visible: Bool) {
        withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) {
            controlsVisible = visible
        }
    }
}

enum PlayerTime {
    /// Seconds from the player as the milliseconds the shared model counts in.
    static func milliseconds(_ seconds: TimeInterval) -> Int64 {
        Int64((seconds.isFinite ? max(0, seconds) : 0) * 1000)
    }
}

enum SeekSide {
    case back
    case forward
}
