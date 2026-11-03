import SwiftUI
import UIKit
import Shared

/// Settings › Quick Connect: type the code another device shows to sign it in as this profile. Its own view, not a shared
/// `SettingsPage`, because it has a text field; the shared `ApproveQuickConnectViewModel` decides what it says.
struct ApproveQuickConnectView: View {
    @StateObject private var model = ApproveQuickConnectObservable()
    @FocusState private var focused: Bool

    var body: some View {
        Group {
            if let state = model.state {
                List { sections(state) }
                    .listStyle(.insetGrouped)
                    .scrollDismissesKeyboard(.interactively)
            } else {
                ZStack {
                    Color(.systemGroupedBackground).ignoresSafeArea()
                    ProgressView().accessibilityLabel(Strings.shared.loadingPage)
                }
            }
        }
        .navigationTitle(Strings.shared.quickConnect.titleCased())
        .navigationBarTitleDisplayMode(.inline)
        .toolbarRole(.editor)
        .onAppear { model.start() }
    }

    @ViewBuilder
    private func sections(_ state: ApproveQuickConnectUiState) -> some View {
        let code = Binding<String>(
            get: { model.state?.code ?? "" },
            set: { model.setCode($0) }
        )
        Section {
            FieldRow(label: Strings.shared.quickConnectCodeLabel) {
                TextField("", text: code, prompt: Text(Strings.shared.quickConnectCodePlaceholder))
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .monospacedDigit()
                    .disabled(state.approving)
                    .focused($focused)
            }
            .listRowInsets(EdgeInsets())
        } header: {
            Text(Strings.shared.quickConnectApproveLead(name: state.profileName))
                .font(.body)
                .foregroundStyle(.secondary)
                .textCase(nil)
                .padding(.bottom, Space.s2)
        } footer: {
            if let error = state.error {
                FieldError(message: error)
            } else if let done = state.done {
                // A check and the words in the text colour: aqua never says how something went.
                Label(done, systemImage: "checkmark")
                    .font(.footnote)
                    .foregroundStyle(.primary)
                    .onAppear { UIAccessibility.post(notification: .announcement, argument: done) }
            }
        }
        Section {
            // The page's one primary action; the number pad has no return key, so this is the way to send the code.
            PrimaryButton(Strings.shared.quickConnectApprove, busyTitle: Strings.shared.quickConnectApproving, busy: state.approving) {
                focused = false
                model.approve()
            }
            .disabled(!state.canApprove && !state.approving)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
    }
}
