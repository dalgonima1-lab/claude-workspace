package com.jpntech.bizassistant

import android.app.Application
import com.jpntech.bizassistant.calendar.CalendarWriter
import com.jpntech.bizassistant.data.Repository
import com.jpntech.bizassistant.data.db.AppDatabase
import com.jpntech.bizassistant.data.remote.RelayApi
import com.jpntech.bizassistant.data.settings.SettingsStore
import com.jpntech.bizassistant.data.sync.LegacyImporter
import com.jpntech.bizassistant.data.sync.SyncEngine
import com.jpntech.bizassistant.data.sync.SyncWorker
import com.jpntech.bizassistant.recordings.RecordingWatcher
import com.jpntech.bizassistant.reminders.ReminderScheduler
import com.jpntech.bizassistant.reminders.ensureNotificationChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BizApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: Repository
        private set
    lateinit var calendar: CalendarWriter
        private set
    lateinit var sync: SyncEngine
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        calendar = CalendarWriter(this)
        val api = RelayApi(
            baseUrlProvider = { settings.current().serverUrl },
            tokenProvider = { settings.current().appToken },
        )
        // 옛 버전(숫자 id) 데이터는 Room이 표를 새로 만들기 전에 읽어 둔다.
        val legacy = runCatching { LegacyImporter.read(this) }.getOrNull()
        val db = AppDatabase.build(this)
        repository = Repository(db, api, settings, calendar, ReminderScheduler(this))
        sync = SyncEngine(db, api, settings, calendar)
        repository.sync = sync
        ensureNotificationChannel(this)
        RecordingWatcher.ensureScheduled(this)
        SyncWorker.ensureScheduled(this)
        appScope.launch {
            if (legacy != null && !legacy.isEmpty) runCatching { LegacyImporter.write(db, legacy) }
            runCatching { sync.syncNow() }
        }
    }

    companion object {
        fun from(context: android.content.Context): BizApp = context.applicationContext as BizApp
    }
}
