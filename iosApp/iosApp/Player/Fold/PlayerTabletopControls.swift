import Shared
import SwiftUI

/// The lower half of the player in the tabletop pose: a flat surface of tiles. Same controls as the
/// full-screen player; only the layout differs.
struct PlayerTabletopControls<Progress: View>: View {
    let title: String
    let subtitle: String?
    let badge: String?
    let isPlaying: Bool
    let skipBackSeconds: Int
    let skipForwardSeconds: Int
    let speeds: [Float]
    let speed: Float
    let audio: [TrackOption]
    let subtitles: [TrackOption]
    let skipOfferLabel: String?
    let pictureColours: PictureColourSampler
    let onClose: () -> Void
    let onSkipBack: () -> Void
    let onTogglePlay: () -> Void
    let onSkipForward: () -> Void
    let onSelectSpeed: (Float) -> Void
    let onSelectAudio: (Int32) -> Void
    let onSelectSubtitle: (Int32) -> Void
    let onSearchSubtitles: (() -> Void)?
    let onSkipOffer: () -> Void
    let onToggleStats: () -> Void
    let onTouch: () -> Void
    let onOpenChange: (Bool) -> Void
    @ViewBuilder let progress: () -> Progress

    var body: some View {
        GeometryReader { proxy in
            let gap: CGFloat = TabletopMetrics.rowGap(availableHeight: proxy.size.height, hasSkipOffer: skipOfferLabel != nil)
            VStack(spacing: 0) {
                titleRow
                Spacer(minLength: gap)
                transport
                Spacer(minLength: gap)
                if let skipOfferLabel {
                    HStack {
                        Spacer(minLength: 0)
                        SkipButton(label: skipOfferLabel, action: onSkipOffer)
                    }
                    .frame(height: TabletopMetrics.skipOfferHeight)
                    Spacer(minLength: gap)
                }
                progress()
                Spacer(minLength: gap)
                secondaryRow
            }
            .padding(TabletopMetrics.inset)
        }
        .background {
            ZStack(alignment: .top) {
                Color(.systemBackground).ignoresSafeArea()
                HingeGlowView(sampler: pictureColours)
            }
        }
    }

    private var titleRow: some View {
        HStack(spacing: Space.s3) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: Space.s2) {
                    Text(title)
                        .font(.headline)
                        .lineLimit(1)
                    if let badge, !badge.isEmpty {
                        FormatBadge(text: badge, fill: Color(.secondarySystemBackground))
                    }
                }
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture(count: 5, perform: onToggleStats)
            Spacer(minLength: 0)
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .frame(width: TabletopMetrics.titleRowHeight, height: TabletopMetrics.titleRowHeight)
                    .background(tileShape(radius: TabletopMetrics.closeRadius))
            }
            .buttonStyle(PressableButtonStyle())
            .accessibilityLabel(Strings.shared.close)
        }
        .frame(height: TabletopMetrics.titleRowHeight)
    }

    private var transport: some View {
        GeometryReader { proxy in
            let widths: (side: CGFloat, play: CGFloat) = TabletopMetrics.transportWidths(available: proxy.size.width, gap: Space.s3)
            HStack(spacing: Space.s3) {
                transportTile(
                    symbol: PlayerTransport.symbol("gobackward", seconds: skipBackSeconds),
                    label: Strings.shared.skipBackLabel(seconds: Int32(skipBackSeconds)),
                    width: widths.side,
                    primary: false,
                    action: onSkipBack
                )
                transportTile(
                    symbol: isPlaying ? "pause.fill" : "play.fill",
                    label: isPlaying ? Strings.shared.pause : Strings.shared.play,
                    width: widths.play,
                    primary: true,
                    action: onTogglePlay
                )
                transportTile(
                    symbol: PlayerTransport.symbol("goforward", seconds: skipForwardSeconds),
                    label: Strings.shared.skipForwardLabel(seconds: Int32(skipForwardSeconds)),
                    width: widths.side,
                    primary: false,
                    action: onSkipForward
                )
            }
        }
        .frame(height: TabletopMetrics.transportHeight)
    }

    private func transportTile(symbol: String, label: String, width: CGFloat, primary: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.title.weight(.semibold))
                .foregroundStyle(primary ? Brand.onAction : Color.primary)
                .frame(width: width, height: TabletopMetrics.transportHeight)
                .background(tileShape(radius: TabletopMetrics.transportRadius, fill: primary ? Brand.action : Color(.secondarySystemBackground)))
        }
        .buttonStyle(PressableButtonStyle())
        .accessibilityLabel(label)
    }

    private var secondaryRow: some View {
        HStack(spacing: Space.s3) {
            menuTile(label: Strings.shared.speed, accessibility: Strings.shared.labelled(label: Strings.shared.speed, value: Strings.shared.speedValue(speed: speed))) {
                Picker(Strings.shared.speed.titleCased(), selection: Binding(get: { speed }, set: onSelectSpeed)) {
                    ForEach(speeds, id: \.self) { value in
                        Text(Strings.shared.speedValue(speed: value)).tag(value)
                    }
                }
            } icon: {
                Text(Strings.shared.speedValue(speed: speed)).font(.title3.weight(.semibold).monospacedDigit())
            }
            if !subtitles.isEmpty {
                menuTile(label: Strings.shared.subtitles, accessibility: Strings.shared.subtitles) {
                    PlayerTrackMenu.picker(options: subtitles, onSelect: onSelectSubtitle)
                    if let onSearchSubtitles {
                        Button(action: onSearchSubtitles) {
                            Label(Strings.shared.searchForSubtitles.titleCased(), systemImage: "magnifyingglass")
                        }
                    }
                } icon: {
                    Image(systemName: subtitles.contains { $0.selected && $0.index >= 0 } ? "captions.bubble.fill" : "captions.bubble")
                        .font(.title3.weight(.semibold))
                }
            }
            if !audio.isEmpty {
                menuTile(label: Strings.shared.audio, accessibility: Strings.shared.audio) {
                    PlayerTrackMenu.picker(options: audio, onSelect: onSelectAudio)
                } icon: {
                    Image(systemName: "waveform").font(.title3.weight(.semibold))
                }
            }
            airPlayTile
        }
        .frame(height: TabletopMetrics.secondaryHeight)
    }

    private func menuTile<Items: View, Icon: View>(
        label: String,
        accessibility: String,
        @ViewBuilder items: () -> Items,
        @ViewBuilder icon: () -> Icon
    ) -> some View {
        Menu {
            items()
        } label: {
            VStack(spacing: Space.s1) {
                icon()
                Text(label).font(.caption).lineLimit(1)
            }
            .foregroundStyle(.primary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(tileShape(radius: TabletopMetrics.secondaryRadius))
        }
        .buttonStyle(PressableButtonStyle())
        .simultaneousGesture(TapGesture().onEnded(onTouch))
        .onMenuOpenChange(onOpenChange)
        .accessibilityLabel(accessibility)
    }

    private var airPlayTile: some View {
        VStack(spacing: 0) {
            AirPlayButton(onTouch: onTouch, onOpenChange: onOpenChange, fillsProposal: true)
                .frame(maxHeight: .infinity)
            Text(Strings.shared.airplay)
                .font(.caption)
                .lineLimit(1)
                .padding(.bottom, Space.s2)
                .allowsHitTesting(false)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(tileShape(radius: TabletopMetrics.secondaryRadius))
    }

    private func tileShape(radius: CGFloat, fill: Color = Color(.secondarySystemBackground)) -> some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous).fill(fill)
    }
}
