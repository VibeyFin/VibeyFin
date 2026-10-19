import SwiftUI
import Shared

struct HomeView: View {
    @StateObject private var model = HomeObservable()

    /// Called with the server's id when the session ends, so the app can go back to sign-in.
    let onSessionEnded: (String) -> Void

    var body: some View {
        ZStack {
            Palette.ink.ignoresSafeArea()
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
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
        }
        .onAppear { model.start() }
        .onChange(of: model.state?.expired) { expired in
            if expired == true, let serverId = model.state?.serverId {
                onSessionEnded(serverId)
            }
        }
    }
}
