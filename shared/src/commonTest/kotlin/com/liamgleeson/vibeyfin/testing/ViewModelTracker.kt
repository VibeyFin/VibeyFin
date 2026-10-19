package com.liamgleeson.vibeyfin.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job

/**
 * Stops the ViewModels a test created and waits for them to finish. A ViewModel that is only left
 * running keeps working after the test, fails once the database is closed, and the failure lands in
 * whichever test runs next.
 */
class ViewModelTracker {
    private val created = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T {
        created += viewModel
        return viewModel
    }

    suspend fun stopAll() {
        created.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
        created.clear()
    }
}
