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
            Palette.ink.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        if let userName = model.state?.userName {
                            Text("Hi, \(userName)").vibeTitle(size: 34)
                        } else {
                            Text("Home").vibeTitle(size: 34)
                        }
                        if let serverName = model.state?.serverName {
                            Text(serverName)
                                .font(.body)
                                .foregroundColor(Palette.textMuted)
                        }
                    }
                    Spacer()
                    profileMenu
                }
                if let state = model.state, state.serverId != nil {
                    libraries(state)
                        .padding(.top, 24)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
        }
        .onAppear { model.start() }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    @ViewBuilder
    private func libraries(_ state: HomeUiState) -> some View {
        Text("Libraries")
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(Palette.textMuted)
            .padding(.bottom, 12)
        if state.loadingLibraries {
            LoadingIndicator()
        } else if let message = state.librariesMessage {
            LoadErrorView(message: message, onRetry: { model.retryLibraries() })
        } else if state.libraries.isEmpty {
            Text("This account has no libraries yet. Ask the server's admin to add one.")
                .font(.body)
                .foregroundColor(Palette.textMuted)
        } else {
            ScrollView {
                ArtworkGrid(items: state.libraries, minimumWidth: 150, onOpen: onOpen)
            }
        }
    }

    private var profileMenu: some View {
        Menu {
            Button("Switch profile") { model.switchProfile() }
            Button("Switch server") { model.switchServer() }
            Button("Settings") { onSettings() }
            Button("Sign out") { model.signOut() }
        } label: {
            ZStack {
                Circle().fill(Palette.surfaceHigh)
                Text(String((model.state?.userName ?? "").prefix(1)).uppercased())
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(Palette.text)
            }
            .frame(width: 44, height: 44)
        }
    }
}
