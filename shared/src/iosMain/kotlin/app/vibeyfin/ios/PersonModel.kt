package app.vibeyfin.ios

import app.vibeyfin.ui.person.PersonUiState
import app.vibeyfin.ui.person.PersonViewModel
import org.koin.core.parameter.parametersOf

/** A cast member's page, as SwiftUI needs it. */
class PersonModel(personId: String) {
    private val host = ViewModelHost(PersonViewModel::class) {
        it.get<PersonViewModel> { parametersOf(personId) }
    }

    fun observe(onChange: (PersonUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onRetry() = host.viewModel.onRetry()

    fun close() = host.close()
}
