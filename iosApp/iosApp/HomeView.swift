import SwiftUI
import Shared

/// Plays one item from Home, full screen. A Continue watching card plays at once.
private struct PlayRequest: Identifiable {
    let id: String
}

struct HomeView: View {
    @StateObject private var model = HomeObservable()
    @Environment(\.scenePhase) private var scenePhase
    @State private var playing: PlayRequest?

    /// Called when the screen should be left: the user chose to, or the session ended.
    let onExit: (HomeExit) -> Void
    let onOpen: (MediaItem) -> Void
    let onSettings: () -> Void

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                header
                    .screenMargin()
                    .padding(.top, Space.s5)
                // No side margin on the scroll content: the Continue watching row runs to the screen
                // edge, so it and the libraries apply the margin themselves.
                if let state = model.state, state.serverId != nil {
                    ScrollView {
                        VStack(alignment: .leading, spacing: Space.s5) {
                            // Always laid out, empty or not, so the libraries never move when a card turns up.
                            ContinueRowView(
                                row: state.continueRow,
                                onPlay: { playing = PlayRequest(id: $0) },
                                onRetry: { model.retryContinue() }
                            )
                            libraries(state)
                                .screenMargin()
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, Space.s5)
                    }
                    .refreshable { await model.refresh() }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .onAppear { model.start() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { model.foreground() }
        }
        .fullScreenCover(item: $playing) { request in
            PlayerView(itemId: request.id, onExit: onExit)
        }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: Space.s1) {
                if let userName = model.state?.userName {
                    Text(Strings.shared.greeting(name: userName)).screenTitle()
                } else {
                    Text(Strings.shared.home.titleCased()).screenTitle()
                }
                if let serverName = model.state?.serverName {
                    Text(serverName)
                        .font(.body)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            profileMenu
        }
    }

    @ViewBuilder
    private func libraries(_ state: HomeUiState) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(Strings.shared.libraries.titleCased())
                .sectionTitle()
                .padding(.bottom, Space.s3)
            if state.loadingLibraries {
                LoadingIndicator()
            } else if let message = state.librariesMessage {
                LoadErrorView(message: message, onRetry: { model.retryLibraries() })
            } else if state.libraries.isEmpty {
                Text(Strings.shared.noLibraries)
                    .font(.body)
                    .foregroundStyle(.secondary)
            } else {
                ArtworkGrid(items: state.libraries, minimumWidth: 150, onOpen: onOpen)
            }
        }
    }

    private var profileMenu: some View {
        Menu {
            Button(Strings.shared.switchProfile.titleCased()) { model.switchProfile() }
            Button(Strings.shared.switchServer.titleCased()) { model.switchServer() }
            Button(Strings.shared.settings.titleCased()) { onSettings() }
            Button(Strings.shared.signOut.titleCased()) { model.signOut() }
        } label: {
            ZStack {
                Circle().fill(Color(.systemGray4))
                Text(String((model.state?.userName ?? "").prefix(1)).uppercased())
                    .font(.headline)
                    .foregroundStyle(.primary)
            }
            .frame(width: 44, height: 44)
            .accessibilityLabel(model.state?.userName ?? "")
        }
    }
}
