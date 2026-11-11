import SwiftUI
import UIKit

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
    /// Scales with `.largeTitle`; apply `.tracking(-0.4)` at the call site.
    static let brandDisplay = Font.custom("ArchivoCondensed-ExtraBold", size: 40, relativeTo: .largeTitle)
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
