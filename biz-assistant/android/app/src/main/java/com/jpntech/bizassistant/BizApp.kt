package com.jpntech.bizassistant

import android.app.Application
import com.jpntech.bizassistant.calendar.CalendarWriter
import com.jpntech.bizassistant.data.Repository
import com.jpntech.bizassistant.data.db.AppDatabase
import com.jpntech.bizassistant.data.remote.RelayApi
import com.jpntech.bizassistant.data.settings.SettingsStore
import com.jpntech.bizassistant.reminders.ReminderScheduler
import com.jpntech.bizassistant.reminders.ensureNotificationChannel

class BizApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: Repository
        private set
    lateinit var calendar: CalendarWriter
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        calendar = CalendarWriter(this)
        val api = RelayApi(
            baseUrlProvider = { settings.current().serverUrl },
            tokenProvider = { settings.current().appToken },
        )
        repository = Repository(AppDatabase.build(this), api, settings, calendar, ReminderScheduler(this))
        ensureNotificationChannel(this)
    }

    companion object {
        fun from(context: android.content.Context): BizApp = context.applicationContext as BizApp
    }
}
