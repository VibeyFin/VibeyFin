import SwiftUI
import Shared

/// What a screen can do about downloads: start one, pause, resume or remove it, and for a show or a season ask for its episodes.
/// Every screen with a download control is handed one; the rules (what a tap does in each state, what is asked first) are the shared
/// ViewModel's, so these are only the ways in.
struct DownloadActions {
    var download: () -> Void = {}
    var downloadEpisode: (String) -> Void = { _ in }
    var action: (String, DownloadAction) -> Void = { _, _ in }
    var downloadSeries: () -> Void = {}
    var downloadSeason: (String) -> Void = { _ in }
    var removeSeries: () -> Void = {}
}

/// What a download looks like, in one picture: the arrow when there is none, a ring that fills while it comes in (grey and still while
/// it waits for Wi-Fi or a connection), pause bars, a warning, and a phone with a check once it is on the device. All in the label colour:
/// aqua is for the one primary action and playback progress, and never signals status.
struct DownloadBadgeView: View {
    let badge: DownloadBadge
    let progress: Float?
    var tint: Color = .primary

    var body: some View {
        if badge == DownloadBadge.done {
            Image(systemName: "checkmark.rectangle.portrait").foregroundStyle(tint)
        } else if badge == DownloadBadge.failed {
            Image(systemName: "exclamationmark.triangle").foregroundStyle(tint)
        } else if badge == DownloadBadge.paused {
            Image(systemName: "pause.circle").foregroundStyle(tint)
        } else if badge == DownloadBadge.waiting || badge == DownloadBadge.running {
            ring
        } else {
            Image(systemName: "arrow.down.to.line").foregroundStyle(tint)
        }
    }

    /// A ring that fills clockwise from the top, with the arrow inside it.
    private var ring: some View {
        let shown = badge == DownloadBadge.running ? CGFloat(progress ?? 0) : 0
        return ZStack {
            Circle().stroke(Brand.progressTrack, lineWidth: 2.5)
            Circle()
                .trim(from: 0, to: shown)
                .stroke(tint, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Image(systemName: "arrow.down")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(badge == DownloadBadge.waiting ? tint.opacity(0.55) : tint)
        }
        .padding(2)
    }
}

/// The words and picture of one thing a download's menu can do.
func downloadActionLabel(_ action: DownloadAction) -> (title: String, systemImage: String) {
    if action == DownloadAction.pause { return (Strings.shared.pauseDownload, "pause") }
    if action == DownloadAction.resume { return (Strings.shared.resumeDownload, "play") }
    return (Strings.shared.removeDownload, "trash")
}

/// The menu entries of a download's actions. Remove is destructive, and asks before it deletes.
struct DownloadActionButtons: View {
    let actions: [DownloadAction]
    let onAction: (DownloadAction) -> Void

    var body: some View {
        ForEach(actions, id: \.self) { action in
            let label = downloadActionLabel(action)
            Button(role: action == DownloadAction.remove ? .destructive : nil) {
                onAction(action)
            } label: {
                Label(label.title, systemImage: label.systemImage)
            }
        }
    }
}

/// The download control of one episode, at the end of its row or on its card: the arrow while there is none (a tap starts it), then the
/// badge of its state (a tap opens a menu to pause, resume or remove). Nothing for an episode with no download when the user may not
/// download.
struct EpisodeDownloadButton: View {
    let title: String
    let download: Download?
    let canDownload: Bool
    let onStart: () -> Void
    let onAction: (DownloadAction) -> Void
    /// On a card's still: on a dark disc, so the label colour reads on any picture.
    var overPicture = false

    var body: some View {
        if download == nil && !canDownload {
            EmptyView()
        } else if let download {
            Menu {
                DownloadActionButtons(actions: DownloadBridge.shared.actions(download: download), onAction: onAction)
            } label: {
                icon(DownloadBridge.shared.badge(download: download), progress: download.progress?.floatValue)
            }
            .accessibilityLabel(Strings.shared.downloadActionDescription(title: title, state: DownloadBridge.shared.stateWord(download: download)))
        } else {
            Button(action: onStart) { icon(DownloadBadge.none, progress: nil) }
                .buttonStyle(PressableButtonStyle())
                .accessibilityLabel(Strings.shared.downloadDescription(title: title))
        }
    }

