import SwiftUI
import Shared

struct SettingsView: View {
    @StateObject private var model = SettingsObservable()

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: Space.s5) {
                Text(Strings.shared.settings.titleCased()).screenTitle()
                // The whole row is the toggle, so the label is a target too. The switch is light on
                // dark: aqua is for the one primary action on a screen.
                Toggle(isOn: alwaysAsk) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(Strings.shared.alwaysAskWhoIsWatchingTitle)
                            .font(.headline)
                            .foregroundStyle(.primary)
                        Text(Strings.shared.alwaysAskWhoIsWatchingHelp)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .tint(Color(.label))
                if model.state?.hasProfile == true {
                    Toggle(isOn: showPlaybackStats) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(Strings.shared.playbackStatsTitle)
                                .font(.headline)
                                .foregroundStyle(.primary)
                            Text(Strings.shared.playbackStatsHelp)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .tint(Color(.label))
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .screenMargin()
            .padding(.vertical, Space.s5)
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
    }

    private var showPlaybackStats: Binding<Bool> {
        Binding(
            get: { model.state?.showPlaybackStats ?? false },
            set: { model.setShowPlaybackStats($0) }
        )
    }

    private var alwaysAsk: Binding<Bool> {
        Binding(
            get: { model.state?.alwaysAskWhoIsWatching ?? false },
            set: { model.setAlwaysAsk($0) }
        )
    }
}
