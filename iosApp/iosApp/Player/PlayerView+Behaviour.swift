import Shared
import SwiftUI
import UIKit

extension PlayerView {
    /// Hands the shared model's new state to the player, the remote commands and the picture-in-picture window.
    func stateChanged(_ state: PlaybackUiState?) {
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
        controller.wantAudio(state?.audioChoice)
        controller.wantSubtitle(state?.subtitleChoice)
        controller.wantDiagnostics(showsStats)
        controller.setSpeed(state?.speed ?? 1)
        applyStillWatching(state?.stillWatching == true)
        controller.setNowPlaying(title: state?.title, subtitle: state?.subtitle, artworkUrl: state?.artworkUrl)
        remote.setSkipIntervals(
            back: Int(state?.skipBackSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_BACK_SECONDS),
            forward: Int(state?.skipForwardSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_FORWARD_SECONDS)
        )
        pip.allow(state?.pictureInPicture != false)
        applyScreenRest(state)
    }

    /// Both engines hold the screen on only while video plays, so the app holds it while paused too; not while minimised or over AirPlay.
    func applyScreenRest(_ state: PlaybackUiState?) {
        let onThisScreen = !session.minimised && !controller.isExternalPlayback
        UIApplication.shared.isIdleTimerDisabled = onThisScreen && state?.keepsScreenOn != false
        if onThisScreen && state?.screenDimmed == true && controlsVisible {
            hideTask?.cancel()
            setControlsVisible(false)
        }
    }

    /// A touch on the dimmed player: the screen wakes with the controls up, and the wait starts again.
    func wakeScreen() {
        model.userInteraction()
        showControls()
    }

