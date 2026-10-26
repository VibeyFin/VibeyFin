import SwiftUI
import Shared
import UIKit

/// The PIN prompt as a sheet: four dots and a numeric keypad, the lock countdown after too many wrong PINs, and "Forgot PIN?", which
/// swaps the keypad for the profile's Jellyfin password. Every text comes from the shared `PinPrompt`; the sheet draws it and forwards events.
/// A PIN is sent as soon as the fourth digit is in. Choosing a PIN is the same sheet: the prompt says whether it is the first or second entry.
struct PinSheet: View {
    let prompt: PinPrompt
    let onSubmitPin: (String) -> Void
    let onForgotPin: () -> Void
    let onSubmitPassword: (String) -> Void
    let onDismiss: () -> Void

    @State private var digits = ""
    @State private var password = ""
    /// Bumped on every PIN sent, so a wrong PIN is announced each time even when its text is the same as the last one's.
    @State private var submitted = 0
    /// The prompt's error and lock as the sheet last saw them, for the announcement task to read after it waits.
    @State private var shownError: String?
    @State private var shownLocked = false

    private var choosing: Bool { prompt.reason == .choosepin }
    private var locked: Bool { prompt.lockedSeconds != nil }

    var body: some View {
        ZStack {
            Color(.secondarySystemBackground).ignoresSafeArea()
            VStack(spacing: Space.s4) {
                header
                if prompt.forgot {
                    forgotBody
                } else {
                    pinBody
                }
            }
            .padding(Space.s5)
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onChange(of: prompt.error) { shownError = $0 }
        // A wrong PIN is announced after every attempt (the prompt does not change when the text repeats, so each attempt restarts this
        // task). During the lock the countdown is not read out every second: one announcement when it starts.
        .task(id: submitted) {
            guard submitted > 0 else { return }
            try? await Task.sleep(nanoseconds: 600_000_000)
            if !shownLocked, let error = shownError {
                UIAccessibility.post(notification: .announcement, argument: error)
            }
        }
        .onChange(of: prompt.lockedSeconds != nil) { locked in
            shownLocked = locked
            // The keys come back when the wait is over; whatever was typed during it is dropped.
            if locked {
                digits = ""
                if let seconds = prompt.lockedSeconds {
                    UIAccessibility.post(notification: .announcement, argument: Strings.shared.pinPromptLocked(seconds: seconds.int32Value))
                }
            }
        }
        .onDisappear { password = "" }
    }

    private var title: String {
        if prompt.forgot { return Strings.shared.pinForgot }
        if choosing { return prompt.message }
        return Strings.shared.pinPromptEnter
    }

    private var header: some View {
        VStack(spacing: Space.s2) {
            Text(title.titleCased()).sheetTitle().multilineTextAlignment(.center)
            // Choosing shows its prompt as the title; the other reasons say why under it.
            if !choosing {
                Text(prompt.message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    // MARK: - Keypad

    private var pinBody: some View {
        VStack(spacing: Space.s5) {
            PinDotsView(filled: digits.count)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Strings.shared.pinDigitsEntered(count: Int32(digits.count)))
            status
            keypad
            if !choosing {
                GhostButton(title: Strings.shared.pinForgot, action: onForgotPin)
                    .frame(minHeight: 44)
            }
            GhostButton(title: Strings.shared.cancel, action: onDismiss)
                .frame(minHeight: 44)
            Spacer(minLength: 0)
        }
    }

    /// The wait after too many wrong PINs, else what was wrong. It keeps its height so the keypad doesn't jump.
    private var status: some View {
        let text: String = {
            if let seconds = prompt.lockedSeconds { return Strings.shared.pinPromptLocked(seconds: seconds.int32Value) }
            return prompt.error ?? ""
        }()
        return Text(text)
            .font(.subheadline)
            .foregroundStyle(Color(.systemRed))
            .multilineTextAlignment(.center)
            .frame(minHeight: 40, alignment: .top)
    }

    private var keypad: some View {
        let rows = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"]]
        return VStack(spacing: Space.s3) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: Space.s4) {
                    ForEach(row, id: \.self) { key in
                        PinKey(label: key) { type(key) }
                    }
                }
            }
            HStack(spacing: Space.s4) {
                Color.clear.frame(width: PinKey.size, height: PinKey.size)
                PinKey(label: "0") { type("0") }
                Button { if !digits.isEmpty { digits.removeLast() } } label: {
                    Image(systemName: "delete.left")
                        .font(.title2)
                        .foregroundStyle(.primary)
                        .frame(width: PinKey.size, height: PinKey.size)
                        .contentShape(Circle())
                }
                .accessibilityLabel(Strings.shared.pinKeypadDelete)
                .accessibilityIdentifier("pin.delete")
            }
        }
        .disabled(locked)
        .opacity(locked ? 0.4 : 1)
    }

    private func type(_ digit: String) {
        guard !locked, digits.count < 4 else { return }
        digits += digit
        if digits.count == 4 {
            let pin = digits
            digits = ""
            submitted += 1
            onSubmitPin(pin)
        }
    }

    // MARK: - Forgot PIN

    private var forgotBody: some View {
        VStack(spacing: Space.s4) {
            FormField(label: Strings.shared.password, isError: prompt.error != nil) {
                SecureField(Strings.shared.password, text: $password)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.go)
                    .onSubmit { submitPassword() }
            }
            if let error = prompt.error {
                Text(error)
                    .font(.subheadline)
                    .foregroundStyle(Color(.systemRed))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            PrimaryButton(action: { submitPassword() }) {
                HStack(spacing: Space.s3) {
                    if prompt.checking { PrimaryButtonSpinner() }
                    Text(Strings.shared.pinForgotSubmit.titleCased())
                }
            }
            .disabled(password.isEmpty && !prompt.checking)
            GhostButton(title: Strings.shared.cancel, action: onDismiss)
                .frame(minHeight: 44)
            Spacer(minLength: 0)
        }
    }

    private func submitPassword() {
        guard !prompt.checking, !password.isEmpty else { return }
        onSubmitPassword(password)
    }
}

/// Four dots, filled as digits go in. They are ink, not aqua: aqua is for the one primary action.
struct PinDotsView: View {
    let filled: Int

    var body: some View {
        HStack(spacing: Space.s4) {
            ForEach(0..<4, id: \.self) { index in
                Circle()
                    .fill(index < filled ? Color.primary : Color.clear)
                    .overlay(Circle().stroke(Color.primary, lineWidth: 1.5))
                    .frame(width: 16, height: 16)
            }
        }
    }
}

/// One digit key. 72 pt, well over the 44 pt minimum.
private struct PinKey: View {
    static let size: CGFloat = 72
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.title.weight(.medium))
                .foregroundStyle(.primary)
                .frame(width: Self.size, height: Self.size)
                .background(Circle().fill(Color(.tertiarySystemFill)))
                .contentShape(Circle())
        }
        .buttonStyle(PressableButtonStyle())
        .accessibilityLabel(label)
        .accessibilityIdentifier("pin.key.\(label)")
    }
}
