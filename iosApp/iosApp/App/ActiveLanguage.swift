import Foundation
import Observation
import Shared

@Observable
final class ActiveLanguage {
    static let shared = ActiveLanguage()

    private(set) var tag: String = StringCatalogKt.DEFAULT_LANGUAGE

    @ObservationIgnored private let model = LanguageModel()
    @ObservationIgnored private var started = false

    func start() {
        guard !started else { return }
        started = true
        model.onRead { [weak self] in _ = self?.tag }
        model.observe { [weak self] tag in self?.tag = tag }
    }

    func deviceLanguageChanged() {
        model.deviceLanguageChanged()
    }
}
