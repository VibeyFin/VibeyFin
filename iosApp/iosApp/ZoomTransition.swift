import SwiftUI

/// The shared-element transition from a poster to the detail page, with iOS 18's zoom navigation transition. Below iOS 18 these
/// do nothing and the page opens with the standard push, with no custom fallback.
private struct ZoomNamespaceKey: EnvironmentKey {
    static let defaultValue: Namespace.ID? = nil
}

extension EnvironmentValues {
    /// The namespace the tab shell hands down, so posters (sources) and the detail page (destination) share one.
    var zoomNamespace: Namespace.ID? {
        get { self[ZoomNamespaceKey.self] }
        set { self[ZoomNamespaceKey.self] = newValue }
    }
}

/// Marks a poster as where [id]'s page zooms out from, and back to.
private struct ZoomSource: ViewModifier {
    let id: String
    @Environment(\.zoomNamespace) private var namespace

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *), let namespace {
            content.matchedTransitionSource(id: id, in: namespace)
        } else {
            content
        }
    }
}

/// Marks a detail page as the place [id]'s poster zooms into.
private struct ZoomDestination: ViewModifier {
    let id: String
    @Environment(\.zoomNamespace) private var namespace

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *), let namespace {
            content.navigationTransition(.zoom(sourceID: id, in: namespace))
        } else {
            content
        }
    }
}

extension View {
    func zoomSource(_ id: String) -> some View { modifier(ZoomSource(id: id)) }

    func zoomDestination(_ id: String) -> some View { modifier(ZoomDestination(id: id)) }
}
