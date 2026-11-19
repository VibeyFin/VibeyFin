import SwiftUI
import Shared

/// The Downloads tab: a card with how much room the downloads take and the bin, the movies, and the shows with their episodes. A finished row plays from the
/// device; one that is not finished opens its page (when there is a server to ask).
struct DownloadsView: View {
    @StateObject private var model = DownloadsObservable()
    @Environment(\.playerSession) private var player
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var open: Set<String> = []

    /// Opens an item's page; not used offline, where there is no page.
    let onOpen: (String) -> Void
    let offline: Bool
    let onExit: (HomeExit) -> Void

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(spacing: 0) {
                TabHeader(title: Strings.shared.tabDownloads)
                if let state = model.state, !state.loading {
                    if state.isEmpty {
                        empty.frame(maxHeight: .infinity)
                    } else {
                        list(state)
                    }
                } else {
                    Spacer(minLength: 0)
                }
            }
        }
        .backMenuTitle(Strings.shared.tabDownloads.titleCased())
        .tabRootBar(title: Strings.shared.tabDownloads)
        .onAppear { model.start() }
        .alert(
            model.state?.removal.map { Strings.shared.removeConfirmTitle(count: $0.count, title: $0.title) } ?? "",
            isPresented: Binding(get: { model.state?.removal != nil }, set: { if !$0 { model.dismissRemoval() } })
        ) {
            Button(Strings.shared.removeConfirmAction.titleCased(), role: .destructive) { model.confirmRemove() }
            Button(Strings.shared.cancel.titleCased(), role: .cancel) { model.dismissRemoval() }
        } message: {
            if let removal = model.state?.removal { Text(Strings.shared.removeConfirmBody(bytes: removal.bytes)) }
        }
        .onChange(of: model.state?.exit != nil) { _, hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    private var empty: some View {
        VStack(spacing: Space.s2) {
            Text(Strings.shared.downloadsEmptyTitle).font(.headline)
            Text(Strings.shared.downloadsEmptyHelp).font(.subheadline).foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .screenMargin()
    }

    private func list(_ state: DownloadsUiState) -> some View {
        List {
            if let storage = state.storageLine {
                Section { storageCard(storage) }
            }
            if !state.movies.isEmpty {
                Section(Strings.shared.filterMovies.titleCased()) {
                    ForEach(state.movies, id: \.itemId) { download in
                        row(download, waiting: state.waiting, poster: true)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) { removeSwipe(download) }
                    }
                }
            }
            if !state.shows.isEmpty {
                Section(Strings.shared.filterShows.titleCased()) {
                    ForEach(state.shows, id: \.seriesId) { show in
                        showRow(show)
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) { removeShowSwipe(show) }
                        if open.contains(show.seriesId) {
                            ForEach(show.episodes, id: \.itemId) { episode in
                                row(episode, waiting: state.waiting, poster: false).padding(.leading, sizeClass == .compact ? Space.s2 : Space.s4)
                                    .swipeActions(edge: .trailing, allowsFullSwipe: true) { removeSwipe(episode) }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    /// Swiping a movie or an episode deletes it at once, as Remove in its menu does. The label is short so the tray stays narrow; VoiceOver gets the full words.
    private func removeSwipe(_ download: Download) -> some View {
        Button(role: .destructive) { model.act(download, DownloadAction.remove) } label: {
            Label(Strings.shared.removeConfirmAction.titleCased(), systemImage: "trash")
        }
        .accessibilityLabel(Strings.shared.removeDownload)
    }

    /// Swiping a show asks first, since it takes every episode. No full swipe: the row would slide out before the question is answered.
    private func removeShowSwipe(_ show: DownloadedShow) -> some View {
        Button(role: .destructive) { model.removeShow(show) } label: {
            Label(Strings.shared.removeConfirmAction.titleCased(), systemImage: "trash")
        }
        .accessibilityLabel(Strings.shared.removeDownloadsOf(count: Int32(show.episodes.count)))
    }

    /// How much the downloads take on the device, and the bin: an icon, which VoiceOver names.
    private func storageCard(_ storage: String) -> some View {
        HStack(spacing: Space.s3) {
            Image(systemName: "arrow.down.circle").foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(Strings.shared.downloadsStorageHeading).font(.footnote).foregroundStyle(.secondary)
                Text(storage).font(.headline).foregroundStyle(.primary)
            }
            .accessibilityElement(children: .combine)
            Spacer(minLength: 0)
            Button { model.removeAll() } label: {
                Image(systemName: "trash").frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .buttonStyle(.borderless)
            .foregroundStyle(.secondary)
            .accessibilityLabel(Strings.shared.removeAllDownloads)
        }
    }

    /// A movie or an episode: its picture, its title, where its download is, and a menu for what can be done to it. The progress bar runs under the whole row.
    private func row(_ download: Download, waiting: DownloadRunner.Waiting, poster: Bool) -> some View {
        let item                 = download.item
        let done                 = DownloadBridge.shared.isDone(download: download)
        let episodeTag: String?  = poster ? nil : Strings.shared.episodeTag(season: item.seasonNumber, episode: item.episodeNumber)
        let thumbnailWidth: CGFloat = poster ? 56 : (sizeClass == .compact ? 96 : 112)
        let inProgress: Bool     = download.state == DownloadState.running || download.state == DownloadState.paused
        return VStack(alignment: .leading, spacing: Space.s2) {
            HStack(spacing: Space.s2) {
                Button {
                    if done { player.play(download.itemId) } else if !offline { onOpen(download.itemId) }
                } label: {
                    HStack(spacing: Space.s3) {
                        Artwork(url: poster ? item.posterUrl : (item.landscapeUrl ?? item.posterUrl), title: item.title, aspectRatio: poster ? 2.0 / 3.0 : 16.0 / 9.0, blurHash: poster ? item.posterBlurHash : item.landscapeBlurHash, showsTitle: false)
                            .frame(width: thumbnailWidth)
                            .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                        VStack(alignment: .leading, spacing: Space.s1) {
                            if let episodeTag { Text(episodeTag).font(.caption).foregroundStyle(.secondary) }
                            Text(item.title).font(.headline).foregroundStyle(.primary).lineLimit(2)
                            let status = DownloadBridge.shared.status(download: download, waiting: waiting)
                            Text(status.state).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                            if let detail = status.detail { Text(detail).font(.footnote).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }
                            if let message = DownloadBridge.shared.failureMessage(download: download) {
                                Text(message).font(.caption).foregroundStyle(.red).lineLimit(3)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Menu {
                    DownloadActionButtons(actions: DownloadBridge.shared.actions(download: download)) { model.act(download, $0) }
                } label: {
                    Image(systemName: "ellipsis").frame(minWidth: 44, minHeight: 44)
                }
                .accessibilityLabel(Strings.shared.showOptions)
            }
            if inProgress, let progress = download.progress?.floatValue {
                ProgressView(value: progress).tint(Color(.label))
            }
        }
        .accessibilityElement(children: .contain)
    }

    /// A show: its picture, its name, how many episodes and how much room, a chevron that opens its episodes, and a menu.
    private func showRow(_ show: DownloadedShow) -> some View {
        let expanded = open.contains(show.seriesId)
        return HStack(spacing: Space.s3) {
            Button {
                if expanded { open.remove(show.seriesId) } else { open.insert(show.seriesId) }
            } label: {
                HStack(spacing: Space.s3) {
                    Artwork(url: show.posterUrl, title: show.title, aspectRatio: 2.0 / 3.0, blurHash: show.posterBlurHash, showsTitle: false)
                        .frame(width: 56)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                    VStack(alignment: .leading, spacing: Space.s1) {
                        Text(show.title).font(.headline).foregroundStyle(.primary).lineLimit(2)
                        Text(show.line).font(.subheadline).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: expanded ? "chevron.up" : "chevron.down").foregroundStyle(.secondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Menu {
                if !offline {
                    Button { onOpen(show.seriesId) } label: { Label(Strings.shared.goToSeries(name: show.title), systemImage: "info.circle") }
                }
                if show.canResume {
                    Button { model.resumeShow(show) } label: {
                        Label(show.resumeLabel.titleCased(), systemImage: "arrow.clockwise")
                    }
                }
                Button(role: .destructive) { model.removeShow(show) } label: {
                    Label(Strings.shared.removeDownloadsOf(count: Int32(show.episodes.count)), systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis").frame(minWidth: 44, minHeight: 44)
            }
            .accessibilityLabel(Strings.shared.showOptions)
        }
        .accessibilityElement(children: .contain)
    }
}
