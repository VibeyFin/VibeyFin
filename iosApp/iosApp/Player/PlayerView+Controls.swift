import Shared
import SwiftUI

extension PlayerView {
    // MARK: - Controls

    var controls: some View {
        ZStack {
            PlayerScrims().bookControls(book)
            PlayerGlassGroup {
                ZStack {
                    VStack(spacing: Space.s3) {
                        topBar
                            .rowInsets(rowInsets(at: .top))
                        Spacer(minLength: 0)
                        bottomBar
                            .rowInsets(rowInsets(at: .bottom))
                    }
                    if hasStream && !isWaiting {
                        PlayerTransport(
                            isPlaying: controller.isPlaying,
                            episodeControls: state?.episodeControls == true,
                            previousEpisode: state?.previousEpisode,
                            nextEpisode: state?.nextEpisode,
                            skipBackSeconds: Int(state?.skipBackSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_BACK_SECONDS),
                            skipForwardSeconds: Int(state?.skipForwardSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_FORWARD_SECONDS),
                            onPreviousEpisode: {
                                model.userInteraction()
                                model.playPrevious()
                            },
                            onTogglePlay: {
                                model.userInteraction()
                                controller.togglePlayPause()
                            },
                            onNextEpisode: {
                                model.userInteraction()
                                model.playNow()
                            },
                            onSkipBack: { skip(.back) },
                            onSkipForward: { skip(.forward) }
                        )
                    }
                    if controller.isExternalPlayback {
                        ExternalPlaybackStatus(text: Strings.shared.playingOn(device: controller.externalDeviceName))
                    }
                }
                .rowInsets(controlsBalance)
                .screenMargin()
                .bookControls(book)
                .padding(.vertical, Space.s3)
                .controlsSpanWidth(spans: spansWidth)
            }
        }
        .background { Color.clear.onGeometryChange(for: ControlsFrame.self) { ControlsFrame(safeArea: $0.safeAreaInsets, size: $0.size) } action: { controlsFrame = $0 } }
    }

    var tabletopPanel: some View {
        PlayerTabletopControls(
            title: state?.title ?? "",
            subtitle: state?.subtitle,
            badge: state?.badge,
            isPlaying: controller.isPlaying,
            skipBackSeconds: Int(state?.skipBackSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_BACK_SECONDS),
            skipForwardSeconds: Int(state?.skipForwardSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_FORWARD_SECONDS),
            speeds: state?.speeds.map { $0.floatValue } ?? [],
            speed: state?.speed ?? 1,
            audio: state?.audioTracks ?? [],
            subtitles: state?.subtitleChoices ?? [],
            skipOfferLabel: state?.skipOffer?.label,
            pictureColours: controller.pictureColours,
            onClose: close,
            onSkipBack: { skip(.back) },
            onTogglePlay: {
                model.userInteraction()
                controller.togglePlayPause()
            },
            onSkipForward: { skip(.forward) },
            onSelectSpeed: { model.selectSpeed($0) },
            onSelectAudio: { model.selectAudio($0) },
            onSelectSubtitle: { model.selectSubtitle($0) },
            onSearchSubtitles: state?.offersSubtitleSearch == true ? { searchingSubtitles = true } : nil,
            onSkipOffer: { model.skip() },
            onToggleStats: { model.toggleStats() },
            onTouch: { model.userInteraction() },
            onOpenChange: menuOpenChanged
        ) { bottomBar }
    }

    var safeArea: EdgeInsets { controlsFrame.safeArea }
    var spansWidth: Bool { PlayerInsets.spansWidth(size: controlsFrame.size) }

    var controlsBalance: (leading: CGFloat, trailing: CGFloat) {
        spansWidth ? (0, 0) : PlayerInsets.balance(leading: safeArea.leading, trailing: safeArea.trailing)
    }

    func rowInsets(at edge: PlayerInsets.CameraEdge) -> (leading: CGFloat, trailing: CGFloat) {
        spansWidth ? PlayerInsets.rowInsets(at: edge, leading: book == nil ? safeArea.leading : 0, trailing: safeArea.trailing, regularWidth: horizontalSizeClass == .regular) : (0, 0)
    }

    var showsStats: Bool {
        state?.showDiagnostics == true
    }

    var topBar: some View {
        HStack(alignment: .top, spacing: Space.s3) {
            PlayerIconButton(systemName: "xmark", label: Strings.shared.close, action: close)
            VStack(alignment: .leading, spacing: Space.s1) {
                if let title = state?.title, !title.isEmpty {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .contentShape(Rectangle())
                }
                if !metaLine.isEmpty {
                    Text(metaLine)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .padding(.top, Space.s1)
            .onTapGesture(count: 5) { model.toggleStats() }
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
                        },
                        onOpenChange: menuOpenChanged
                    )
                }
                if let state, state.hasTrackChoices {
                    PlayerTrackMenu(
                        audio: state.audioTracks,
                        subtitles: state.subtitleChoices,
                        onSelectAudio: { index in
                            model.selectAudio(index)
                            scheduleHide()
                        },
                        onSelectSubtitle: { index in
                            model.selectSubtitle(index)
                            scheduleHide()
                        },
                        onSearchSubtitles: state.offersSubtitleSearch ? { searchingSubtitles = true } : nil,
                        onTouch: {
                            model.userInteraction()
                            hideTask?.cancel()
                        },
                        onOpenChange: menuOpenChanged
                    )
                    .equatable()
                }
                // Picture in picture doesn't apply to external playback; the button is back when AirPlay stops.
                if pip.isSupported && state?.pictureInPicture != false && !controller.isExternalPlayback {
                    PlayerIconButton(
                        systemName: "pip.enter",
                        label: Strings.shared.pictureInPicture,
                        action: {
                            model.userInteraction()
                            pip.startAfterTurningUpright()
                        }
                    )
                }
                AirPlayButton(
                    onTouch: {
                        model.userInteraction()
                        hideTask?.cancel()
                    },
                    onOpenChange: menuOpenChanged
                )
            }
        }
    }

    /// The subtitle then the picture badge, as plain text.
    var metaLine: String {
        Strings.shared.metaLine(subtitle: state?.subtitle, badge: state?.badge) ?? ""
    }

    /// The scrubber alone while skips in a row show, where it sits with the controls up.
    var seekingBar: some View {
        bottomBar
            .rowInsets(rowInsets(at: .bottom))
            .rowInsets(controlsBalance)
            .screenMargin()
            .bookControls(book)
            .padding(.vertical, Space.s3)
            .controlsSpanWidth(spans: spansWidth)
    }

    var bottomBar: some View {
        PlayerProgressBar(
            clock: controller.clock,
            duration: controller.duration,
            scrubPosition: scrubPosition,
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
    }

    // MARK: - Errors

    /// A failed load or a video that wouldn't play: what happened and Try again. The close button is on top.
    func errorPanel(_ message: String) -> some View {
        LoadErrorView(message: message, onRetry: { model.retry() })
            .frame(maxWidth: 420)
            .padding(Space.s5)
    }

    var closeOverlay: some View {
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
}
