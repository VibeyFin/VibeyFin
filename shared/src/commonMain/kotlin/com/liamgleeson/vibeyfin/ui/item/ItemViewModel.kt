package com.liamgleeson.vibeyfin.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liamgleeson.vibeyfin.data.api.ApiResult
import com.liamgleeson.vibeyfin.domain.ConnectionManager
import com.liamgleeson.vibeyfin.domain.LibraryRepository
import com.liamgleeson.vibeyfin.domain.WatchChanges
import com.liamgleeson.vibeyfin.domain.model.MediaItem
import com.liamgleeson.vibeyfin.ui.common.LoadMessages
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.common.sessionEnded
import com.liamgleeson.vibeyfin.ui.home.HomeExit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
) {
    /** Whether the page offers Play. */
    val canPlay: Boolean get() = item?.canPlay == true

    /** "Resume from 41:12" when the server says the user stopped part way, else null. */
    private val resumeLabel: String?
        get() = item?.takeIf { it.canPlay && !it.played && it.resumePositionMs > 0 }
            ?.let { Strings.resumeFrom(Strings.clock(it.resumePositionMs)) }

    /** The primary button: "Resume from 41:12", or "Play". */
    val playLabel: String get() = resumeLabel ?: Strings.play

    /** Whether "Start over" is offered beside the primary button: only when there is something to resume. */
    val canStartOver: Boolean get() = resumeLabel != null
}

/** One item's metadata. */
class ItemViewModel(
    private val itemId: String,
    private val library: LibraryRepository,
    connection: ConnectionManager,
    changes: WatchChanges,
) : ViewModel() {
    private val content = MutableStateFlow(ItemUiState())

    val uiState: StateFlow<ItemUiState> = combine(content, connection.sessionEnded()) { content, exit ->
        content.copy(exit = exit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemUiState())

    init {
        load()
        // What was watched changed (playback ended): bring the Resume button up to date, quietly.
        viewModelScope.launch { changes.events.collect { refresh() } }
    }

    fun onRetry() = load()

    /** Reads the item again without the spinner; a failure leaves the page as it was. */
    private suspend fun refresh() {
        if (content.value.item == null) return
        val result = library.item(itemId) as? ApiResult.Success ?: return
        content.update { it.copy(item = result.value, facts = result.value.facts()) }
    }

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
