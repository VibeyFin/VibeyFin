import SwiftUI
import UIKit
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

struct BusyLabel: View {
    let title: String
    let busyTitle: String?
    let busy: Bool

    var body: some View {
        HStack(spacing: Space.s3) {
            if busy { PrimaryButtonSpinner() }
            if !busy || busyTitle != nil {
                Text((busy ? busyTitle ?? title : title).titleCased())
            }
        }
    }
}

extension PrimaryButton where Label == BusyLabel {
    init(_ title: String, busyTitle: String? = nil, busy: Bool, action: @escaping () -> Void) {
        self.init(action: { if !busy { action() } }) { BusyLabel(title: title, busyTitle: busyTitle, busy: busy) }
    }
}

struct SecondaryButton: View {
    let title: String
    var systemImage: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if let systemImage {
                    Label(title.titleCased(), systemImage: systemImage)
                } else {
                    Text(title.titleCased())
                }
            }
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

struct FieldGroup<Content: View>: View {
    var isError = false
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) { content() }
            .background(RoundedRectangle(cornerRadius: Radius.card / 1.5, style: .continuous).fill(Color(.tertiarySystemBackground)))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card / 1.5, style: .continuous)
                    .stroke(isError ? Color(.systemRed) : Color.clear, lineWidth: 1)
            )
    }
}

struct FieldRow<Field: View>: View {
    let label: String
    @ViewBuilder let field: () -> Field

    var body: some View {
        HStack(spacing: Space.s3) {
            Text(label)
                .foregroundStyle(.primary)
                .frame(minWidth: 88, alignment: .leading)
                .accessibilityHidden(true)
            field()
                .accessibilityLabel(label)
        }
        .padding(.horizontal, Space.s4)
        .frame(minHeight: 48)
    }
}

struct UsernameField: View {
    @Binding var text: String

    var body: some View {
        FieldRow(label: Strings.shared.username) {
            TextField("", text: $text, prompt: Text(Strings.shared.required))
                .textContentType(.username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.next)
        }
    }
}

struct PasswordField: View {
    @Binding var text: String
    let onSubmit: () -> Void
    @State private var shown = false

    var body: some View {
        FieldRow(label: Strings.shared.password) {
            HStack(spacing: Space.s2) {
                Group {
                    if shown {
                        TextField("", text: $text, prompt: Text(Strings.shared.required))
                    } else {
                        SecureField("", text: $text, prompt: Text(Strings.shared.required))
                    }
                }
                .textContentType(.password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .onSubmit(onSubmit)
                Button { shown.toggle() } label: {
                    Image(systemName: shown ? "eye.slash" : "eye")
                        .foregroundStyle(.secondary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(shown ? Strings.shared.hidePassword : Strings.shared.showPassword)
            }
        }
    }
}

struct FieldError: View {
    let message: String

    var body: some View {
        Label(message, systemImage: "exclamationmark.circle")
            .font(.footnote)
            .foregroundStyle(Color(.systemRed))
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

func dismissKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}

struct AddressSection<Header: View>: View {
    let label: String
    let placeholder: String
    @Binding var address: String
    var help: String?
    var error: String?
    var disabled = false
    var focusOnAppear = false
    let onSubmit: () -> Void
    @ViewBuilder var header: () -> Header
    @FocusState private var focused: Bool
    @State private var focusedOnce = false

    var body: some View {
        Section {
            FieldRow(label: label) {
                TextField("", text: $address, prompt: Text(placeholder))
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.go)
                    .onSubmit {
                        focused = false
                        onSubmit()
                    }
                    .disabled(disabled)
                    .focused($focused)
                    // Only the first time: a list row appears again as the footer under it changes, and that must not bring the
                    // keyboard back after Go put it away.
                    .onAppear {
                        guard focusOnAppear, !focusedOnce else { return }
                        focusedOnce = true
                        // The push has to settle before the keyboard can come up.
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { focused = true }
                    }
            }
            .listRowInsets(EdgeInsets())
        } header: {
            header()
        } footer: {
            if let error {
                FieldError(message: error)
            } else if let help {
                Text(help)
            }
        }
    }
}

struct SheetScaffold<Leading: View, Content: View>: View {
    let title: String
    var footnote: String?
    var detents: Set<PresentationDetent> = [.medium, .large]
    let onClose: (() -> Void)?
    @ViewBuilder var leading: () -> Leading
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            Color(.secondarySystemBackground).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: Space.s4) {
                    HStack(spacing: Space.s3) {
                        leading()
                        Text(title.titleCased()).sheetTitle()
                        Spacer(minLength: Space.s2)
                        if let onClose {
                            IconSecondaryButton(systemImage: "xmark", label: Strings.shared.close, action: onClose)
                        }
                    }
                    content()
                    if let footnote {
                        Text(footnote)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity)
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(Space.s5)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .presentationDetents(detents)
        .presentationDragIndicator(.visible)
    }
}

extension SheetScaffold where Leading == EmptyView {
    init(
        title: String,
        footnote: String? = nil,
        detents: Set<PresentationDetent> = [.medium, .large],
        onClose: (() -> Void)?,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.init(title: title, footnote: footnote, detents: detents, onClose: onClose, leading: { EmptyView() }, content: content)
    }
}

private struct Toast: ViewModifier {
    let message: String?
    var bottom: CGFloat
    let onDismiss: () -> Void

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let message {
                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Space.s4)
                        .padding(.vertical, Space.s3)
                        .background(.regularMaterial, in: Capsule())
                        .padding(.horizontal, Space.s4)
                        .padding(.bottom, bottom)
                        .allowsHitTesting(false)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .onAppear { UIAccessibility.post(notification: .announcement, argument: message) }
                        .task(id: message) {
                            try? await Task.sleep(nanoseconds: 3_500_000_000)
                            onDismiss()
                        }
                }
            }
            .animation(.default, value: message)
    }
}

extension View {
    func toast(_ message: String?, bottom: CGFloat = Space.s4, onDismiss: @escaping () -> Void) -> some View {
        modifier(Toast(message: message, bottom: bottom, onDismiss: onDismiss))
    }
}

struct SearchingLabel: View {
    var text = Strings.shared.searching

    var body: some View {
        HStack(spacing: Space.s2) {
            ProgressView()
                .progressViewStyle(.circular)
                .scaleEffect(0.8)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

/// Says that a change couldn't be saved, with "Try again", at the bottom of the screen. iOS has no
/// snackbar, so this is a small banner on the system material: it never blocks anything, never takes
/// a tap away from the screen, and goes by itself after a few seconds. [message] nil shows nothing. [actionLabel] is the button's
/// words (Try again unless set); nil hides the button.
private struct FailureBanner: ViewModifier {
    let message: String?
    let actionLabel: String?
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
                        if let actionLabel {
                            Button(actionLabel.titleCased(), action: onRetry)
                                .buttonStyle(PressableButtonStyle())
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.primary)
                        }
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
    func failureBanner(
        _ message: String?,
        actionLabel: String? = Strings.shared.tryAgain,
        onRetry: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) -> some View {
        modifier(FailureBanner(message: message, actionLabel: actionLabel, onRetry: onRetry, onDismiss: onDismiss))
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
