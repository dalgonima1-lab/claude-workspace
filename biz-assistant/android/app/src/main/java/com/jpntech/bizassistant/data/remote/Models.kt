package com.jpntech.bizassistant.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** 중계 서버 응답 JSON. relay-server/lib/schema.ts 와 같은 모양이다. */
@Serializable
data class Counterpart(
    val org: String? = null,
    @SerialName("org_kind") val orgKind: String = "other",
    val person: String? = null,
    val phone: String? = null,
)

@Serializable
data class EventDto(
    val title: String,
    val start: String,
    val end: String? = null,
    val place: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
)

@Serializable
data class TaskDto(
    val title: String,
    val owner: String = "me",
    val due: String? = null,
    val note: String? = null,
)

@Serializable
data class MoneyDto(
    val kind: String,
    val amount: Long? = null,
    val due: String? = null,
    val note: String? = null,
)

@Serializable
data class Analysis(
    @SerialName("is_business") val isBusiness: Boolean = true,
    val entity: String = "불명",
    val counterpart: Counterpart = Counterpart(),
    val project: String? = null,
    val system: String = "none",
    val site: String? = null,
    val type: String = "info",
    val urgency: String = "normal",
    val summary: String = "",
    val decisions: List<String> = emptyList(),
    val events: List<EventDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList(),
    val money: List<MoneyDto> = emptyList(),
    @SerialName("reply_draft") val replyDraft: String? = null,
    val sensitive: Boolean = false,
    val confidence: Double = 0.0,
    val questions: List<String> = emptyList(),
)

@Serializable
data class AnalyzeRequest(
    val text: String,
    val source: String,
    @SerialName("received_at") val receivedAt: String,
    @SerialName("default_entity") val defaultEntity: String,
    @SerialName("known_orgs") val knownOrgs: List<String>,
    @SerialName("known_projects") val knownProjects: List<String>,
    @SerialName("caller_hint") val callerHint: String? = null,
)

@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
data class AnalyzeResponse(val analysis: Analysis, val model: String = "", val usage: Usage = Usage())

@Serializable
data class DraftReplyRequest(val analysis: Analysis, val tone: String = "formal")

@Serializable
data class DraftReplyResponse(val reply: String)

@Serializable
data class HealthResponse(val ok: Boolean, val model: String = "", val transcribe: Boolean = false, val problem: String? = null)

@Serializable
data class ErrorResponse(val error: String = "오류")

@Serializable
data class TranscribeResponse(val text: String = "", val model: String = "")

/** 서버 items 한 건. data 는 종류별 JSON */
@Serializable
data class SyncItem(val kind: String, val uid: String, val updatedAt: Long, val deleted: Boolean = false, val data: JsonObject)

@Serializable
data class PullResponse(val items: List<SyncItem> = emptyList(), val cursor: Long = 0)

@Serializable
data class PushRequest(val items: List<SyncItem>)

@Serializable
data class PushResponse(val received: Int = 0, val applied: Int = 0, val cursor: Long = 0)