    /// What leaving or coming back to the app does to the video, the orientation and the controls.
    func scenePhaseChanged(_ phase: ScenePhase) {
        // The brightness is the whole screen's on iOS, so other apps and the home screen get theirs back.
        if phase != .active { swipe.restoreBrightness() }
        // A phone held upright goes to portrait before the app leaves, so the window starts there and not after a turn.
        // Only when the window will open by itself and the phone is upright: a pulled-down notification centre is `.inactive` too.
        if phase == .inactive && !pip.isActive && UIDevice.current.orientation == .portrait
            && pip.mayStartAutomatically(whilePlaying: controller.isPlaying) {
            OrientationLock.restore()
        }
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
            // The window did not come up after the early turn to portrait below.
            if !session.minimised { OrientationLock.lockLandscape() }
            // A picker that closed while the app was away may never have said so.
            menuOpen = false
            model.screenShown()
            showControls()
        }
    }

    /// A new item's trickplay gets new tiles, so the previous episode's thumbnails never show; none clears them.
    /// The tiles are fetched from the moment they exist, and again around the position after each seek.
    func updateTrickplay(_ state: PlaybackUiState?) {
        guard let track = state?.trickplay, !track.tilesUrl.isEmpty else {
            trickplayTiles?.close()
            if trickplayTiles != nil { trickplayTiles = nil }
            return
        }
        let limited = state?.dataLimited == true
        if let tiles = trickplayTiles, tiles.isFor(track) {
            tiles.setLimited(limited)
            return
        }
        guard let headers = state?.stream?.headers else { return }
        trickplayTiles?.close()
        let tiles = TrickplayTiles(track: track, headers: headers, limited: limited)
        tiles.aim(atMs: controller.currentMilliseconds)
        trickplayTiles = tiles
    }

    /// Gives the shared model what the player does. Done on appear, after both objects exist.
    func bindPlayer() {
        controller.events = PlayerEvents(
            playing: { model.playing($0) },
            paused: { model.paused($0) },
            seeked: {
                model.seeked($0)
                trickplayTiles?.aim(atMs: $0)
            },
            position: {
                model.position($0)
                trickplayTiles?.aim(atMs: $0)
            },
            ended: { model.ended($0) },
            failed: { model.failed($0) },
            diagnostics: { model.diagnostics($0) }
        )
    }

    /// "Are you still watching?" pauses the video; "Keep watching" plays on. A pause the viewer made is left alone.
    func applyStillWatching(_ asking: Bool) {
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
    func bindPictureInPicture() {
        pip.entered = {
            model.enteredPictureInPicture(controller.currentMilliseconds)
            // The video is in the window now: the player steps aside so the page it was opened from shows, not an empty screen.
            session.minimise()
        }
        pip.restoreRequested = {
            let turns = OrientationLock.lockLandscape()
            session.expand()
            return turns
        }
        pip.left = { closed in
            model.leftPictureInPicture(controller.currentMilliseconds, closed: closed)
            if closed { session.end() } else { session.expand() }
        }
        // The window never came up: the model must stop treating the player as in picture-in-picture,
        // and if the app already went to the background meanwhile, that is handled now as it would have been.
        pip.failedToStart = {
            model.leftPictureInPicture(controller.currentMilliseconds, closed: false)
            session.expand()
            if UIApplication.shared.applicationState == .background { leaveForBackground() }
        }
    }

    /// The app went to the background without a picture-in-picture window: the video stops and the server is told.
    func leaveForBackground() {
        controller.pause()
        model.backgrounded(controller.currentMilliseconds)
    }

    func close() {
        controller.pause()
        model.closed(controller.currentMilliseconds)
        session.end()
    }

    /// The card's buttons act on this player the way its own controls do; a seek goes through the same path, so the server hears it.
    func installRemoteCommands() {
        remote.install(PlayerRemoteCommands.Handlers(
            play: { controller.play() },
            pause: { controller.pause() },
            togglePlayPause: {
                model.userInteraction()
                controller.togglePlayPause()
            },
            skip: { side in
                let position = PlayerTime.milliseconds(controller.position)
                switch side {
                case .back: model.skipBack(position)
                case .forward: model.skipForward(position, durationMs: PlayerTime.milliseconds(controller.duration))
                }
            },
            seek: { controller.seek(to: $0) }
        ))
    }

    /// Skips by the profile's seconds: the model answers with a seek that `onReceive` hands to the player.
    /// [fromControls] is false for taps on the video, which hide the controls while the total shows.
    func skip(_ side: SeekSide, fromControls: Bool = true) {
        model.userInteraction()
        let position = PlayerTime.milliseconds(controller.position)
        switch side {
        case .back:
            model.skipBack(position, fromControls: fromControls)
        case .forward:
            model.skipForward(position, durationMs: PlayerTime.milliseconds(controller.duration), fromControls: fromControls)
        }
        scheduleHide()
    }

    /// The left third skips back, the right third forward, the middle does nothing.
    func tapToSkip(at x: CGFloat, width: CGFloat) {
        guard hasStream, !isWaiting, state?.message == nil, let side = SeekSide.at(x: x, width: width) else { return }
        skip(side, fromControls: false)
    }

    func toggleControls() {
        model.userInteraction()
        // Over AirPlay the controls stay up.
        guard !controller.isExternalPlayback else { return }
        // A touch on the video means no menu or picker is open, whatever it last reported.
        menuOpen = false
        if controlsVisible {
            hideTask?.cancel()
            setControlsVisible(false)
        } else {
            showControls()
        }
    }

    func showControls() {
        setControlsVisible(true)
        scheduleHide()
    }

    /// Hides the controls after a quiet moment, but only while playing, never mid-drag and not for
    /// someone using VoiceOver, who would lose them.
    func scheduleHide() {
        hideTask?.cancel()
        guard controller.isPlaying, scrubPosition == nil, !menuOpen, state?.message == nil,
              !controller.isExternalPlayback, !UIAccessibility.isVoiceOverRunning else { return }
        hideTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: hideDelay)
            guard !Task.isCancelled else { return }
            setControlsVisible(false)
        }
    }

    func menuOpenChanged(_ open: Bool) {
        menuOpen = open
        model.userInteraction()
        if open {
            hideTask?.cancel()
        } else {
            scheduleHide()
        }
    }

    func setControlsVisible(_ visible: Bool) {
        withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) {
            controlsVisible = visible
        }
    }
}
