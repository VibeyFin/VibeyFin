package com.liamgleeson.vibeyfin.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.liamgleeson.vibeyfin.domain.DevicePreferences
import com.liamgleeson.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

fun createDevicePreferencesDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

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

    override suspend fun setAlwaysAskWhoIsWatching(value: Boolean) {
        dataStore.edit { it[ALWAYS_ASK] = value }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LAST_SERVER_ID = stringPreferencesKey("last_server_id")
        val LAST_USER_ID = stringPreferencesKey("last_user_id")
        val ALWAYS_ASK = booleanPreferencesKey("always_ask_who_is_watching")
    }
}
