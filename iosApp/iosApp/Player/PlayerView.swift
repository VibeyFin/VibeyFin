import Shared
import SwiftUI
import UIKit

/// Plays one item full screen: custom controls over the video, the shared model deciding what to
/// play and reporting to the server. Presented over the item's page.
struct PlayerView: View {
    @StateObject var model: PlaybackObservable
    @StateObject var controller = PlayerController(engine: PlaybackEngineFactory.make())
    @StateObject var pip = PlayerPictureInPicture()
    /// The volume and brightness a vertical swipe on the video changes.
    @StateObject var swipe = PlayerSwipe()
    /// The Control Center and lock screen buttons, held for as long as this player lives.
    @State var remote = PlayerRemoteCommands()

    /// Called when the session ended and the player has closed.
    let onExit: (HomeExit) -> Void

    @Environment(\.playerSession) var session
    @Environment(\.scenePhase) var scenePhase
    @Environment(\.accessibilityReduceMotion) var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) var voiceOver

    @State var controlsVisible = true
    @State var controlsFrame: ControlsFrame = ControlsFrame(safeArea: EdgeInsets(), size: .zero)
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State var tabletop: TabletopSplit?
    @State var book: BookSplit?
    /// The second under the finger while the scrubber is dragged.
    @State var scrubPosition: TimeInterval?
    @State var hideTask: Task<Void, Never>?
    /// A track or speed menu is showing: the controls stay up, and the quiet moment starts when it closes.
    @State var menuOpen = false
    @State var searchingSubtitles = false
    /// The video was playing when "Are you still watching?" paused it.
    @State var pausedForPrompt = false
    /// The skip seek handed to the player last, until the model's state has cleared it.
    @State var lastSkipSeekMs: Int64?
    /// The scrub-preview tiles of the playing item; a new item gets its own, which drops the old cache.
    @State var trickplayTiles: TrickplayTiles?
    /// Going to the background, held back while automatic picture-in-picture may still announce itself.
    @State var pendingBackground: Task<Void, Never>?

    /// How long the controls stay after the last touch while playing.
    let hideDelay: UInt64 = 3_000_000_000
    /// How long the background waits for automatic picture-in-picture to start before it pauses.
    let pictureInPictureGrace: UInt64 = 500_000_000

    /// Resumes where the server says the user stopped, unless `startOver`.
    init(itemId: String, startOver: Bool = false, onExit: @escaping (HomeExit) -> Void) {
        _model = StateObject(wrappedValue: PlaybackObservable(itemId: itemId, startOver: startOver))
        self.onExit = onExit
    }

    var state: PlaybackUiState? { model.state }
    var hasStream: Bool { state?.stream != nil }
    /// The profile wants the swipe, and the picture is on this screen: not in a picture-in-picture window, not over AirPlay.
    var swipeEnabled: Bool { state?.swipeGestures != false && !pip.isActive && !controller.isExternalPlayback }
    var isWaiting: Bool { state?.message == nil && (state?.loading ?? true || controller.isBuffering) }
    /// Skips in a row are showing: every single tap on a side adds one.
    var isSkipping: Bool { state?.skipRun != nil }
    /// A run begun by double taps on the video: only the total and the scrubber are on screen. One begun on the skip
    /// buttons keeps the controls up, and so does VoiceOver.
    var isSeeking: Bool { state?.skipRun?.fromControls == false && !voiceOver }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            Group {
                PlayerSurface(controller: controller, pip: pip).ignoresSafeArea()
                SubtitleOverlay(
                    source: controller.isExternalPlayback ? nil : state?.subtitleSource,
                    onUnauthorized: { controller.subtitleRejected() }
                ) { controller.engine.currentSeconds }
                .bookSubtitles(book)
            }
            .tabletopVideo(split: tabletop)
            // The picture is on the other screen over AirPlay: the item's artwork, blurred, stands in for it.
            if controller.isExternalPlayback {
                ExternalPlaybackBackdrop(artworkUrl: state?.artworkUrl)
                    .transition(.opacity)
            }
            // The double tap comes first so a single tap isn't taken for half of it.
            GeometryReader { proxy in
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        SpatialTapGesture(count: 1).onEnded { tapToSkip(at: $0.location.x, width: proxy.size.width) },
                        including: isSkipping ? .all : .none
                    )
                    .gesture(
                        SpatialTapGesture(count: 2)
                            .onEnded { tapToSkip(at: $0.location.x, width: proxy.size.width) }
                            .exclusively(before: TapGesture().onEnded { toggleControls() }),
                        including: isSkipping ? .none : .all
                    )
                    .swipeForLevels(swipe, enabled: swipeEnabled, size: proxy.size) {
                        model.userInteraction()
                        if controlsVisible { scheduleHide() }
                    }
            }
            .ignoresSafeArea()
            .allowsHitTesting(tabletop == nil)
            .accessibilityHidden(true)
            ZStack {
                if state?.message == nil, let run = state?.skipRun {
                    let side: SeekSide = run.forward ? .forward : .back
                    let total = Strings.shared.seekTotal(forward: run.forward, seconds: run.seconds)
                    if tabletop != nil || !isSeeking {
                        // The controls stay (tabletop, a run from the skip buttons, VoiceOver), so only the total shows.
                        SeekingOverlay(side: side, text: total, taps: Int(run.taps)) { EmptyView() }
                            .tabletopVideo(split: tabletop)
                    } else {
                        SeekingOverlay(side: side, text: total, taps: Int(run.taps)) { seekingBar }
                    }
                }
            }
            .animation(.easeOut(duration: 0.15), value: isSeeking)
            if let message = state?.message {
                errorPanel(message)
                closeOverlay
            } else {
                if isWaiting {
                    ProgressView().controlSize(.large).tabletopVideo(split: tabletop)
                }
                if let tabletop {
                    tabletopPanel
                        .tabletopControls(split: tabletop)
                } else if (controlsVisible || controller.isExternalPlayback) && !isSeeking {
                    controls
                        .transition(.opacity)
                }
                // Offered for the whole segment, controls up or not; above the scrubber while that shows.
                if tabletop == nil, !isSeeking, let offer = state?.skipOffer {
                    SkipOfferOverlay(label: offer.label, raised: controlsVisible) { model.skip() }
                }
                // Stays up when the controls hide; an error fills the same part of the screen.
                if showsStats, let diagnostics = state?.diagnostics {
                    StatsPanel(diagnostics: diagnostics)
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
            if let upNext = state?.upNext, state?.message == nil, !isSeeking {
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
        // Over everything else on the video, so the up next card or the skip button never covers it.
        .overlay {
            if swipeEnabled {
                ZStack {
                    PlayerVolumeHost(swipe: swipe).frame(width: 0, height: 0)
                    SwipeLevelIndicator(swipe: swipe)
                }
            }
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.26), value: state?.upNext != nil)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: controller.isExternalPlayback)
        .tint(Brand.onMedia)
        .foldReader(tabletop: $tabletop, book: $book)
        .statusBarHidden(!(controlsVisible || tabletop != nil))
        .persistentSystemOverlays(controlsVisible || tabletop != nil ? .automatic : .hidden)
        .overlay(alignment: .top) {
            if let notice = model.state?.notice { PlayerNotice(text: notice) }
        }
        .subtitleSearch(
            itemId: model.state?.subtitleSearchItemId,
            isPresented: $searchingSubtitles,
            onFailedWhileClosed: { model.notice($0) },
            onAdded: { model.subtitleAdded($0) }
        )
        .onChange(of: searchingSubtitles) { _, open in menuOpenChanged(open) }
        .onAppear {
            bindPlayer()
            bindPictureInPicture()
            model.start()
            controller.activateAudioSession()
            OrientationLock.lockLandscape()
            installRemoteCommands()
            session.stopCurrent = {
                pip.stop()
                controller.pause()
                model.closed(controller.currentMilliseconds)
            }
        }
        // Out of sight while picture-in-picture plays: the page behind is upright, and the player is back in landscape when it returns.
        .onChange(of: session.minimised) { _, minimised in
            if minimised { OrientationLock.restore() } else { OrientationLock.lockLandscape() }
        }
        .onDisappear {
            hideTask?.cancel()
            pendingBackground?.cancel()
            // The window plays on without this screen being visible; it is torn down when the window closes.
            remote.remove()
            if !pip.isActive { controller.tearDown() }
            OrientationLock.restore()
            swipe.restoreBrightness()
        }
        .onReceive(model.$state) { stateChanged($0) }
        // Over AirPlay the controls are the whole screen, so they stay up until it stops.
        .onChange(of: controller.isExternalPlayback) { _, active in
            if active {
                hideTask?.cancel()
                setControlsVisible(true)
            } else {
                scheduleHide()
            }
        }
        .onChange(of: controller.isPlaying) { _, isPlaying in
            if isPlaying {
                scheduleHide()
            } else {
                // Paused: the controls stay up.
                hideTask?.cancel()
                showControls()
            }
        }
        .onChange(of: scenePhase) { _, phase in scenePhaseChanged(phase) }
        .onChange(of: model.state?.finished == true) { _, finished in
            if finished {
                pip.stop()
                session.end()
            }
        }
        .onChange(of: model.state?.exit != nil) { _, hasExit in
            if hasExit, let exit = model.state?.exit {
                pip.stop()
                session.end()
                onExit(exit)
            }
        }
    }
}
