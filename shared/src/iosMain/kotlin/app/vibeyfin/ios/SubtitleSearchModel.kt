package app.vibeyfin.ios

import app.vibeyfin.ui.subtitles.SubtitleSearchUiState
import app.vibeyfin.ui.subtitles.SubtitleSearchViewModel

/** The "Search for subtitles" sheet of a screen, as SwiftUI needs it. */
class SubtitleSearchModel {
    private val host = ViewModelHost(SubtitleSearchViewModel::class) { it.get<SubtitleSearchViewModel>() }

    fun observe(onChange: (SubtitleSearchUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onOpen(itemId: String) = host.viewModel.onOpen(itemId)

    fun onSelectLanguage(code: String) = host.viewModel.onSelectLanguage(code)

    fun onSearchLanguages(query: String) = host.viewModel.onSearchLanguages(query)

    fun onChoose(id: String) = host.viewModel.onChoose(id)

    fun onRetry() = host.viewModel.onRetry()

    fun onDismissMessage() = host.viewModel.onDismissMessage()

    fun onAddedHandled() = host.viewModel.onAddedHandled()

    fun close() = host.close()
}
