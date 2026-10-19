package com.liamgleeson.vibeyfin.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.LibraryRepository
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.common.LoadMessages
import com.liamgleeson.vibeyfin.ui.common.sessionEnded
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ItemUiState(
    val item: MediaItem? = null,
    /** The short facts under the title: "S1 E3", year, age rating, runtime, rating. */
    val facts: List<String> = emptyList(),
    val loading: Boolean = true,
    /** Why the item didn't load, with what to try. */
    val message: String? = null,
    /** Set when the session ended and the screen must be left. */
    val exit: HomeExit? = null,
)

/** One item's metadata. */
class ItemViewModel(
    private val itemId: String,
    private val library: LibraryRepository,
    connection: ConnectionManager,
) : ViewModel() {
    private val content = MutableStateFlow(ItemUiState())

    val uiState: StateFlow<ItemUiState> = combine(content, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemUiState())

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        content.value = ItemUiState(loading = true)
        viewModelScope.launch {
            content.value = when (val result = library.item(itemId)) {
                is ApiResult.Success -> ItemUiState(item = result.value, facts = result.value.facts(), loading = false)
                is ApiResult.Failure -> ItemUiState(loading = false, message = LoadMessages.forError(result.error))
            }
        }
    }
}
