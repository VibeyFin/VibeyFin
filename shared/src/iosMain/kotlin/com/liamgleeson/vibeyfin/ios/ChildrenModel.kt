package com.liamgleeson.vibeyfin.ios

import com.liamgleeson.vibeyfin.ui.children.ChildrenUiState
import com.liamgleeson.vibeyfin.ui.children.ChildrenViewModel
import org.koin.core.parameter.parametersOf

/** What is inside the library, folder, series or season [parentId], as SwiftUI needs it. */
class ChildrenModel(parentId: String) {
    private val host = ViewModelHost(ChildrenViewModel::class) {
        it.get<ChildrenViewModel> { parametersOf(parentId) }
    }

    fun observe(onChange: (ChildrenUiState) -> Unit) = host.observe(host.viewModel.uiState, onChange)

    fun onLoadMore() = host.viewModel.onLoadMore()

    fun onRetry() = host.viewModel.onRetry()

    fun close() = host.close()
}
