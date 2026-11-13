package app.vibeyfin.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

fun createDevicePreferencesDataStore(path: String): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

class DataStoreDevicePreferences(
    private val dataStore: DataStore<Preferences>,
    private val newDeviceId: () -> String = { Uuid.random().toString() },
) : DevicePreferences {

    override suspend fun deviceId(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }
        // edit() runs one at a time, so two callers racing here still end up with one ID.
        return dataStore.edit { it[DEVICE_ID] = it[DEVICE_ID] ?: newDeviceId() }[DEVICE_ID]!!
    }

    override val lastServerId: Flow<String?> = dataStore.data.map { it[LAST_SERVER_ID] }

    override val lastProfile: Flow<ProfileRef?> = dataStore.data.map { prefs ->
        val serverId = prefs[LAST_SERVER_ID]
        val userId = prefs[LAST_USER_ID]
        if (serverId != null && userId != null) ProfileRef(serverId, userId) else null
    }

    override val alwaysAskWhoIsWatching: Flow<Boolean> = dataStore.data.map { it[ALWAYS_ASK] ?: false }

    override val offlineMode: Flow<Boolean> = dataStore.data.map { it[OFFLINE_MODE] ?: false }

    override val appLanguage: Flow<String?> = dataStore.data.map { it[APP_LANGUAGE] }

    override suspend fun setLastServer(serverId: String?) {
        dataStore.edit { prefs ->
            if (prefs[LAST_SERVER_ID] != serverId) prefs.remove(LAST_USER_ID)
            if (serverId == null) prefs.remove(LAST_SERVER_ID) else prefs[LAST_SERVER_ID] = serverId
        }
    }

    override suspend fun setLastProfile(profile: ProfileRef) {
        dataStore.edit { prefs ->
            prefs[LAST_SERVER_ID] = profile.serverId
            prefs[LAST_USER_ID] = profile.userId
        }
    }

    override suspend fun addedSubtitles(serverId: String, itemId: String): Set<String> = dataStore.data.first()[addedSubtitlesKey(serverId, itemId)].orEmpty()

    override suspend fun setSubtitleAdded(serverId: String, itemId: String, subtitleId: String) {
        val key = addedSubtitlesKey(serverId, itemId)
        dataStore.edit { it[key] = it[key].orEmpty() + subtitleId }
    }

    override suspend fun setAlwaysAskWhoIsWatching(value: Boolean) {
        dataStore.edit { it[ALWAYS_ASK] = value }
    }

    override suspend fun setOfflineMode(value: Boolean) {
        dataStore.edit { it[OFFLINE_MODE] = value }
    }

    override suspend fun setAppLanguage(tag: String?) {
        dataStore.edit { prefs -> if (tag == null) prefs.remove(APP_LANGUAGE) else prefs[APP_LANGUAGE] = tag }
    }

    private fun addedSubtitlesKey(serverId: String, itemId: String) = stringSetPreferencesKey("added_subtitles.$serverId.$itemId")

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LAST_SERVER_ID = stringPreferencesKey("last_server_id")
        val LAST_USER_ID = stringPreferencesKey("last_user_id")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val ALWAYS_ASK = booleanPreferencesKey("always_ask_who_is_watching")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }
}
