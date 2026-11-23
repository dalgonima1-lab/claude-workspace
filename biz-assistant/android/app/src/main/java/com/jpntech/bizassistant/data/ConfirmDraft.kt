package com.jpntech.bizassistant.data

import com.jpntech.bizassistant.data.remote.Analysis
import com.jpntech.bizassistant.data.remote.Counterpart
import com.jpntech.bizassistant.data.remote.EventDto
import com.jpntech.bizassistant.data.remote.MoneyDto
import com.jpntech.bizassistant.data.remote.TaskDto

data class DraftEvent(val dto: EventDto, val include: Boolean = true)
data class DraftTask(val dto: TaskDto, val include: Boolean = true)
data class DraftMoney(val dto: MoneyDto, val include: Boolean = true)

/** 확인 카드에서 사용자가 고친 뒤 저장하는 내용. 서버 분석 결과에 선택 여부가 붙은 형태다. */
data class ConfirmDraft(
    val rawText: String,
    val source: String,
    val entity: String,
    val orgName: String,
    val orgKind: String,
    val person: String,
    val projectName: String,
    val system: String,
    val site: String,
    val type: String,
    val urgency: String,
    val summary: String,
    val decisions: List<String>,
    val questions: List<String>,
    val events: List<DraftEvent>,
    val tasks: List<DraftTask>,
    val money: List<DraftMoney>,
    val replyDraft: String?,
    val sensitive: Boolean,
    val confidence: Double,
    val createTicket: Boolean,
) {
    companion object {
        fun from(a: Analysis, rawText: String, source: String, defaultEntity: String): ConfirmDraft = ConfirmDraft(
            rawText = rawText,
            source = source,
            entity = if (a.entity == "불명") defaultEntity else a.entity,
            orgName = a.counterpart.org ?: "",
            orgKind = a.counterpart.orgKind,
            person = a.counterpart.person ?: "",
            projectName = a.project ?: "",
            system = a.system,
            site = a.site ?: "",
            type = a.type,
            urgency = a.urgency,
            summary = a.summary,
            decisions = a.decisions,
            questions = a.questions,
            events = a.events.map { DraftEvent(it) },
            tasks = a.tasks.map { DraftTask(it) },
            money = a.money.map { DraftMoney(it) },
            replyDraft = a.replyDraft,
            sensitive = a.sensitive,
            confidence = a.confidence,
            createTicket = a.type == "fault",
        )
    }

    /** 답장 초안 요청 등에 다시 보낼 분석 모양 */
    fun toAnalysis(): Analysis = Analysis(
        isBusiness = type != "personal",
        entity = entity,
        counterpart = Counterpart(org = orgName.ifBlank { null }, orgKind = orgKind, person = person.ifBlank { null }),
        project = projectName.ifBlank { null },
        system = system,
        site = site.ifBlank { null },
        type = type,
        urgency = urgency,
        summary = summary,
        decisions = decisions,
        events = events.filter { it.include }.map { it.dto },
        tasks = tasks.filter { it.include }.map { it.dto },
        money = money.filter { it.include }.map { it.dto },
        replyDraft = replyDraft,
        sensitive = sensitive,
        confidence = confidence,
        questions = questions,
    )
}