    private func icon(_ badge: DownloadBadge, progress: Float?) -> some View {
        DownloadBadgeView(badge: badge, progress: progress, tint: overPicture ? .white : .primary)
            .font(.title3)
            .frame(width: 28, height: 28)
            .padding(overPicture ? 6 : 8)
            .background {
                if overPicture { Circle().fill(Brand.scrim) }
            }
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
    }
}

/// The download entries of an episode's context menu: start it, or pause, resume or remove what is there.
struct EpisodeDownloadMenuItems: View {
    let episodeId: String
    let download: Download?
    let canDownload: Bool
    let actions: DownloadActions

    var body: some View {
        if let download {
            DownloadActionButtons(actions: DownloadBridge.shared.actions(download: download)) { actions.action(episodeId, $0) }
        } else if canDownload {
            Button { actions.downloadEpisode(episodeId) } label: {
                Label(Strings.shared.download.titleCased(), systemImage: "arrow.down.to.line")
            }
        }
    }
}

/// The question before a season or a show is downloaded: how many episodes, how much room, and "only episodes I haven't watched".
struct DownloadConfirmSheet: View {
    let prompt: DownloadPrompt
    let onToggleUnwatched: () -> Void
    let onConfirm: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            Text(title).font(.title3.weight(.semibold))
            if prompt.loading {
                Text(Strings.shared.searching).foregroundStyle(.secondary)
            } else if prompt.nothingToDownload {
                Text(Strings.shared.downloadNothingNew).foregroundStyle(.secondary)
            } else {
                if let size = prompt.size { Text(size).foregroundStyle(.secondary) }
                if prompt.canChooseUnwatched {
                    Toggle(isOn: Binding(get: { prompt.unwatchedOnly }, set: { _ in onToggleUnwatched() })) {
                        Text(Strings.shared.downloadUnwatchedOnly)
                    }
                    .tint(Color(.label))
                }
            }
            Spacer(minLength: 0)
            HStack(spacing: Space.s3) {
                Button(Strings.shared.cancel.titleCased(), action: onDismiss)
                    .frame(maxWidth: .infinity, minHeight: 44)
                if !prompt.loading && !prompt.nothingToDownload {
                    PrimaryButton(action: onConfirm) { Text(Strings.shared.downloadConfirmAction.titleCased()) }
                        .disabled(prompt.count == 0)
                }
            }
        }
        .padding(Space.s5)
        .presentationDetents([.height(prompt.canChooseUnwatched ? 300 : 230)])
    }

    private var title: String {
        prompt.loading || prompt.nothingToDownload ? prompt.title : Strings.shared.downloadConfirmTitle(episodes: prompt.count)
    }
}

extension View {
    /// The questions around downloads: before a season or a show is downloaded, and before downloads are deleted.
    func downloadPrompts(
        prompt: DownloadPrompt?,
        removal: RemovePrompt?,
        onToggleUnwatched: @escaping () -> Void,
        onConfirm: @escaping () -> Void,
        onDismiss: @escaping () -> Void,
        onConfirmRemove: @escaping () -> Void,
        onDismissRemoval: @escaping () -> Void
    ) -> some View {
        self
            .sheet(isPresented: Binding(get: { prompt != nil }, set: { if !$0 { onDismiss() } })) {
                if let prompt {
                    DownloadConfirmSheet(prompt: prompt, onToggleUnwatched: onToggleUnwatched, onConfirm: onConfirm, onDismiss: onDismiss)
                }
            }
            // An alert, not a confirmationDialog: on iPad that is a popover pointing at the screen's top edge.
            .alert(
                removal.map { Strings.shared.removeConfirmTitle(count: $0.count, title: $0.title) } ?? "",
                isPresented: Binding(get: { removal != nil }, set: { if !$0 { onDismissRemoval() } })
            ) {
                Button(Strings.shared.removeConfirmAction.titleCased(), role: .destructive, action: onConfirmRemove)
                Button(Strings.shared.cancel.titleCased(), role: .cancel, action: onDismissRemoval)
            } message: {
                if let removal { Text(Strings.shared.removeConfirmBody(bytes: removal.bytes)) }
            }
    }
}
