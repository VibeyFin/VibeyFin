import SwiftUI
import Shared

/// Lets SwiftUI watch the offline Home state. `OfflineHomeModel` calls back on the main thread.
final class OfflineHomeObservable: ObservableObject {
    @Published private(set) var state: OfflineHomeUiState?

    private let model = OfflineHomeModel()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] state in
            self?.state = state
        }
    }

    deinit {
        model.close()
    }
}

/// Home with no server: what the profile can watch from the device. A card or a movie plays at once, since there is no page of it to
/// open without the server; a show opens the Downloads tab, where its episodes are.
struct OfflineHomeView: View {
    @StateObject private var model = OfflineHomeObservable()
    @Environment(\.playerSession) private var player

    let onOpenDownloads: () -> Void
    let onSettings: () -> Void
    let onExit: (HomeExit) -> Void

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                header
                    .screenMargin()
                    .padding(.top, Space.s3)
                    .padding(.bottom, Space.s3)
                if let state = model.state, !state.loading {
                    if state.isEmpty {
                        empty
                    } else {
                        ScrollView {
                            VStack(alignment: .leading, spacing: Space.s5) {
                                ContinueRowView(
                                    row: state.continueRow,
                                    onPlay: { player.play($0) },
                                    onDetails: { player.play($0) },
                                    onMarkWatched: { _ in },
                                    onRetry: {},
                                    showsMenu: false
                                )
                                ForEach(state.rows, id: \.id) { row in
                                    MediaRowView(
                                        row: row,
                                        onOpen: { item in
                                            if item.kind == ItemKind.movie {
                                                player.play(item.id)
                                            } else {
                                                onOpenDownloads()
                                            }
                                        },
                                        onSeeAll: { _ in },
                                        onRetry: {}
                                    )
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.bottom, Space.s5)
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        // There is no server to read a poster's menu from.
        .environment(\.tileMenu, nil)
        .onAppear { model.start() }
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: Space.s3) {
            AppLogo()
            Text(Strings.shared.home.titleCased())
                .screenTitle()
                .accessibilityAddTraits(.isHeader)
            Spacer()
            // With no server the profile has only Settings to go to, to switch Offline mode off.
            Button(action: onSettings) {
                ZStack {
                    Circle().fill(Color(.systemGray4))
                    Text(String((model.state?.userName ?? "").prefix(1)).uppercased())
                        .font(.headline)
                        .foregroundStyle(.primary)
                }
                .frame(width: 44, height: 44)
            }
            .buttonStyle(PressableButtonStyle())
            .accessibilityLabel(Strings.shared.settings.titleCased())
        }
    }

    private var empty: some View {
        VStack(spacing: Space.s2) {
            Text(Strings.shared.downloadsEmptyTitle).font(.headline)
            Text(Strings.shared.downloadsEmptyHelp)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .screenMargin()
    }
}
