import SwiftUI
import Shared

struct HomeView: View {
    @StateObject private var model = HomeObservable()

    /// Called when the screen should be left: the user chose to, or the session ended.
    let onExit: (HomeExit) -> Void
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
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
        }
        .onAppear { model.start() }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
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
