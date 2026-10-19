import SwiftUI
import Shared

/// The one filled primary action on a screen: an aqua capsule, full width. Native `.borderedProminent`
/// so the system supplies press, disabled and Liquid Glass-era behaviour; the label sits on `onAccent`.
struct PrimaryButton<Label: View>: View {
    let action: () -> Void
    @ViewBuilder let label: () -> Label

    var body: some View {
        Button(action: action) {
            label()
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .controlSize(.large)
        .tint(Brand.accent)
        .foregroundStyle(Brand.onAccent)
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
