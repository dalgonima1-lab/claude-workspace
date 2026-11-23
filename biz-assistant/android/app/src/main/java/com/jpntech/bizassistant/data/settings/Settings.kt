package com.jpntech.bizassistant.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

const val DEFAULT_SERVER_URL = "https://claude-workspace-relay-server.vercel.app"
val ENTITIES = listOf("제이피앤텍", "서일이앤씨")

data class AppSettings(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val appToken: String = "",
    val defaultEntity: String = ENTITIES.first(),
    val calendarId: Long = -1L,
    val remindersEnabled: Boolean = true,
    val sensitiveKeywords: String = "",
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val appToken = stringPreferencesKey("app_token")
        val defaultEntity = stringPreferencesKey("default_entity")
        val calendarId = longPreferencesKey("calendar_id")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val sensitiveKeywords = stringPreferencesKey("sensitive_keywords")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            serverUrl = p[Keys.serverUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL,
            appToken = p[Keys.appToken] ?: "",
            defaultEntity = p[Keys.defaultEntity] ?: ENTITIES.first(),
            calendarId = p[Keys.calendarId] ?: -1L,
            remindersEnabled = p[Keys.remindersEnabled] ?: true,
            sensitiveKeywords = p[Keys.sensitiveKeywords] ?: "",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        val next = block(current())
        context.dataStore.edit { p ->
            p[Keys.serverUrl] = next.serverUrl
            p[Keys.appToken] = next.appToken
            p[Keys.defaultEntity] = next.defaultEntity
            p[Keys.calendarId] = next.calendarId
            p[Keys.remindersEnabled] = next.remindersEnabled
            p[Keys.sensitiveKeywords] = next.sensitiveKeywords
        }
    }
}
