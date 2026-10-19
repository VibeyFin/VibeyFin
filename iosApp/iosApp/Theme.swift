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

// MARK: - Previous palette

/// The previous palette, mapped onto the new design so screens keep compiling until Phase 5 moves
/// them to Apple's semantic colours and `Brand`. Teal is gone from the design, so `available` is the
/// label colour. Removed in Phase 5.
enum Palette {
    static let ink = Color(.systemBackground)
    static let surface = Color(.secondarySystemBackground)
    static let surfaceHigh = Color(.systemGray4)
    static let text = Color(.label)
    static let textMuted = Color(.secondaryLabel)
    static let marquee = Brand.accent
    static let onMarquee = Brand.onAccent
    static let available = Color(.label)
    static let error = Color(.systemRed)
}

extension Text {
    /// Previous heavy title style; Phase 5 replaces it with Dynamic Type styles.
    func vibeTitle(size: CGFloat, weight: Font.Weight = .heavy, tracking: CGFloat = -0.8) -> Text {
        self.font(.system(size: size, weight: weight))
            .tracking(tracking)
            .foregroundColor(Palette.text)
    }
}
