package com.jpntech.bizassistant.calendar

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/** 안드로이드 공용 캘린더에 일정을 쓴다. 삼성 캘린더는 같은 저장소를 보여 주므로 바로 반영된다. */
class CalendarWriter(private val context: Context) {

    data class CalendarInfo(val id: Long, val name: String, val account: String)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun listCalendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val out = mutableListOf<CalendarInfo>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val access = c.getInt(3)
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                out += CalendarInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")
            }
        }
        return out
    }

    /** 설정된 캘린더가 없으면 첫 번째 쓰기 가능한 캘린더를 쓴다 (보통 삼성 계정 기본 캘린더). */
    fun resolveCalendarId(preferred: Long): Long? {
        val list = listCalendars()
        if (list.isEmpty()) return null
        return list.firstOrNull { it.id == preferred }?.id ?: list.first().id
    }

    fun insertEvent(calendarId: Long, title: String, startMillis: Long, endMillis: Long, place: String?, description: String?): Long? {
        if (!hasPermission()) return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (!place.isNullOrBlank()) put(CalendarContract.Events.EVENT_LOCATION, place)
            if (!description.isNullOrBlank()) put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = uri.lastPathSegment?.toLongOrNull() ?: return null
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 60)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
        return eventId
    }
}
