import SwiftUI
import Shared

/// Lets SwiftUI watch the banner that says the server cannot be reached, and the toast as the app goes offline. `AppBannerModel`
/// calls back on the main thread, with nil when there is nothing to say.
final class AppBannerObservable: ObservableObject {
    @Published private(set) var banner: Banner?
    @Published private(set) var toast: String?

    private let model = AppBannerModel()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] banner in
            self?.banner = banner
        }
        model.observeToast { [weak self] message in
            // Shown once: the model forgets it, and the toast keeps its own copy until it goes.
            guard let message else { return }
            self?.toast = message
            self?.model.onToastShown()
        }
    }

    func act() { model.onAction() }

    func dismissToast() { toast = nil }

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

/// The line across the top: "Can't reach the server" with Use offline. Nothing when all is well; going offline is a toast instead.
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

/// Says, once, that the app went offline: a small capsule on the system material at the bottom, above the tab bar. iOS has no
/// toast, so this is one, in the shape of the failure banner: it never blocks anything, takes no tap, and goes by itself after a
/// few seconds. VoiceOver reads it as it appears.
private struct OfflineToast: ViewModifier {
    @ObservedObject var model: AppBannerObservable

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let message = model.toast {
                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Space.s4)
                        .padding(.vertical, Space.s3)
                        .background(.regularMaterial, in: Capsule())
                        .padding(.horizontal, Space.s4)
                        // Above the tab bar, which floats over the bottom of the screen.
                        .padding(.bottom, 96)
                        .allowsHitTesting(false)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .onAppear { UIAccessibility.post(notification: .announcement, argument: message) }
                        .task(id: message) {
                            try? await Task.sleep(nanoseconds: 3_500_000_000)
                            model.dismissToast()
                        }
                }
            }
            .animation(.default, value: model.toast)
    }
}

extension View {
    func offlineToast(_ model: AppBannerObservable) -> some View {
        modifier(OfflineToast(model: model))
    }
}
