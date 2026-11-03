package app.vibeyfin.app.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Where Home's ViewModel lives for a signed-in session, outside Home's own page: a new one is started as soon as a profile signs in,
 * so Home loads while "Who's watching?" zooms, and Home's page picks up the same one. Kept by the activity, so it outlives a rotation.
 */
class HomeSession : ViewModel() {
    var owner: ViewModelStoreOwner by mutableStateOf(newOwner())
        private set

    /** A profile signed in: the last session's Home is dropped and a fresh one is made from [owner]. */
    fun renew() {
        owner.viewModelStore.clear()
        owner = newOwner()
    }

    override fun onCleared() = owner.viewModelStore.clear()

    private fun newOwner() = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
