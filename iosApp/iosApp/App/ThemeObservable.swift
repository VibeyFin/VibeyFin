import SwiftUI
import Shared

/// The signed-in profile's theme id. `BrandPalette.active` is set before the id is published, so the rebuilt tree reads the new colours.
final class ThemeObservable: ObservableObject {
    @Published private(set) var themeId: String

    private let model = ThemeModel()
    private var started = false

    init() {
        themeId = BrandPalette.defaultId
    }

    func start() {
        guard !started else { return }
        started = true
        model.observe { [weak self] id in
            BrandPalette.active = BrandPalette.byId(id)
            self?.themeId = id
        }
    }

    deinit {
        model.close()
    }
}
