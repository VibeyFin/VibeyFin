import Foundation
import Observation
import Shared

/// The language tag the app shows. Reading `tag` while a view draws makes SwiftUI draw that view again when the language changes.
@Observable
final class ActiveLanguage {
    static let shared = ActiveLanguage()

    private(set) var tag: String = "en"

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
