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
    /** 통화 녹음을 자동으로 글자로 바꿔 정리할지 */
    val autoProcess: Boolean = false,
    /** 자동 처리 결과를 확인 없이 바로 등록할지 */
    val autoRegister: Boolean = false,
    /** 이 시각 이후에 생긴 녹음만 처리한다 (켠 시점) */
    val autoSince: Long = 0L,
    /** 서버에서 마지막으로 받은 변경 커서 */
    val syncCursor: Long = 0L,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val appToken = stringPreferencesKey("app_token")
        val defaultEntity = stringPreferencesKey("default_entity")
        val calendarId = longPreferencesKey("calendar_id")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val sensitiveKeywords = stringPreferencesKey("sensitive_keywords")
        val autoProcess = booleanPreferencesKey("auto_process")
        val autoRegister = booleanPreferencesKey("auto_register")
        val autoSince = longPreferencesKey("auto_since")
        val syncCursor = longPreferencesKey("sync_cursor")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            serverUrl = p[Keys.serverUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL,
            appToken = p[Keys.appToken] ?: "",
            defaultEntity = p[Keys.defaultEntity] ?: ENTITIES.first(),
            calendarId = p[Keys.calendarId] ?: -1L,
            remindersEnabled = p[Keys.remindersEnabled] ?: true,
            sensitiveKeywords = p[Keys.sensitiveKeywords] ?: "",
            autoProcess = p[Keys.autoProcess] ?: false,
            autoRegister = p[Keys.autoRegister] ?: false,
            autoSince = p[Keys.autoSince] ?: 0L,
            syncCursor = p[Keys.syncCursor] ?: 0L,
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
            p[Keys.autoProcess] = next.autoProcess
            p[Keys.autoRegister] = next.autoRegister
            p[Keys.autoSince] = next.autoSince
            p[Keys.syncCursor] = next.syncCursor
        }
    }
}
