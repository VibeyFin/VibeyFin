import Shared
import SwiftUI
import UIKit

/// Plays one item full screen: custom controls over the video, the shared model deciding what to
/// play and reporting to the server. Presented over the item's page.
struct PlayerView: View {
    @StateObject private var model: PlaybackObservable
    @StateObject private var controller = PlayerController()

    /// Called when the session ended and the player has closed.
    let onExit: (HomeExit) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var controlsVisible = true
    /// The second under the finger while the scrubber is dragged.
    @State private var scrubPosition: TimeInterval?
    @State private var hideTask: Task<Void, Never>?

    /// How long the controls stay after the last touch while playing.
    private let hideDelay: UInt64 = 3_000_000_000

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
            PlayerSurface(player: controller.player).ignoresSafeArea()
            // A touch anywhere on the video shows or hides the controls.
            Color.clear
                .contentShape(Rectangle())
                .ignoresSafeArea()
                .onTapGesture(perform: toggleControls)
                .accessibilityHidden(true)
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
                // Stays up when the controls hide; an error fills the same part of the screen.
                if state?.showDiagnostics == true, let diagnostics = state?.diagnostics {
                    statsOverlay(diagnostics)
                }
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
        .statusBarHidden(!controlsVisible)
        .persistentSystemOverlays(controlsVisible ? .automatic : .hidden)
        .onAppear {
            bindPlayer()
            model.start()
            controller.activateAudioSession()
            OrientationLock.lockLandscape()
        }
        .onDisappear {
            hideTask?.cancel()
            controller.tearDown()
            OrientationLock.restore()
        }
        .onReceive(model.$state) { state in
            if let stream = state?.stream {
                controller.open(stream)
            } else if state?.message != nil {
                controller.clear()
            }
            controller.wantAudio(audioChoice(in: state))
            controller.wantDiagnostics(state?.showDiagnostics == true)
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
            if phase == .background {
                controller.pause()
                model.backgrounded(controller.currentMilliseconds)
            } else if phase == .active {
                showControls()
            }
        }
        .onChange(of: model.state?.finished == true) { finished in
            if finished { dismiss() }
        }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit {
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
                        PlayerIconButton(
                            systemName: controller.isPlaying ? "pause.fill" : "play.fill",
                            label: controller.isPlaying ? Strings.shared.pause : Strings.shared.play,
                            font: .largeTitle,
                            action: { controller.togglePlayPause() }
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
                if let subtitle = state?.subtitle {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .padding(.top, Space.s1)
            Spacer(minLength: 0)
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
                    onTouch: { hideTask?.cancel() }
                )
            }
        }
    }

    private var bottomBar: some View {
        VStack(spacing: 0) {
            PlayerScrubber(
                position: shownPosition,
                duration: controller.duration,
                onScrub: { seconds in
                    scrubPosition = seconds
                    hideTask?.cancel()
                },
                onCommit: { seconds in
                    scrubPosition = nil
                    controller.seek(to: seconds)
                    scheduleHide()
                }
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

    private func toggleControls() {
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
