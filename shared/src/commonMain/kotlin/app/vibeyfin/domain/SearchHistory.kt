package app.vibeyfin.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What the signed-in profile searched for, newest first, kept on this device only: each profile has its own list, and
 * nothing is sent to the server. At most [LIMIT] terms; searching a term again moves it to the front instead of adding it twice.
 */
class SearchHistory(
    private val dataStore: DataStore<Preferences>,
    private val connection: ConnectionManager,
) {
    /** The terms of the profile signed in now; empty when nobody is. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: Flow<List<String>> = connection.state
        .map { (it as? ConnectionState.Connected)?.account?.let { a -> keyFor(a.serverId, a.userId) } }
        .distinctUntilChanged()
        .flatMapLatest { key -> if (key == null) flowOf(emptyList()) else dataStore.data.map { decode(it[key]) } }

    /** Puts [term] first. A blank term is ignored, as is a call when nobody is signed in. */
    suspend fun record(term: String) {
        val clean = term.trim().replace('\n', ' ')
        if (clean.isEmpty()) return
        val key = currentKey() ?: return
        dataStore.edit { prefs ->
            val kept = decode(prefs[key]).filterNot { it.equals(clean, ignoreCase = true) }
            prefs[key] = encode((listOf(clean) + kept).take(LIMIT))
        }
    }

    suspend fun remove(term: String) {
        val key = currentKey() ?: return
        dataStore.edit { prefs -> prefs[key] = encode(decode(prefs[key]).filterNot { it == term }) }
    }

    suspend fun clear() {
        val key = currentKey() ?: return
        dataStore.edit { it.remove(key) }
    }

    suspend fun clear(serverId: String, userId: String) {
        dataStore.edit { it.remove(keyFor(serverId, userId)) }
    }

    private fun currentKey() = (connection.state.value as? ConnectionState.Connected)?.account?.let { keyFor(it.serverId, it.userId) }

    private fun keyFor(serverId: String, userId: String) = stringPreferencesKey("search_history:$serverId:$userId")

    private fun decode(stored: String?): List<String> = stored?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private fun encode(terms: List<String>) = terms.joinToString("\n")

    companion object {
        const val LIMIT = 10
    }
}
