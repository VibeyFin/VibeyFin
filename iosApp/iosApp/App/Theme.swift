import SwiftUI
import UIKit

// The iOS side of design/tokens.json (the `ios` column). Colours that Apple provides as dark
// semantic colours (background, label, separator, ...) are used by name, never as hex; only the
// brand values live here. scripts/check-swift-tokens.sh fails when this file drifts from the JSON.

/// Brand values with no Apple equivalent. Aqua is the one filled primary action per screen.
enum Brand {
    static let accent = Color(hex: 0x3FE0D0)
    static let onAccent = Color(hex: 0x04201D)
    static let accentSoft = Color(hex: 0x0F2F2C)
    static let onAccentSoft = Color(hex: 0x3FE0D0)

    /// Under any text or control laid over artwork or video.
    static let scrim = Color(hex: 0x000000, alpha: 0.78)
    static let scrimStrong = Color(hex: 0x000000, alpha: 0.9)
    static let progressTrack = Color(hex: 0xFFFFFF, alpha: 0.3)

    /// Profile tile grounds, picked from the user's name; the initial sits on one in `.primary`.
    static let avatar1 = Color(hex: 0x3A4A6B)
    static let avatar2 = Color(hex: 0x4B3F6B)
    static let avatar3 = Color(hex: 0x3F6B5E)
    static let avatar4 = Color(hex: 0x6B4B3F)
    static let avatar5 = Color(hex: 0x6B3F55)
    static let avatar6 = Color(hex: 0x55633F)
    static let avatars = [avatar1, avatar2, avatar3, avatar4, avatar5, avatar6]
}

enum Space {
    static let s1: CGFloat = 4
    static let s2: CGFloat = 8
    static let s3: CGFloat = 12
    static let s4: CGFloat = 16
    static let s5: CGFloat = 24
    static let s6: CGFloat = 32
    static let s7: CGFloat = 48
}

enum Radius {
    /// Posters, thumbnails and the hero card. Use `.continuous` corners.
    static let artwork: CGFloat = 8
    /// Grouped containers; nested corners stay concentric.
    static let card: CGFloat = 16
}

enum Size {
    /// A Continue watching thumbnail on phones (16:9).
    static let thumbWidth: CGFloat = 224
}

extension Font {
    /// `brand-display`: the only brand face. For the wordmark, a hero title without a Logo image and
    /// onboarding; everything else uses Dynamic Type text styles. Scales with `.largeTitle`. Apply
    /// `.tracking(-0.8)` at the call site (-0.02em at 40 pt).
    static let brandDisplay = Font.custom("BricolageGrotesque-96ptExtraBold", size: 40, relativeTo: .largeTitle)

}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

// MARK: - Layout and text

extension Space {
    /// iPad screen margin. Phones use `s4`.
    static let regularMargin: CGFloat = 20
}

private struct ScreenMargin: ViewModifier {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.screenMarginOverride) private var override

    func body(content: Content) -> some View {
        content.padding(.horizontal, override ?? (sizeClass == .regular ? Space.regularMargin : Space.s4))
    }
}

private struct ScreenMarginOverrideKey: EnvironmentKey {
    static let defaultValue: CGFloat? = nil
}

extension EnvironmentValues {
    /// A page that wants a different side margin than the screen's default (the item page's wide layout uses 32 pt) sets it here, and
    /// every `screenMargin()` below it follows.
    var screenMarginOverride: CGFloat? {
        get { self[ScreenMarginOverrideKey.self] }
        set { self[ScreenMarginOverrideKey.self] = newValue }
    }
}

extension View {
    /// The horizontal screen margin: 16 pt on phones, 20 pt on iPad.
    func screenMargin() -> some View {
        modifier(ScreenMargin())
    }

    /// The name this page has in the system back button's long-press menu, which lists the pages behind it. The bar itself stays
    /// clear: the page draws its own title, so an empty principal item stands in for the one the system would show.
    func backMenuTitle(_ title: String) -> some View {
        self
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .principal) { Color.clear.frame(width: 1, height: 1).accessibilityHidden(true) } }
    }
}

extension Text {
    /// A screen's own title, drawn in the content (tab roots and pushed screens that don't use the
    /// navigation bar title). Dynamic Type `.largeTitle`, bold.
    func screenTitle() -> some View {
        self.font(.largeTitle.bold()).foregroundStyle(.primary)
    }

    /// A sheet's title: Dynamic Type `.title2`, bold.
    func sheetTitle() -> some View {
        self.font(.title2.bold()).foregroundStyle(.primary)
    }

    /// A section heading above a row or grid: Dynamic Type `.title3`, bold.
    func sectionTitle() -> some View {
        self.font(.title3.bold()).foregroundStyle(.primary)
    }
}
