package com.jpntech.bizassistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrgDao {
    @Query("SELECT * FROM orgs ORDER BY lastContactAt DESC")
    fun all(): Flow<List<OrgEntity>>

    @Query("SELECT name FROM orgs")
    suspend fun names(): List<String>

    @Query("SELECT * FROM orgs WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): OrgEntity?

    @Insert
    suspend fun insert(org: OrgEntity): Long

    @Query("UPDATE orgs SET lastContactAt = :at, lastPerson = COALESCE(:person, lastPerson) WHERE id = :id")
    suspend fun touch(id: Long, at: Long, person: String?)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name")
    fun all(): Flow<List<ProjectEntity>>

    @Query("SELECT name FROM projects")
    suspend fun names(): List<String>

    @Query("SELECT * FROM projects WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE orgId = :orgId ORDER BY createdAt DESC")
    fun forOrg(orgId: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun byId(id: Long): RecordEntity?

    @Insert
    suspend fun insert(record: RecordEntity): Long
}

@Dao
interface EventDao {
    @Query("SELECT e.* FROM events e JOIN records r ON r.id = e.recordId WHERE r.orgId = :orgId ORDER BY e.startMillis DESC")
    fun forOrg(orgId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE startMillis >= :from AND startMillis < :to ORDER BY startMillis")
    fun between(from: Long, to: Long): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity): Long
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY CASE WHEN due IS NULL THEN 1 ELSE 0 END, due")
    fun open(): Flow<List<TaskEntity>>

    @Query("SELECT t.* FROM tasks t JOIN records r ON r.id = t.recordId WHERE r.orgId = :orgId ORDER BY t.done, t.due")
    fun forOrg(orgId: Long): Flow<List<TaskEntity>>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)
}

@Dao
interface MoneyDao {
    @Query("SELECT m.* FROM money m JOIN records r ON r.id = m.recordId WHERE r.orgId = :orgId ORDER BY m.due")
    fun forOrg(orgId: Long): Flow<List<MoneyEntity>>

    @Query("SELECT * FROM money WHERE settled = 0 ORDER BY CASE WHEN due IS NULL THEN 1 ELSE 0 END, due")
    fun open(): Flow<List<MoneyEntity>>

    @Insert
    suspend fun insert(money: MoneyEntity): Long

    @Query("UPDATE money SET settled = :settled WHERE id = :id")
    suspend fun setSettled(id: Long, settled: Boolean)
}

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY CASE status WHEN 'open' THEN 0 WHEN 'scheduled' THEN 1 WHEN 'working' THEN 2 ELSE 3 END, CASE urgency WHEN 'high' THEN 0 WHEN 'normal' THEN 1 ELSE 2 END, createdAt DESC")
    fun all(): Flow<List<TicketEntity>>

    @Query("SELECT COUNT(*) FROM tickets WHERE status != 'done'")
    fun openCount(): Flow<Int>

    @Insert
    suspend fun insert(ticket: TicketEntity): Long

    @Update
    suspend fun update(ticket: TicketEntity)
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending ORDER BY createdAt DESC")
    fun all(): Flow<List<PendingEntity>>

    @Query("SELECT COUNT(*) FROM pending")
    fun count(): Flow<Int>

    @Query("SELECT * FROM pending WHERE id = :id")
    suspend fun byId(id: Long): PendingEntity?

    @Insert
    suspend fun insert(p: PendingEntity): Long

    @Query("DELETE FROM pending WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE mediaId = :mediaId")
    suspend fun byMediaId(mediaId: Long): RecordingEntity?

    @Query("SELECT * FROM recordings ORDER BY dateAdded DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RecordingEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(r: RecordingEntity)
}
