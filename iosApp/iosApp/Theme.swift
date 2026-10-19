import SwiftUI

/// The VibeyFin palette. Dark blue-ink base; amber is reserved for the single primary action on a
/// screen (Play, Sign in) and teal only for availability. Artwork carries the colour elsewhere.
enum Palette {
    static let ink = Color(hex: 0x10121A)
    static let surface = Color(hex: 0x1A1D29)
    static let surfaceHigh = Color(hex: 0x262A3A)
    static let text = Color(hex: 0xECEAE4)
    static let textMuted = Color(hex: 0x8E92A3)

    /// Amber. Primary action only.
    static let marquee = Color(hex: 0xE8B04B)
    static let onMarquee = Color(hex: 0x1B1405)

    /// Teal. Availability only.
    static let available = Color(hex: 0x5CC8B0)

    /// What went wrong. The palette defines no red, so this matches the Android app's error tone.
    static let error = Color(hex: 0xF2B8B5)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

extension Text {
    /// Heavy, tight type for titles; body text keeps the system defaults.
    func vibeTitle(size: CGFloat, weight: Font.Weight = .heavy, tracking: CGFloat = -0.8) -> Text {
        self.font(.system(size: size, weight: weight))
            .tracking(tracking)
            .foregroundColor(Palette.text)
    }
}
