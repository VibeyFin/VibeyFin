import SwiftUI
import Shared

/// The one primary action on a screen: amber and full width.
struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        PrimaryButtonBody(configuration: configuration)
    }

    private struct PrimaryButtonBody: View {
        let configuration: ButtonStyleConfiguration
        @Environment(\.isEnabled) private var isEnabled

        var body: some View {
            configuration.label
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(isEnabled ? Palette.onMarquee : Palette.textMuted)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(Capsule().fill(isEnabled ? Palette.marquee : Palette.surfaceHigh))
                .opacity(configuration.isPressed ? 0.85 : 1)
        }
    }
}

/// A quieter full-width action for screens that already have their own focus.
struct OutlineButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(Palette.textMuted)
            .frame(maxWidth: .infinity, minHeight: 52)
            .overlay(Capsule().stroke(Palette.surfaceHigh, lineWidth: 1))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// A rounded surface that holds a server or a message.
struct Card<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Palette.surface))
    }
}

struct SearchingLabel: View {
    var body: some View {
        HStack(spacing: 8) {
            ProgressView()
                .progressViewStyle(.circular)
                .tint(Palette.textMuted)
                .scaleEffect(0.8)
            Text(Strings.shared.searching)
                .font(.subheadline)
                .foregroundColor(Palette.textMuted)
        }
    }
}
