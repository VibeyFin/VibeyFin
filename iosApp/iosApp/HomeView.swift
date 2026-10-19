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
                            Text(Strings.shared.greeting(name: userName)).vibeTitle(size: 34)
                        } else {
                            Text(Strings.shared.home).vibeTitle(size: 34)
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
        Text(Strings.shared.libraries)
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(Palette.textMuted)
            .padding(.bottom, 12)
        if state.loadingLibraries {
            LoadingIndicator()
        } else if let message = state.librariesMessage {
            LoadErrorView(message: message, onRetry: { model.retryLibraries() })
        } else if state.libraries.isEmpty {
            Text(Strings.shared.noLibraries)
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
            Button(Strings.shared.switchProfile) { model.switchProfile() }
            Button(Strings.shared.switchServer) { model.switchServer() }
            Button(Strings.shared.settings) { onSettings() }
            Button(Strings.shared.signOut) { model.signOut() }
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
