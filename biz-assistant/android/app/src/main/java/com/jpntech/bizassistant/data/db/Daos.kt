package com.jpntech.bizassistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 모든 조회는 지운 표시가 없는 것만 보여 주고, 저장은 같은 uid를 덮어쓴다. */

@Dao
interface OrgDao {
    @Query("SELECT * FROM orgs WHERE deleted = 0 ORDER BY lastContactAt DESC")
    fun all(): Flow<List<OrgEntity>>

    @Query("SELECT name FROM orgs WHERE deleted = 0")
    suspend fun names(): List<String>

    @Query("SELECT * FROM orgs WHERE name = :name AND deleted = 0 LIMIT 1")
    suspend fun findByName(name: String): OrgEntity?

    @Query("SELECT * FROM orgs WHERE uid = :uid")
    suspend fun byUid(uid: String): OrgEntity?

    @Query("SELECT * FROM orgs WHERE dirty = 1")
    suspend fun dirty(): List<OrgEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(org: OrgEntity)

    @Query("UPDATE orgs SET lastContactAt = :at, lastPerson = COALESCE(:person, lastPerson), updatedAt = :at, dirty = 1 WHERE uid = :uid")
    suspend fun touch(uid: String, at: Long, person: String?)

    @Query("UPDATE orgs SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deleted = 0 ORDER BY name")
    fun all(): Flow<List<ProjectEntity>>

    @Query("SELECT name FROM projects WHERE deleted = 0")
    suspend fun names(): List<String>

    @Query("SELECT * FROM projects WHERE name = :name AND deleted = 0 LIMIT 1")
    suspend fun findByName(name: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE uid = :uid")
    suspend fun byUid(uid: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE dirty = 1")
    suspend fun dirty(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE orgUid = :orgUid AND deleted = 0 ORDER BY createdAt DESC")
    fun forOrg(orgUid: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE deleted = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE uid = :uid")
    suspend fun byUid(uid: String): RecordEntity?

    @Query("SELECT * FROM records WHERE uid = :uid")
    fun observe(uid: String): Flow<RecordEntity?>

    @Query("SELECT * FROM records WHERE dirty = 1")
    suspend fun dirty(): List<RecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RecordEntity)

    @Query("UPDATE records SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)
}

@Dao
interface EventDao {
    @Query("SELECT e.* FROM events e JOIN records r ON r.uid = e.recordUid WHERE r.orgUid = :orgUid AND e.deleted = 0 ORDER BY e.startMillis DESC")
    fun forOrg(orgUid: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE recordUid = :recordUid AND deleted = 0 ORDER BY startMillis")
    fun forRecord(recordUid: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE startMillis >= :from AND startMillis < :to AND deleted = 0 ORDER BY startMillis")
    fun between(from: Long, to: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE calendarEventId IS NULL AND deleted = 0 AND startMillis > :after")
    suspend fun withoutCalendar(after: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE uid = :uid")
    suspend fun byUid(uid: String): EventEntity?

    @Query("SELECT * FROM events WHERE dirty = 1")
    suspend fun dirty(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)

    @Query("UPDATE events SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE done = 0 AND deleted = 0 ORDER BY CASE WHEN due IS NULL THEN 1 ELSE 0 END, due")
    fun open(): Flow<List<TaskEntity>>

    @Query("SELECT t.* FROM tasks t JOIN records r ON r.uid = t.recordUid WHERE r.orgUid = :orgUid AND t.deleted = 0 ORDER BY t.done, t.due")
    fun forOrg(orgUid: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE recordUid = :recordUid AND deleted = 0 ORDER BY done, due")
    fun forRecord(recordUid: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE uid = :uid")
    suspend fun byUid(uid: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE dirty = 1")
    suspend fun dirty(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("UPDATE tasks SET done = :done, updatedAt = :now, dirty = 1 WHERE uid = :uid")
    suspend fun setDone(uid: String, done: Boolean, now: Long)

    @Query("UPDATE tasks SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)
}

@Dao
interface MoneyDao {
    @Query("SELECT m.* FROM money m JOIN records r ON r.uid = m.recordUid WHERE r.orgUid = :orgUid AND m.deleted = 0 ORDER BY m.due")
    fun forOrg(orgUid: String): Flow<List<MoneyEntity>>

    @Query("SELECT * FROM money WHERE recordUid = :recordUid AND deleted = 0 ORDER BY due")
    fun forRecord(recordUid: String): Flow<List<MoneyEntity>>

    @Query("SELECT * FROM money WHERE settled = 0 AND deleted = 0 ORDER BY CASE WHEN due IS NULL THEN 1 ELSE 0 END, due")
    fun open(): Flow<List<MoneyEntity>>

    @Query("SELECT * FROM money WHERE uid = :uid")
    suspend fun byUid(uid: String): MoneyEntity?

    @Query("SELECT * FROM money WHERE dirty = 1")
    suspend fun dirty(): List<MoneyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(money: MoneyEntity)

    @Query("UPDATE money SET settled = :settled, updatedAt = :now, dirty = 1 WHERE uid = :uid")
    suspend fun setSettled(uid: String, settled: Boolean, now: Long)

    @Query("UPDATE money SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)
}

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets WHERE deleted = 0 ORDER BY CASE status WHEN 'open' THEN 0 WHEN 'scheduled' THEN 1 WHEN 'working' THEN 2 ELSE 3 END, CASE urgency WHEN 'high' THEN 0 WHEN 'normal' THEN 1 ELSE 2 END, createdAt DESC")
    fun all(): Flow<List<TicketEntity>>

    @Query("SELECT COUNT(*) FROM tickets WHERE status != 'done' AND deleted = 0")
    fun openCount(): Flow<Int>

    @Query("SELECT * FROM tickets WHERE recordUid = :recordUid AND deleted = 0")
    fun forRecord(recordUid: String): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE uid = :uid")
    suspend fun byUid(uid: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE dirty = 1")
    suspend fun dirty(): List<TicketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ticket: TicketEntity)

    @Query("UPDATE tickets SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending WHERE deleted = 0 ORDER BY createdAt DESC")
    fun all(): Flow<List<PendingEntity>>

    @Query("SELECT COUNT(*) FROM pending WHERE deleted = 0")
    fun count(): Flow<Int>

    @Query("SELECT * FROM pending WHERE uid = :uid AND deleted = 0")
    suspend fun byUid(uid: String): PendingEntity?

    @Query("SELECT * FROM pending WHERE dirty = 1")
    suspend fun dirty(): List<PendingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PendingEntity)

    /** 지운 표시만 남기고 서버에도 알린다 */
    @Query("UPDATE pending SET deleted = 1, updatedAt = :now, dirty = 1 WHERE uid = :uid")
    suspend fun softDelete(uid: String, now: Long)

    @Query("UPDATE pending SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE mediaId = :mediaId")
    suspend fun byMediaId(mediaId: Long): RecordingEntity?

    @Query("SELECT * FROM recordings ORDER BY dateAdded DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: RecordingEntity)
}
