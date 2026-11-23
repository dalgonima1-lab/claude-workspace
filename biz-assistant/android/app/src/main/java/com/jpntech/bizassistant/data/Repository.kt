package com.jpntech.bizassistant.data

import com.jpntech.bizassistant.calendar.CalendarWriter
import com.jpntech.bizassistant.data.db.AppDatabase
import com.jpntech.bizassistant.data.db.EventEntity
import com.jpntech.bizassistant.data.db.MoneyEntity
import com.jpntech.bizassistant.data.db.OrgEntity
import com.jpntech.bizassistant.data.db.ProjectEntity
import com.jpntech.bizassistant.data.db.RecordEntity
import com.jpntech.bizassistant.data.db.TaskEntity
import com.jpntech.bizassistant.data.db.TicketEntity
import com.jpntech.bizassistant.data.remote.AnalyzeRequest
import com.jpntech.bizassistant.data.remote.AnalyzeResponse
import com.jpntech.bizassistant.data.remote.RelayApi
import com.jpntech.bizassistant.data.settings.SettingsStore
import com.jpntech.bizassistant.reminders.ReminderScheduler

data class SaveResult(val recordId: Long, val calendarWritten: Int, val calendarSkipped: Int, val tasksSaved: Int, val ticketCreated: Boolean)

class Repository(
    val db: AppDatabase,
    val api: RelayApi,
    val settings: SettingsStore,
    private val calendar: CalendarWriter,
    private val reminders: ReminderScheduler,
) {
    suspend fun analyze(text: String, source: String, callerHint: String? = null): AnalyzeResponse {
        val s = settings.current()
        return api.analyze(
            AnalyzeRequest(
                text = text,
                source = source,
                receivedAt = TimeUtil.nowIso(),
                defaultEntity = s.defaultEntity,
                knownOrgs = db.orgs().names(),
                knownProjects = db.projects().names(),
                callerHint = callerHint,
            ),
        )
    }

    /** 확인 카드에서 등록을 누르면 기록, 일정, 할 일, 금전, 티켓을 한 번에 저장한다. */
    suspend fun save(d: ConfirmDraft): SaveResult {
        val now = System.currentTimeMillis()
        val s = settings.current()

        val orgId = d.orgName.trim().takeIf { it.isNotEmpty() }?.let { name ->
            val existing = db.orgs().findByName(name)
            val id = existing?.id ?: db.orgs().insert(OrgEntity(name = name, kind = d.orgKind))
            db.orgs().touch(id, now, d.person.ifBlank { null })
            id
        }
        val projectId = d.projectName.trim().takeIf { it.isNotEmpty() }?.let { name ->
            db.projects().findByName(name)?.id
                ?: db.projects().insert(ProjectEntity(name = name, entity = d.entity, orgId = orgId, system = d.system))
        }

        val recordId = db.records().insert(
            RecordEntity(
                createdAt = now,
                source = d.source,
                entity = d.entity,
                orgId = orgId,
                orgName = d.orgName.ifBlank { null },
                person = d.person.ifBlank { null },
                projectId = projectId,
                projectName = d.projectName.ifBlank { null },
                site = d.site.ifBlank { null },
                system = d.system,
                type = d.type,
                urgency = d.urgency,
                summary = d.summary,
                decisions = d.decisions.joinToString("\n"),
                questions = d.questions.joinToString("\n"),
                rawText = d.rawText,
                replyDraft = d.replyDraft,
                sensitive = d.sensitive,
                confidence = d.confidence,
            ),
        )

        var written = 0
        var skipped = 0
        val calendarId = calendar.resolveCalendarId(s.calendarId)
        for (e in d.events.filter { it.include }) {
            val start = TimeUtil.parseToMillis(e.dto.start)
            if (start == null) { skipped++; continue }
            val end = TimeUtil.parseToMillis(e.dto.end) ?: (start + 60 * 60 * 1000)
            val description = buildString {
                append(d.summary)
                if (d.orgName.isNotBlank()) append("\n상대: ${d.orgName} ${d.person}".trimEnd())
                append("\n(사업 비서에서 등록)")
            }
            val calEventId = calendarId?.let { calendar.insertEvent(it, e.dto.title, start, end, e.dto.place, description) }
            if (calEventId != null) written++ else skipped++
            db.events().insert(EventEntity(recordId = recordId, title = e.dto.title, startMillis = start, endMillis = end, place = e.dto.place, calendarEventId = calEventId))
        }

        var tasksSaved = 0
        for (t in d.tasks.filter { it.include }) {
            val reminderAt = if (s.remindersEnabled) TimeUtil.reminderFor(t.dto.due) else null
            val id = db.tasks().insert(TaskEntity(recordId = recordId, title = t.dto.title, owner = t.dto.owner, due = t.dto.due, note = t.dto.note, reminderAt = reminderAt))
            if (reminderAt != null) reminders.schedule(id, t.dto.title, reminderAt)
            tasksSaved++
        }

        for (m in d.money.filter { it.include }) {
            db.money().insert(MoneyEntity(recordId = recordId, kind = m.dto.kind, amount = m.dto.amount, due = m.dto.due, note = m.dto.note))
        }

        var ticket = false
        if (d.createTicket) {
            val hasVisit = d.events.any { it.include }
            db.tickets().insert(
                TicketEntity(
                    recordId = recordId, orgId = orgId, orgName = d.orgName.ifBlank { null }, entity = d.entity,
                    site = d.site.ifBlank { null }, system = d.system, symptom = d.summary, urgency = d.urgency,
                    status = if (hasVisit) "scheduled" else "open", createdAt = now,
                ),
            )
            ticket = true
        }
        return SaveResult(recordId, written, skipped, tasksSaved, ticket)
    }

    suspend fun advanceTicket(t: TicketEntity) {
        val next = when (t.status) { "open" -> "scheduled"; "scheduled" -> "working"; else -> "done" }
        db.tickets().update(t.copy(status = next, closedAt = if (next == "done") System.currentTimeMillis() else null))
    }
}
