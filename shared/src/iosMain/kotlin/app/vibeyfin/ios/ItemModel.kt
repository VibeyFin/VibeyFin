package app.vibeyfin.ios

import app.vibeyfin.ui.item.ItemUiState
import app.vibeyfin.ui.item.ItemViewModel
import org.koin.core.parameter.parametersOf

/** The item [itemId]'s metadata, as SwiftUI needs it. */
class ItemModel(itemId: String) {
    private val host = ViewModelHost(ItemViewModel::class) {
        it.get<ItemViewModel> { parametersOf(itemId) }
    }

    fun observe(onChange: (ItemUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onRetry() = host.viewModel.onRetry()

    fun onTogglePlayed() = host.viewModel.onTogglePlayed()

    fun onToggleFavorite() = host.viewModel.onToggleFavorite()

    fun onSelectSeason(seasonId: String) = host.viewModel.onSelectSeason(seasonId)

    fun onToggleEpisodePlayed(episodeId: String) = host.viewModel.onToggleEpisodePlayed(episodeId)

    fun onRetrySimilar() = host.viewModel.onRetrySimilar()

    fun onRetryCollections() = host.viewModel.onRetryCollections()

    fun onRetryExtras() = host.viewModel.onRetryExtras()

    fun onRetrySeasons() = host.viewModel.onRetrySeasons()

    fun onRetryEpisodes() = host.viewModel.onRetryEpisodes()

    fun onRetryAction() = host.viewModel.onRetryAction()

    fun onDismissMessage() = host.viewModel.onDismissMessage()

    fun close() = host.close()
}
