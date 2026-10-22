import SwiftUI

/// The app's mark: the play triangle of the app icon, on its own, for the top of Home. Drawn here so it
/// takes the text colour and scales with Dynamic Type; when the icon is redesigned this follows it.
struct AppLogo: View {
    var body: some View {
        LogoTriangle()
            .fill(.primary)
            .frame(width: 32, height: 32)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Strings.shared.appName)
    }
}

private struct LogoTriangle: Shape {
    func path(in rect: CGRect) -> Path {
        // The same proportions as the Android vector: a 24-unit square with the triangle from x 5 to 19.
        let unit = rect.width / 24
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 5 * unit, y: rect.minY + 3.5 * unit))
        path.addLine(to: CGPoint(x: rect.minX + 19 * unit, y: rect.minY + 12 * unit))
        path.addLine(to: CGPoint(x: rect.minX + 5 * unit, y: rect.minY + 20.5 * unit))
        path.closeSubpath()
        return path
    }
}
