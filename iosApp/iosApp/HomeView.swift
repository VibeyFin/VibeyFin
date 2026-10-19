import SwiftUI
import Shared

struct HomeView: View {
    @StateObject private var model = HomeObservable()

    /// Called when the screen should be left: the user chose to, or the session ended.
    let onExit: (HomeExit) -> Void
    let onOpen: (MediaItem) -> Void
    let onSettings: () -> Void

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
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
                if let state = model.state, state.serverId != nil {
                    libraries(state)
                        .padding(.top, Space.s5)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .screenMargin()
            .padding(.vertical, Space.s5)
        }
        .onAppear { model.start() }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    @ViewBuilder
    private func libraries(_ state: HomeUiState) -> some View {
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
            ScrollView {
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
