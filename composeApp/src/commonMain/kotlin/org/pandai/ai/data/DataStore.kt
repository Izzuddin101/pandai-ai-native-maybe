package org.pandai.ai.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import org.koin.core.annotation.Single

expect fun producePath(): String

fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() }
    )

private object PreferencesKeys {
    val ACCESS_TOKEN = stringPreferencesKey("access_token")
}

@Single
class PandaiDataStore {
    private val dataStore = createDataStore(::producePath)

    suspend fun getToken(): String? {
        return dataStore.data.first()[PreferencesKeys.ACCESS_TOKEN]
    }

    suspend fun setToken(token: String) {
        dataStore.edit {
            it[PreferencesKeys.ACCESS_TOKEN] = token
        }

    }
}