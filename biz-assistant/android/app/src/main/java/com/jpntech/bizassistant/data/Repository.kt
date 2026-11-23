package com.jpntech.bizassistant.data

import com.jpntech.bizassistant.calendar.CalendarWriter
import com.jpntech.bizassistant.data.db.AppDatabase
import com.jpntech.bizassistant.data.db.EventEntity
import com.jpntech.bizassistant.data.db.MoneyEntity
import com.jpntech.bizassistant.data.db.OrgEntity
import com.jpntech.bizassistant.data.db.PendingEntity
import com.jpntech.bizassistant.data.db.ProjectEntity
import com.jpntech.bizassistant.data.db.RecordEntity
import com.jpntech.bizassistant.data.db.TaskEntity
import com.jpntech.bizassistant.data.db.TicketEntity
import com.jpntech.bizassistant.data.remote.AnalyzeRequest
import com.jpntech.bizassistant.data.remote.AnalyzeResponse
import com.jpntech.bizassistant.data.remote.RelayApi
import com.jpntech.bizassistant.data.settings.SettingsStore
import com.jpntech.bizassistant.data.sync.SyncEngine
import com.jpntech.bizassistant.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class SaveResult(val recordUid: String, val calendarWritten: Int, val calendarSkipped: Int, val tasksSaved: Int, val ticketCreated: Boolean)

class Repository(
    val db: AppDatabase,
    val api: RelayApi,
    val settings: SettingsStore,
    private val calendar: CalendarWriter,
    private val reminders: ReminderScheduler,
) {
    lateinit var sync: SyncEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 저장 직후 서버에 올린다. 실패해도 dirty 로 남아 다음 동기화 때 올라간다. */
    fun pushSoon() { scope.launch { runCatching { sync.pushOnly() } } }

    suspend fun analyze(text: String, source: String, callerHint: String? = null): AnalyzeResponse {
        val s = settings.current()
        return api.analyze(
            AnalyzeRequest(
                text = text, source = source, receivedAt = TimeUtil.nowIso(), defaultEntity = s.defaultEntity,
                knownOrgs = db.orgs().names(), knownProjects = db.projects().names(), callerHint = callerHint,
            ),
        )
    }

    /** 확인 카드에서 등록을 누르면 기록, 일정, 할 일, 금전, 티켓을 한 번에 저장하고 서버에 올린다. */
    suspend fun save(d: ConfirmDraft): SaveResult {
        val now = System.currentTimeMillis()
        val s = settings.current()

        val orgUid = d.orgName.trim().takeIf { it.isNotEmpty() }?.let { name ->
            val existing = db.orgs().findByName(name)
            val uid = existing?.uid ?: SyncEngine.newUid().also { db.orgs().upsert(OrgEntity(it, name, d.orgKind, null, null, now, true, false)) }
            db.orgs().touch(uid, now, d.person.ifBlank { null })
            uid
        }
        val projectUid = d.projectName.trim().takeIf { it.isNotEmpty() }?.let { name ->
            db.projects().findByName(name)?.uid
                ?: SyncEngine.newUid().also { db.projects().upsert(ProjectEntity(it, name, d.entity, orgUid, d.system, "active", null, now, true, false)) }
        }

        val recordUid = SyncEngine.newUid()
        db.records().upsert(
            RecordEntity(
                uid = recordUid, createdAt = now, source = d.source, entity = d.entity, orgUid = orgUid, orgName = d.orgName.ifBlank { null }, person = d.person.ifBlank { null },
                projectUid = projectUid, projectName = d.projectName.ifBlank { null }, site = d.site.ifBlank { null }, system = d.system, type = d.type, urgency = d.urgency,
                summary = d.summary, decisions = d.decisions.joinToString("\n"), questions = d.questions.joinToString("\n"), rawText = d.rawText, replyDraft = d.replyDraft,
                sensitive = d.sensitive, confidence = d.confidence, updatedAt = now, dirty = true, deleted = false,
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
            db.events().upsert(EventEntity(SyncEngine.newUid(), recordUid, e.dto.title, start, end, e.dto.place, calEventId, now, true, false))
        }

        var tasksSaved = 0
        for (t in d.tasks.filter { it.include }) {
            val reminderAt = if (s.remindersEnabled) TimeUtil.reminderFor(t.dto.due) else null
            val uid = SyncEngine.newUid()
            db.tasks().upsert(TaskEntity(uid, recordUid, t.dto.title, t.dto.owner, t.dto.due, t.dto.note, false, reminderAt, now, true, false))
            if (reminderAt != null) reminders.schedule(uid.hashCode().toLong(), t.dto.title, reminderAt)
            tasksSaved++
        }

        for (m in d.money.filter { it.include }) {
            db.money().upsert(MoneyEntity(SyncEngine.newUid(), recordUid, m.dto.kind, m.dto.amount, m.dto.due, m.dto.note, false, now, true, false))
        }

        var ticket = false
        if (d.createTicket) {
            val hasVisit = d.events.any { it.include }
            db.tickets().upsert(
                TicketEntity(
                    SyncEngine.newUid(), recordUid, orgUid, d.orgName.ifBlank { null }, d.entity, d.site.ifBlank { null }, d.system, d.summary, d.urgency,
                    if (hasVisit) "scheduled" else "open", now, null, now, true, false,
                ),
            )
            ticket = true
        }
        pushSoon()
        return SaveResult(recordUid, written, skipped, tasksSaved, ticket)
    }

    suspend fun advanceTicket(t: TicketEntity) {
        val next = when (t.status) { "open" -> "scheduled"; "scheduled" -> "working"; else -> "done" }
        val now = System.currentTimeMillis()
        db.tickets().upsert(t.copy(status = next, closedAt = if (next == "done") now else null, updatedAt = now, dirty = true))
        pushSoon()
    }

    suspend fun setTaskDone(uid: String, done: Boolean) {
        db.tasks().setDone(uid, done, System.currentTimeMillis())
        pushSoon()
    }

    suspend fun addPending(p: PendingEntity) {
        db.pending().upsert(p)
        pushSoon()
    }

    suspend fun removePending(uid: String) {
        db.pending().softDelete(uid, System.currentTimeMillis())
        pushSoon()
    }
}
