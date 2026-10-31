import SwiftUI
import Shared

/// The one filled primary action on a screen: an aqua capsule, full width. Native `.borderedProminent`
/// so the system supplies press, disabled and Liquid Glass-era behaviour; the label sits on `onAccent`.
struct PrimaryButton<Label: View>: View {
    let action: () -> Void
    @ViewBuilder let label: () -> Label
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Button(action: action) {
            label()
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .controlSize(.large)
        .tint(Brand.accent)
        // Disabled keeps a readable label: the system greys the fill, and `onAccent` on grey is not legible.
        .foregroundStyle(isEnabled ? Brand.onAccent : Color.secondary)
    }
}

/// A spinner that shows on the aqua of a busy primary button.
struct PrimaryButtonSpinner: View {
    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .tint(Brand.onAccent)
            .scaleEffect(0.8)
    }
}

extension PrimaryButton where Label == Text {
    init(_ title: String, action: @escaping () -> Void) {
        self.init(action: action) { Text(title.titleCased()) }
    }
}

/// A secondary action: the system's `.bordered` capsule with a grey fill, full width.
struct SecondaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title.titleCased())
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.capsule)
        .controlSize(.large)
        .tint(Color(.systemGray))
        .foregroundStyle(.primary)
    }
}

/// A secondary action with only an icon: the system's `.bordered` circle with a grey fill. [label] is what
/// VoiceOver says, since there is no text.
struct IconSecondaryButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
        }
        .buttonStyle(.bordered)
        .circleBorderShape()
        .controlSize(.large)
        .tint(Color(.systemGray))
        .foregroundStyle(.primary)
        .accessibilityLabel(label)
    }
}

private extension View {
    /// The circle border shape arrived in iOS 17; on iOS 16 the capsule is the closest the system offers.
    @ViewBuilder
    func circleBorderShape() -> some View {
        if #available(iOS 17, *) {
            buttonBorderShape(.circle)
        } else {
            buttonBorderShape(.capsule)
        }
    }
}

/// A tertiary action: the system's `.borderless` button, tinted with the app tint.
struct GhostButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(title.titleCased(), action: action)
            .buttonStyle(.borderless)
    }
}

/// A grouped surface that holds a server or a message.
struct Card<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(Space.s4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
            )
    }
}

/// A labelled field on `surface-2`. The border turns red when `isError`; it is the control's edge, so it
/// stays visible at rest.
struct FormField<Content: View>: View {
    let label: String
    var isError = false
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s1) {
            Text(label)
                .font(.caption)
                .foregroundStyle(isError ? Color(.systemRed) : Color.secondary)
            content()
                .foregroundStyle(.primary)
                .padding(Space.s4)
                .background(
                    RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous)
                        .fill(Color(.tertiarySystemBackground))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous)
                        .stroke(isError ? Color(.systemRed) : Color(.systemGray), lineWidth: 1)
                )
        }
    }
}

struct SearchingLabel: View {
    var body: some View {
        HStack(spacing: Space.s2) {
            ProgressView()
                .progressViewStyle(.circular)
                .scaleEffect(0.8)
            Text(Strings.shared.searching)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

/// Says that a change couldn't be saved, with "Try again", at the bottom of the screen. iOS has no
/// snackbar, so this is a small banner on the system material: it never blocks anything, never takes
/// a tap away from the screen, and goes by itself after a few seconds. [message] nil shows nothing.
private struct FailureBanner: ViewModifier {
    let message: String?
    let onRetry: () -> Void
    let onDismiss: () -> Void

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let message {
                    HStack(spacing: Space.s3) {
                        Text(message)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Button(Strings.shared.tryAgain.titleCased(), action: onRetry)
                            .buttonStyle(PressableButtonStyle())
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                    .padding(Space.s4)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                    .padding(Space.s4)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: message) {
                        try? await Task.sleep(nanoseconds: 6_000_000_000)
                        onDismiss()
                    }
                }
            }
            .animation(.default, value: message)
    }
}

extension View {
    func failureBanner(_ message: String?, onRetry: @escaping () -> Void, onDismiss: @escaping () -> Void) -> some View {
        modifier(FailureBanner(message: message, onRetry: onRetry, onDismiss: onDismiss))
    }
}

/// The press feedback for buttons that have no system look of their own (cards, tiles, rows, the glassless
/// player buttons): the label shrinks a little under the finger and springs back. The system's bordered and
/// glass styles keep their own highlight. Nothing moves when the system asks for Reduce Motion.
struct PressableButtonStyle: ButtonStyle {
    /// Called once the finger has stayed down for a moment, not for a tap or a scroll.
    var onHold: (() -> Void)?

    func makeBody(configuration: Configuration) -> some View {
        PressableLabel(configuration: configuration, onHold: onHold)
    }

    private struct PressableLabel: View {
        let configuration: ButtonStyleConfiguration
        let onHold: (() -> Void)?
        @Environment(\.accessibilityReduceMotion) private var reduceMotion

        var body: some View {
            configuration.label
                .scaleEffect(configuration.isPressed && !reduceMotion ? 0.95 : 1)
                .animation(reduceMotion ? nil : .spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
                .task(id: configuration.isPressed) {
                    guard configuration.isPressed, let onHold else { return }
                    try? await Task.sleep(nanoseconds: 150_000_000)
                    if !Task.isCancelled { onHold() }
                }
        }
    }
}
