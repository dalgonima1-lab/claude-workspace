package com.jpntech.bizassistant.data.sync

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
import com.jpntech.bizassistant.data.remote.RelayApi
import com.jpntech.bizassistant.data.remote.SyncItem
import com.jpntech.bizassistant.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * 폰의 Room 표와 서버의 items 를 맞춘다.
 * push: dirty 행을 올린다. pull: 서버 커서 이후 바뀐 항목을 받아 덮어쓴다 (내 쪽이 더 새롭고 아직 안 올렸으면 내 것을 지킨다).
 * 웹에서 만든 일정에는 캘린더 번호가 없으므로 pull 뒤에 삼성 캘린더에 넣고 번호를 적어 둔다.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val api: RelayApi,
    private val settings: SettingsStore,
    private val calendar: CalendarWriter,
) {
    data class Status(val running: Boolean = false, val lastSyncAt: Long? = null, val lastError: String? = null, val pushed: Int = 0, val pulled: Int = 0)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status
    private val lock = Mutex()

    suspend fun syncNow(): Status = lock.withLock {
        _status.value = _status.value.copy(running = true, lastError = null)
        try {
            val pushed = push()
            val pulled = pull()
            _status.value = Status(running = false, lastSyncAt = System.currentTimeMillis(), pushed = pushed, pulled = pulled)
        } catch (e: Exception) {
            _status.value = _status.value.copy(running = false, lastError = e.message ?: "동기화 실패")
        }
        _status.value
    }

    suspend fun pushOnly() { lock.withLock { runCatching { push() }.onFailure { _status.value = _status.value.copy(lastError = it.message) } } }

    private suspend fun push(): Int {
        val items = mutableListOf<SyncItem>()
        db.orgs().dirty().forEach { items += SyncItem("org", it.uid, it.updatedAt, it.deleted, orgJson(it)) }
        db.projects().dirty().forEach { items += SyncItem("project", it.uid, it.updatedAt, it.deleted, projectJson(it)) }
        db.records().dirty().forEach { items += SyncItem("record", it.uid, it.updatedAt, it.deleted, recordJson(it)) }
        db.events().dirty().forEach { items += SyncItem("event", it.uid, it.updatedAt, it.deleted, eventJson(it)) }
        db.tasks().dirty().forEach { items += SyncItem("task", it.uid, it.updatedAt, it.deleted, taskJson(it)) }
        db.money().dirty().forEach { items += SyncItem("money", it.uid, it.updatedAt, it.deleted, moneyJson(it)) }
        db.tickets().dirty().forEach { items += SyncItem("ticket", it.uid, it.updatedAt, it.deleted, ticketJson(it)) }
        db.pending().dirty().forEach { items += SyncItem("pending", it.uid, it.updatedAt, it.deleted, pendingJson(it)) }
        if (items.isEmpty()) return 0
        items.chunked(500).forEach { chunk ->
            api.pushData(chunk)
            for (it in chunk) when (it.kind) {
                "org" -> db.orgs().markClean(it.uid, it.updatedAt)
                "project" -> db.projects().markClean(it.uid, it.updatedAt)
                "record" -> db.records().markClean(it.uid, it.updatedAt)
                "event" -> db.events().markClean(it.uid, it.updatedAt)
                "task" -> db.tasks().markClean(it.uid, it.updatedAt)
                "money" -> db.money().markClean(it.uid, it.updatedAt)
                "ticket" -> db.tickets().markClean(it.uid, it.updatedAt)
                "pending" -> db.pending().markClean(it.uid, it.updatedAt)
            }
        }
        return items.size
    }

    private suspend fun pull(): Int {
        val since = settings.current().syncCursor
        val res = api.pullData(since)
        var applied = 0
        for (it in res.items) {
            if (apply(it)) applied++
        }
        if (res.cursor != since) settings.update { s -> s.copy(syncCursor = res.cursor) }
        attachCalendar()
        return applied
    }

    /** 서버 항목 하나를 로컬에 반영. 내가 아직 안 올린 더 새 변경이 있으면 건너뛴다. */
    private suspend fun apply(it: SyncItem): Boolean {
        val d = it.data
        when (it.kind) {
            "org" -> {
                val local = db.orgs().byUid(it.uid)
                if (local != null && local.dirty && local.updatedAt > it.updatedAt) return false
                db.orgs().upsert(OrgEntity(it.uid, d.str("name") ?: local?.name ?: "", d.str("kind") ?: "other", d.long("lastContactAt"), d.str("lastPerson"), it.updatedAt, false, it.deleted))
            }
            "project" -> {
                val local = db.projects().byUid(it.uid)
                if (local != null && local.dirty && local.updatedAt > it.updatedAt) return false
                db.projects().upsert(ProjectEntity(it.uid, d.str("name") ?: "", d.str("entity") ?: "불명", d.str("orgUid"), d.str("system") ?: "none", d.str("status") ?: "active", d.str("warrantyEnd"), it.updatedAt, false, it.deleted))
            }
            "record" -> {
                val local = db.records().byUid(it.uid)
                if (local != null && local.dirty && local.updatedAt > it.updatedAt) return false
                db.records().upsert(
                    RecordEntity(
                        uid = it.uid, createdAt = d.long("createdAt") ?: it.updatedAt, source = d.str("source") ?: "memo", entity = d.str("entity") ?: "불명",
                        orgUid = d.str("orgUid"), orgName = d.str("orgName"), person = d.str("person"), projectUid = d.str("projectUid"), projectName = d.str("projectName"),
                        site = d.str("site"), system = d.str("system") ?: "none", type = d.str("type") ?: "info", urgency = d.str("urgency") ?: "normal", summary = d.str("summary") ?: "",
                        decisions = d.lines("decisions"), questions = d.lines("questions"), rawText = d.str("rawText") ?: "", replyDraft = d.str("replyDraft"),
                        sensitive = d.bool("sensitive") ?: false, confidence = d.double("confidence") ?: 0.0,
                        answersJson = (d["answers"] as? JsonArray)?.toString() ?: "[]", updatedAt = it.updatedAt, dirty = false, deleted = it.deleted,
                    ),
                )
            }
            "event" -> {
                val local = db.events().byUid(it.uid)
                if (local != null && local.dirty && local.updatedAt > it.updatedAt) return false
                val start = d.long("startMillis") ?: return false
                db.events().upsert(EventEntity(it.uid, d.str("recordUid") ?: "", d.str("title") ?: "", start, d.long("endMillis") ?: (start + 3600_000), d.str("place"), d.long("calendarEventId") ?: local?.calendarEventId, it.updatedAt, false, it.deleted))
            }
            "task" -> {
                val local = db.tasks().byUid(it.uid)
                if (local != null && local.dirty && local.updatedAt > it.updatedAt) return false
                db.tasks().upsert(TaskEntity(it.uid, d.str("recordUid"), d.str("title") ?: "", d.str("owner") ?: "me", d.str("due"), d.str("note"), d.bool("done") ?: false, d.long("reminderAt") ?: local?.reminderAt, it.updatedAt, false, it.deleted))
            }
            "money" -> {
                val local = db.money().byUid(it.uid)
                if (local != null && local.dirty && local.updatedAt > it.updatedAt) return false
                db.money().upsert(MoneyEntity(it.uid, d.str("recordUid") ?: "", d.str("kind") ?: "receivable", d.long("amount"), d.str("due"), d.str("note"), d.bool("settled") ?: false, it.updatedAt, false, it.deleted))
            }
            "ticket" -> {
                val local = db.tickets().byUid(it.uid)
                if (local != null && local.dirty && local.updatedAt > it.updatedAt) return false
                db.tickets().upsert(TicketEntity(it.uid, d.str("recordUid") ?: "", d.str("orgUid"), d.str("orgName"), d.str("entity") ?: "불명", d.str("site"), d.str("system") ?: "none", d.str("symptom") ?: "", d.str("urgency") ?: "normal", d.str("status") ?: "open", d.long("createdAt") ?: it.updatedAt, d.long("closedAt"), it.updatedAt, false, it.deleted))
            }
            "pending" -> {
                val local = db.pending().byUid(it.uid)
                if (local != null && local.dirty && local.updatedAt > it.updatedAt) return false
                db.pending().upsert(PendingEntity(it.uid, d.long("createdAt") ?: it.updatedAt, d.str("source") ?: "call", d.str("rawText") ?: "", d.str("analysisJson") ?: "", d.str("callerHint"), d.str("title") ?: "", it.updatedAt, false, it.deleted))
            }
            else -> return false
        }
        return true
    }

    /** 웹에서 만든 일정처럼 캘린더 번호가 없는 앞으로의 일정을 삼성 캘린더에 넣는다. */
    private suspend fun attachCalendar() {
        if (!calendar.hasPermission()) return
        val calendarId = calendar.resolveCalendarId(settings.current().calendarId) ?: return
        val now = System.currentTimeMillis()
        for (e in db.events().withoutCalendar(now - 3600_000)) {
            val record = db.records().byUid(e.recordUid)
            val desc = listOfNotNull(record?.summary, record?.orgName?.let { "상대: $it ${record.person ?: ""}".trim() }, "(사업 비서에서 등록)").joinToString("\n")
            val id = calendar.insertEvent(calendarId, e.title, e.startMillis, e.endMillis, e.place, desc) ?: continue
            db.events().upsert(e.copy(calendarEventId = id, updatedAt = System.currentTimeMillis(), dirty = true))
        }
    }

    companion object {
        fun newUid(): String = UUID.randomUUID().toString().replace("-", "")

        fun orgJson(o: OrgEntity) = buildJsonObject { put("name", o.name); put("kind", o.kind); putNullable("lastContactAt", o.lastContactAt); putNullable("lastPerson", o.lastPerson) }
        fun projectJson(p: ProjectEntity) = buildJsonObject { put("name", p.name); put("entity", p.entity); putNullable("orgUid", p.orgUid); put("system", p.system); put("status", p.status); putNullable("warrantyEnd", p.warrantyEnd) }
        fun recordJson(r: RecordEntity) = buildJsonObject {
            put("createdAt", r.createdAt); put("source", r.source); put("entity", r.entity); putNullable("orgUid", r.orgUid); putNullable("orgName", r.orgName); putNullable("person", r.person)
            putNullable("projectUid", r.projectUid); putNullable("projectName", r.projectName); putNullable("site", r.site); put("system", r.system); put("type", r.type); put("urgency", r.urgency)
            put("summary", r.summary); put("decisions", JsonArray(r.decisions.lines().filter { it.isNotBlank() }.map { JsonPrimitive(it) })); put("questions", JsonArray(r.questions.lines().filter { it.isNotBlank() }.map { JsonPrimitive(it) }))
            put("rawText", r.rawText); putNullable("replyDraft", r.replyDraft); put("sensitive", r.sensitive); put("confidence", r.confidence)
            put("answers", runCatching { kotlinx.serialization.json.Json.parseToJsonElement(r.answersJson) }.getOrElse { JsonArray(emptyList()) })
        }
        fun eventJson(e: EventEntity) = buildJsonObject { put("recordUid", e.recordUid); put("title", e.title); put("startMillis", e.startMillis); put("endMillis", e.endMillis); putNullable("place", e.place); putNullable("calendarEventId", e.calendarEventId) }
        fun taskJson(t: TaskEntity) = buildJsonObject { putNullable("recordUid", t.recordUid); put("title", t.title); put("owner", t.owner); putNullable("due", t.due); putNullable("note", t.note); put("done", t.done); putNullable("reminderAt", t.reminderAt) }
        fun moneyJson(m: MoneyEntity) = buildJsonObject { put("recordUid", m.recordUid); put("kind", m.kind); putNullable("amount", m.amount); putNullable("due", m.due); putNullable("note", m.note); put("settled", m.settled) }
        fun ticketJson(t: TicketEntity) = buildJsonObject { put("recordUid", t.recordUid); putNullable("orgUid", t.orgUid); putNullable("orgName", t.orgName); put("entity", t.entity); putNullable("site", t.site); put("system", t.system); put("symptom", t.symptom); put("urgency", t.urgency); put("status", t.status); put("createdAt", t.createdAt); putNullable("closedAt", t.closedAt) }
        fun pendingJson(p: PendingEntity) = buildJsonObject { put("createdAt", p.createdAt); put("source", p.source); put("rawText", p.rawText); put("analysisJson", p.analysisJson); putNullable("callerHint", p.callerHint); put("title", p.title) }

        private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, v: String?) { if (v == null) put(key, JsonNull) else put(key, v) }
        private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, v: Long?) { if (v == null) put(key, JsonNull) else put(key, v) }

        private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
        fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull
        fun JsonObject.long(key: String): Long? = prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
        fun JsonObject.double(key: String): Double? = prim(key)?.doubleOrNull
        fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull
        fun JsonObject.lines(key: String): String {
            val el: JsonElement = this[key] ?: return ""
            return when (el) {
                is JsonArray -> el.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n")
                is JsonPrimitive -> el.contentOrNull ?: ""
                else -> ""
            }
        }
    }
}
