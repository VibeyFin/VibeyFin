import SwiftUI
import Shared

struct SettingsView: View {
    @StateObject private var model = SettingsObservable()

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            ScrollView {
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
                    bingeThresholdRow
                    Toggle(isOn: showHomeHero) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(Strings.shared.homeHeroTitle)
                                .font(.headline)
                                .foregroundStyle(.primary)
                            Text(Strings.shared.homeHeroHelp)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .tint(Color(.label))
                    homeGenreRowsRow
                    playbackSection
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .screenMargin()
            .padding(.vertical, Space.s5)
            }
        }
        .onAppear { model.start() }
        .backMenuTitle(Strings.shared.settings.titleCased())
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
    }

    /// Skip back and forward as menus, and auto-skip of intros and recaps. The footer says what the seconds drive.
    private var playbackSection: some View {
        VStack(alignment: .leading, spacing: Space.s5) {
            Text(Strings.shared.playbackSection.titleCased())
                .font(.title3.weight(.semibold))
                .foregroundStyle(.primary)
                .accessibilityAddTraits(.isHeader)
            skipRow(
                title: Strings.shared.skipBack,
                choices: ProfileSettings.Companion.shared.SKIP_BACK_CHOICES,
                selection: skipBack
            )
            skipRow(
                title: Strings.shared.skipForward,
                choices: ProfileSettings.Companion.shared.SKIP_FORWARD_CHOICES,
                selection: skipForward
            )
            Toggle(isOn: autoSkip) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(Strings.shared.autoSkipSegments)
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text(Strings.shared.autoSkipSegmentsHelp)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .tint(Color(.label))
            Text(Strings.shared.skipHelp)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private func skipRow(title: String, choices: [KotlinInt], selection: Binding<Int32>) -> some View {
        HStack(alignment: .center) {
            Text(title.titleCased())
                .font(.headline)
                .foregroundStyle(.primary)
            Spacer(minLength: Space.s3)
            Picker(title.titleCased(), selection: selection) {
                ForEach(choices.map { $0.int32Value }, id: \.self) { choice in
                    Text(Strings.shared.skipSeconds(seconds: choice)).tag(choice)
                }
            }
            .pickerStyle(.menu)
            .tint(Color(.secondaryLabel))
        }
    }

    /// Off, or after 2 to 5 episodes in a row, as a menu on the row.
    private var bingeThresholdRow: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(Strings.shared.stillWatchingTitle)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(Strings.shared.stillWatchingHelp)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: Space.s3)
            Picker(Strings.shared.stillWatchingTitle, selection: bingeThreshold) {
                ForEach([0, 2, 3, 4, 5] as [Int32], id: \.self) { choice in
                    Text(choice == 0 ? Strings.shared.stillWatchingOff : Strings.shared.stillWatchingAfter(n: choice)).tag(choice)
                }
            }
            .pickerStyle(.menu)
            .tint(Color(.secondaryLabel))
        }
    }

    /// How many genres get a row on Home: all, the biggest few, or none.
    private var homeGenreRowsRow: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(Strings.shared.homeGenreRowsTitle)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(Strings.shared.homeGenreRowsHelp)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: Space.s3)
            Picker(Strings.shared.homeGenreRowsTitle, selection: homeGenreRows) {
                ForEach([0, 20, 10, 5, 3, -1] as [Int32], id: \.self) { choice in
                    Text(genreRowsLabel(choice)).tag(choice)
                }
            }
            .pickerStyle(.menu)
            .tint(Color(.secondaryLabel))
        }
    }

    private func genreRowsLabel(_ value: Int32) -> String {
        switch value {
        case 0: return Strings.shared.homeGenreRowsAll
        case -1: return Strings.shared.homeGenreRowsNone
        default: return Strings.shared.homeGenreRowsTop(n: value)
        }
    }

    private var skipBack: Binding<Int32> {
        Binding(
            get: { model.state?.skipBackSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_BACK_SECONDS },
            set: { model.setSkipBack($0) }
        )
    }

    private var skipForward: Binding<Int32> {
        Binding(
            get: { model.state?.skipForwardSeconds ?? ProfileSettings.Companion.shared.DEFAULT_SKIP_FORWARD_SECONDS },
            set: { model.setSkipForward($0) }
        )
    }

    private var autoSkip: Binding<Bool> {
        Binding(
            get: { model.state?.autoSkipSegments ?? false },
            set: { model.setAutoSkip($0) }
        )
    }

    private var showHomeHero: Binding<Bool> {
        Binding(
            get: { model.state?.showHomeHero ?? true },
            set: { model.setShowHomeHero($0) }
        )
    }

    private var homeGenreRows: Binding<Int32> {
        Binding(
            get: { model.state?.homeGenreRows ?? 0 },
            set: { model.setHomeGenreRows($0) }
        )
    }

    private var bingeThreshold: Binding<Int32> {
        Binding(
            get: { model.state?.bingeThreshold ?? 3 },
            set: { model.setBingeThreshold($0) }
        )
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
