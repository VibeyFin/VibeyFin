import SwiftUI
import Shared

/// Lets SwiftUI watch the banner that says the app is offline or the server cannot be reached. `AppBannerModel` calls back on the
/// main thread, with nil when there is nothing to say.
final class AppBannerObservable: ObservableObject {
    @Published private(set) var banner: Banner?

    private let model = AppBannerModel()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] banner in
            self?.banner = banner
        }
    }

    func act() { model.onAction() }

    deinit {
        model.close()
    }
}

/// Whether the app is offline: no network, or Offline mode on. The shell shows three tabs while it is.
final class OfflineModeObservable: ObservableObject {
    @Published private(set) var isOffline = false

    private let model = OfflineModeModel()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] value in
            self?.isOffline = value.boolValue
        }
    }

    deinit {
        model.close()
    }
}

/// The line across the top: "You're offline" (with Go online when it is the user's own choice), or "Can't reach the server" with
/// Use offline. Nothing when all is well.
struct OfflineBannerView: View {
    @ObservedObject var model: AppBannerObservable

    var body: some View {
        if let banner = model.banner {
            HStack(alignment: .center, spacing: Space.s3) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(banner.title).font(.subheadline.weight(.semibold))
                    Text(banner.help).font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: Space.s2)
                if let label = banner.actionLabel {
                    Button(action: { model.act() }) {
                        Text(label.titleCased())
                            .font(.subheadline.weight(.semibold))
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableButtonStyle())
                }
            }
            .padding(.horizontal, Space.s4)
            .padding(.vertical, Space.s2)
            .background(Color(.secondarySystemBackground))
            .accessibilityElement(children: .combine)
        }
    }
}
