package com.jpntech.bizassistant.data.sync

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.jpntech.bizassistant.data.db.AppDatabase
import com.jpntech.bizassistant.data.db.EventEntity
import com.jpntech.bizassistant.data.db.MoneyEntity
import com.jpntech.bizassistant.data.db.OrgEntity
import com.jpntech.bizassistant.data.db.PendingEntity
import com.jpntech.bizassistant.data.db.ProjectEntity
import com.jpntech.bizassistant.data.db.RecordEntity
import com.jpntech.bizassistant.data.db.TaskEntity
import com.jpntech.bizassistant.data.db.TicketEntity

/**
 * 1·2버전(숫자 id) 데이터베이스를 Room이 열기 전에 통째로 읽어 둔다.
 * Room은 3버전으로 올라가며 표를 새로 만들므로, 읽어 둔 것을 uid 를 붙여 다시 넣고 서버에 올린다.
 */
object LegacyImporter {
    class Snapshot(
        val orgs: List<OrgEntity>, val projects: List<ProjectEntity>, val records: List<RecordEntity>, val events: List<EventEntity>,
        val tasks: List<TaskEntity>, val money: List<MoneyEntity>, val tickets: List<TicketEntity>, val pending: List<PendingEntity>,
    ) { val isEmpty get() = orgs.isEmpty() && records.isEmpty() && tasks.isEmpty() && pending.isEmpty() }

    /** 옛 버전이 아니면 null */
    fun read(context: Context): Snapshot? {
        val file = context.getDatabasePath(AppDatabase.NAME)
        if (!file.exists()) return null
        val db = try { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) } catch (_: Exception) { return null }
        db.use {
            val version = it.version
            if (version >= 3 || version == 0) return null
            val now = System.currentTimeMillis()
            val orgUid = HashMap<Long, String>()
            val projectUid = HashMap<Long, String>()
            val recordUid = HashMap<Long, String>()
            val pendingUid = HashMap<Long, String>()

            val orgs = rows(it, "orgs") { c ->
                val uid = SyncEngine.newUid(); orgUid[c.l("id")] = uid
                OrgEntity(uid, c.s("name") ?: "", c.s("kind") ?: "other", c.ln("lastContactAt"), c.s("lastPerson"), now, true, false)
            }
            val projects = rows(it, "projects") { c ->
                val uid = SyncEngine.newUid(); projectUid[c.l("id")] = uid
                ProjectEntity(uid, c.s("name") ?: "", c.s("entity") ?: "불명", c.ln("orgId")?.let(orgUid::get), c.s("system") ?: "none", c.s("status") ?: "active", c.s("warrantyEnd"), now, true, false)
            }
            val records = rows(it, "records") { c ->
                val uid = SyncEngine.newUid(); recordUid[c.l("id")] = uid
                RecordEntity(
                    uid = uid, createdAt = c.l("createdAt"), source = c.s("source") ?: "memo", entity = c.s("entity") ?: "불명",
                    orgUid = c.ln("orgId")?.let(orgUid::get), orgName = c.s("orgName"), person = c.s("person"), projectUid = c.ln("projectId")?.let(projectUid::get), projectName = c.s("projectName"),
                    site = c.s("site"), system = c.s("system") ?: "none", type = c.s("type") ?: "info", urgency = c.s("urgency") ?: "normal", summary = c.s("summary") ?: "",
                    decisions = c.s("decisions") ?: "", questions = c.s("questions") ?: "", rawText = c.s("rawText") ?: "", replyDraft = c.s("replyDraft"),
                    sensitive = c.l("sensitive") == 1L, confidence = c.d("confidence"), updatedAt = now, dirty = true, deleted = false,
                )
            }
            val events = rows(it, "events") { c ->
                val rec = recordUid[c.l("recordId")] ?: return@rows null
                EventEntity(SyncEngine.newUid(), rec, c.s("title") ?: "", c.l("startMillis"), c.l("endMillis"), c.s("place"), c.ln("calendarEventId"), now, true, false)
            }
            val tasks = rows(it, "tasks") { c ->
                TaskEntity(SyncEngine.newUid(), c.ln("recordId")?.let(recordUid::get), c.s("title") ?: "", c.s("owner") ?: "me", c.s("due"), c.s("note"), c.l("done") == 1L, c.ln("reminderAt"), now, true, false)
            }
            val money = rows(it, "money") { c ->
                val rec = recordUid[c.l("recordId")] ?: return@rows null
                MoneyEntity(SyncEngine.newUid(), rec, c.s("kind") ?: "receivable", c.ln("amount"), c.s("due"), c.s("note"), c.l("settled") == 1L, now, true, false)
            }
            val tickets = rows(it, "tickets") { c ->
                val rec = recordUid[c.l("recordId")] ?: return@rows null
                TicketEntity(SyncEngine.newUid(), rec, c.ln("orgId")?.let(orgUid::get), c.s("orgName"), c.s("entity") ?: "불명", c.s("site"), c.s("system") ?: "none", c.s("symptom") ?: "", c.s("urgency") ?: "normal", c.s("status") ?: "open", c.l("createdAt"), c.ln("closedAt"), now, true, false)
            }
            val pending = if (version >= 2) rows(it, "pending") { c ->
                val uid = SyncEngine.newUid(); pendingUid[c.l("id")] = uid
                PendingEntity(uid, c.l("createdAt"), c.s("source") ?: "call", c.s("rawText") ?: "", c.s("analysisJson") ?: "", c.s("callerHint"), c.s("title") ?: "", now, true, false)
            } else emptyList()
            return Snapshot(orgs, projects, records, events, tasks, money, tickets, pending)
        }
    }

    suspend fun write(db: AppDatabase, s: Snapshot) {
        s.orgs.forEach { db.orgs().upsert(it) }
        s.projects.forEach { db.projects().upsert(it) }
        s.records.forEach { db.records().upsert(it) }
        s.events.forEach { db.events().upsert(it) }
        s.tasks.forEach { db.tasks().upsert(it) }
        s.money.forEach { db.money().upsert(it) }
        s.tickets.forEach { db.tickets().upsert(it) }
        s.pending.forEach { db.pending().upsert(it) }
    }

    private fun <T : Any> rows(db: SQLiteDatabase, table: String, map: (Cursor) -> T?): List<T> {
        val out = mutableListOf<T>()
        val c = try { db.rawQuery("SELECT * FROM $table", null) } catch (_: Exception) { return out }
        c.use { while (it.moveToNext()) map(it)?.let(out::add) }
        return out
    }
    private fun Cursor.idx(name: String): Int = getColumnIndex(name)
    private fun Cursor.s(name: String): String? { val i = idx(name); return if (i < 0 || isNull(i)) null else getString(i) }
    private fun Cursor.l(name: String): Long { val i = idx(name); return if (i < 0 || isNull(i)) 0L else getLong(i) }
    private fun Cursor.ln(name: String): Long? { val i = idx(name); return if (i < 0 || isNull(i)) null else getLong(i) }
    private fun Cursor.d(name: String): Double { val i = idx(name); return if (i < 0 || isNull(i)) 0.0 else getDouble(i) }
}
