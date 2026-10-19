import SwiftUI
import Shared

struct SettingsView: View {
    @StateObject private var model = SettingsObservable()

    var body: some View {
        ZStack {
            Palette.ink.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 24) {
                Text("Settings").vibeTitle(size: 34)
                // The whole row is the toggle, so the label is a target too. The switch is light on
                // dark: amber is for the one primary action on a screen.
                Toggle(isOn: alwaysAsk) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Always ask who's watching")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(Palette.text)
                        Text("Show the profile screen every time the app opens instead of going straight to the last profile.")
                            .font(.subheadline)
                            .foregroundColor(Palette.textMuted)
                    }
                }
                .tint(Palette.text)
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
    }

    private var alwaysAsk: Binding<Bool> {
        Binding(
            get: { model.state?.alwaysAskWhoIsWatching ?? false },
            set: { model.setAlwaysAsk($0) }
        )
    }
}
