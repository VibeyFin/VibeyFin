import SwiftUI
import Shared

/// The Downloads tab: a card with how much room the downloads take and the bin, the movies, and the shows with their episodes. A finished row plays from the
/// device; one that is not finished opens its page (when there is a server to ask).
struct DownloadsView: View {
    @StateObject private var model = DownloadsObservable()
    @Environment(\.playerSession) private var player
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
        // The tab draws its own header; the system bar above it would only be empty space.
        .tabRootBar()
        .onAppear { model.start() }
        .confirmationDialog(
            model.state?.removal.map { Strings.shared.removeConfirmTitle(count: $0.count, title: $0.title) } ?? "",
            isPresented: Binding(get: { model.state?.removal != nil }, set: { if !$0 { model.dismissRemoval() } }),
            titleVisibility: .visible
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
                    }
                }
            }
            if !state.shows.isEmpty {
                Section(Strings.shared.filterShows.titleCased()) {
                    ForEach(state.shows, id: \.seriesId) { show in
                        showRow(show)
                        if open.contains(show.seriesId) {
                            ForEach(show.episodes, id: \.itemId) { episode in
                                row(episode, waiting: state.waiting, poster: false).padding(.leading, Space.s4)
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
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

    /// A movie or an episode: its picture, its title, where its download is, and a menu for what can be done to it.
    private func row(_ download: Download, waiting: DownloadRunner.Waiting, poster: Bool) -> some View {
        let item = download.item
        let done = DownloadBridge.shared.isDone(download: download)
        let title = poster ? item.title : Strings.shared.taggedTitle(tag: Strings.shared.episodeTag(season: item.seasonNumber, episode: item.episodeNumber), title: item.title)
        return HStack(spacing: Space.s3) {
            Button {
                if done { player.play(download.itemId) } else if !offline { onOpen(download.itemId) }
            } label: {
                HStack(spacing: Space.s3) {
                    Artwork(url: poster ? item.posterUrl : (item.landscapeUrl ?? item.posterUrl), title: item.title, aspectRatio: poster ? 2.0 / 3.0 : 16.0 / 9.0, blurHash: poster ? item.posterBlurHash : item.landscapeBlurHash)
                        .frame(width: poster ? 56 : 112)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                    VStack(alignment: .leading, spacing: Space.s1) {
                        Text(title).font(.headline).foregroundStyle(.primary).lineLimit(2)
                        Text(DownloadBridge.shared.statusLine(download: download, waiting: waiting)).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                        if let message = DownloadBridge.shared.failureMessage(download: download) {
                            Text(message).font(.caption).foregroundStyle(.red).lineLimit(3)
                        }
                        if download.state == DownloadState.running || download.state == DownloadState.paused, let progress = download.progress?.floatValue {
                            // The label colour on a quiet track: aqua is for playback progress, not for downloads.
                            ProgressView(value: progress).tint(Color(.label))
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
                    Artwork(url: show.posterUrl, title: show.title, aspectRatio: 2.0 / 3.0, blurHash: show.posterBlurHash)
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
