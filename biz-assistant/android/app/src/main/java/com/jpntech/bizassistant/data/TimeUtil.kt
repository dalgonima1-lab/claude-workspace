package com.jpntech.bizassistant.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeUtil {
    val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayDateTime = DateTimeFormatter.ofPattern("M/d (E) HH:mm")
    private val displayDate = DateTimeFormatter.ofPattern("M/d (E)")
    private val displayTime = DateTimeFormatter.ofPattern("HH:mm")

    /** "2026-11-24T10:00" 또는 "2026-11-24" → epoch millis (한국 시간). 실패하면 null */
    fun parseToMillis(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(text.take(16), dateTime).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(text.take(10), date).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun parseDate(text: String?): LocalDate? =
        try { if (text.isNullOrBlank()) null else LocalDate.parse(text.take(10), date) } catch (_: DateTimeParseException) { null }

    /** epoch millis → 'YYYY-MM-DDTHH:mm' (한국 시간). 서버에 기존 일정을 설명할 때 */
    fun formatIsoLocal(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(dateTime)

    fun nowIso(): String = ZonedDateTime.now(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun today(): LocalDate = LocalDate.now(zone)

    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun formatDateTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(displayDateTime)
    fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(displayTime)
    fun formatDate(d: LocalDate): String = d.format(displayDate)
    fun formatDate(text: String?): String = parseDate(text)?.let { formatDate(it) } ?: (text ?: "")

    fun relative(millis: Long): String {
        val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(d, today())
        return when {
            days <= 0L -> "오늘"
            days == 1L -> "어제"
            days < 7L -> "${days}일 전"
            days < 30L -> "${days / 7}주 전"
            else -> "${days / 30}개월 전"
        }
    }

    /** 기한 하루 전 09:00. 이미 지났으면 당일 09:00, 그것도 지났으면 null */
    fun reminderFor(due: String?): Long? {
        val d = parseDate(due) ?: return null
        val now = ZonedDateTime.now(zone)
        val dayBefore = d.minusDays(1).atTime(9, 0).atZone(zone)
        val sameDay = d.atTime(9, 0).atZone(zone)
        return when {
            dayBefore.isAfter(now) -> dayBefore.toInstant().toEpochMilli()
            sameDay.isAfter(now) -> sameDay.toInstant().toEpochMilli()
            else -> null
        }
    }

    fun dDay(due: String?): String? {
        val d = parseDate(due) ?: return null
        val diff = java.time.temporal.ChronoUnit.DAYS.between(today(), d)
        return when {
            diff == 0L -> "D-Day"
            diff > 0 -> "D-$diff"
            else -> "D+${-diff}"
        }
    }
}
