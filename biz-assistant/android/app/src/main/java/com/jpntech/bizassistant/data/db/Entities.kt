package com.jpntech.bizassistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 모든 기록은 서버와 같은 uid(문자열)로 식별한다. 폰은 서버 기록의 사본이자 오프라인 대기열이다.
 * updatedAt: 마지막 수정 시각(마지막 쓰기 우선 기준), dirty: 아직 서버에 올리지 않음, deleted: 지운 표시.
 */

/** 상대 조직. kind: client(발주처) partner(협력) vendor(자재·외주) internal(내부) other */
@Entity(tableName = "orgs")
data class OrgEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val kind: String,
    val lastContactAt: Long? = null,
    val lastPerson: String? = null,
    val updatedAt: Long,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val entity: String,
    val orgUid: String? = null,
    val system: String = "none",
    val status: String = "active",
    val warrantyEnd: String? = null,
    val updatedAt: Long,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
)

/** 통화·문자 한 건의 기록. 조직·사업 이름은 표시 편의를 위해 함께 저장한다. */
@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val uid: String,
    val createdAt: Long,
    val source: String,
    val entity: String,
    val orgUid: String? = null,
    val orgName: String? = null,
    val person: String? = null,
    val projectUid: String? = null,
    val projectName: String? = null,
    val site: String? = null,
    val system: String = "none",
    val type: String,
    val urgency: String,
    val summary: String,
    val decisions: String = "",
    val questions: String = "",
    val rawText: String,
    val replyDraft: String? = null,
    val sensitive: Boolean = false,
    val confidence: Double = 0.0,
    val updatedAt: Long,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val uid: String,
    val recordUid: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val place: String? = null,
    val calendarEventId: Long? = null,
    val updatedAt: Long,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val uid: String,
    val recordUid: String? = null,
    val title: String,
    val owner: String = "me",
    val due: String? = null,
    val note: String? = null,
    val done: Boolean = false,
    val reminderAt: Long? = null,
    val updatedAt: Long,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
)

@Entity(tableName = "money")
data class MoneyEntity(
    @PrimaryKey val uid: String,
    val recordUid: String,
    val kind: String,
    val amount: Long? = null,
    val due: String? = null,
    val note: String? = null,
    val settled: Boolean = false,
    val updatedAt: Long,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
)

/** 장애·AS 티켓. status: open(접수) scheduled(방문 예정) working(조치 중) done(완료) */
@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val uid: String,
    val recordUid: String,
    val orgUid: String? = null,
    val orgName: String? = null,
    val entity: String,
    val site: String? = null,
    val system: String = "none",
    val symptom: String,
    val urgency: String,
    val status: String = "open",
    val createdAt: Long,
    val closedAt: Long? = null,
    val updatedAt: Long,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
)

/** 자동 처리로 분석까지 끝났지만 아직 사용자가 확인하지 않은 건 */
@Entity(tableName = "pending")
data class PendingEntity(
    @PrimaryKey val uid: String,
    val createdAt: Long,
    val source: String,
    val rawText: String,
    val analysisJson: String,
    val callerHint: String? = null,
    val title: String,
    val updatedAt: Long,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
)

/** 처리한(또는 처리 중인) 통화 녹음 파일. 이 폰에서만 쓰고 서버에는 올리지 않는다. */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val mediaId: Long,
    val displayName: String,
    val dateAdded: Long,
    val status: String,
    val processedAt: Long? = null,
    val pendingUid: String? = null,
    val error: String? = null,
)
