package com.jpntech.bizassistant.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.data.Labels
import com.jpntech.bizassistant.data.TimeUtil
import com.jpntech.bizassistant.ui.common.AssistantBand
import com.jpntech.bizassistant.ui.common.EntityPill
import com.jpntech.bizassistant.ui.common.Pill
import com.jpntech.bizassistant.ui.common.RailItem
import com.jpntech.bizassistant.ui.common.SectionTitle
import com.jpntech.bizassistant.ui.common.TypePill
import com.jpntech.bizassistant.ui.common.UrgencyPill
import com.jpntech.bizassistant.ui.common.voice
import com.jpntech.bizassistant.ui.theme.BizColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 기록 한 건의 상세. 연결된 일정·할 일·금전·티켓을 보여 주고,
 * "확인이 필요한 점"에 답을 적으면 비서가 답을 반영해 기록을 보완한다.
 */
@Composable
fun RecordDetailScreen(recordUid: String, onBack: () -> Unit) {
    val repo = BizApp.from(LocalContext.current).repository
    val scope = rememberCoroutineScope()
    val record by remember(recordUid) { repo.db.records().observe(recordUid) }.collectAsStateWithLifecycle(null)
    val events by remember(recordUid) { repo.db.events().forRecord(recordUid) }.collectAsStateWithLifecycle(emptyList())
    val tasks by remember(recordUid) { repo.db.tasks().forRecord(recordUid) }.collectAsStateWithLifecycle(emptyList())
    val money by remember(recordUid) { repo.db.money().forRecord(recordUid) }.collectAsStateWithLifecycle(emptyList())
    val tickets by remember(recordUid) { repo.db.tickets().forRecord(recordUid) }.collectAsStateWithLifecycle(emptyList())
    val answers = remember { mutableStateMapOf<String, String>() }
    var working by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }

    val r = record
    if (r == null) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("돌아가기", color = BizColors.Muted) }
            AssistantBand(kicker = null, voice = voice("기록을 찾을 수 없습니다. 지워졌거나 아직 동기화되지 않았을 수 있습니다."))
        }
        return
    }
    val questions = r.questions.lines().filter { it.isNotBlank() }
    val pastAnswers = remember(r.answersJson) { parseAnswers(r.answersJson) }
    val sentence = buildString {
        append("{${Labels.type(r.type)}}")
        append(if (r.urgency == "high") ", 긴급 건입니다. " else " 기록입니다. ")
        val parts = mutableListOf<String>()
        if (events.isNotEmpty()) parts += "일정 {${events.size}건}"
        if (tasks.isNotEmpty()) parts += "할 일 {${tasks.count { !it.done }}건}"
        if (money.isNotEmpty()) parts += "금전 {${money.size}건}"
        if (parts.isNotEmpty()) append(parts.joinToString(", ") + "이 연결되어 있습니다. ")
        when {
            note != null -> append(note)
            questions.isNotEmpty() -> append("확인할 점이 {${questions.size}가지} 남아 있습니다. 아래에 답을 적어 주시면 제가 반영하겠습니다.")
            pastAnswers.isNotEmpty() -> append("확인할 점은 모두 정리되었습니다.")
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) { Text("돌아가기", color = BizColors.Muted) } }
        item {
            AssistantBand(
                kicker = listOfNotNull(r.orgName, r.person, Labels.source(r.source) + " " + TimeUtil.formatDateTime(r.createdAt)).joinToString(", "),
                voice = voice(sentence), live = working,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypePill(r.type); EntityPill(r.entity); if (r.urgency == "high") UrgencyPill(r.urgency)
                }
                Text(r.summary, style = MaterialTheme.typography.titleMedium, color = BizColors.Text)
                val where = listOfNotNull(r.projectName, r.site, Labels.system(r.system).takeIf { r.system != "none" }).joinToString(", ")
                if (where.isNotBlank()) Text(where, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
            }
        }

        val decisions = r.decisions.lines().filter { it.isNotBlank() }
        if (decisions.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    SectionTitle("결정 사항")
                    decisions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = BizColors.Text, modifier = Modifier.padding(vertical = 3.dp)) }
                }
            }
        }

        if (questions.isNotEmpty()) {
            item {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
                        .background(BizColors.Panel, RoundedCornerShape(14.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("확인이 필요한 점", style = MaterialTheme.typography.labelMedium, color = BizColors.Amber, fontWeight = FontWeight.SemiBold)
                    questions.forEach { q ->
                        Column {
                            Text(q, style = MaterialTheme.typography.bodyMedium, color = BizColors.Amber)
                            OutlinedTextField(
                                value = answers[q] ?: "",
                                onValueChange = { answers[q] = it },
                                placeholder = { Text("답을 적어 주세요. 모르면 비워 두어도 됩니다", color = BizColors.Dim) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                minLines = 1,
                            )
                        }
                    }
                    val filled = answers.filterValues { it.isNotBlank() }
                    Button(
                        onClick = {
                            working = true; note = null
                            scope.launch {
                                try {
                                    val msg = repo.answerQuestions(r.uid, filled.map { it.key to it.value.trim() })
                                    note = msg
                                    answers.clear()
                                } catch (e: Exception) {
                                    note = "반영하지 못했습니다: ${e.message}"
                                } finally { working = false }
                            }
                        },
                        enabled = filled.isNotEmpty() && !working,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(if (working) "반영하는 중" else "답 반영하기 (${filled.size}건)") }
                }
            }
        }

        if (pastAnswers.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionTitle("답한 내용")
                    pastAnswers.forEach { (q, a) ->
                        Text(q, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                        Text(a, style = MaterialTheme.typography.bodyMedium, color = BizColors.Text, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }

        if (events.isNotEmpty() || tasks.isNotEmpty() || money.isNotEmpty() || tickets.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionTitle("연결된 항목")
                    val all = events.size + tasks.size + money.size + tickets.size
                    var i = 0
                    events.forEach { e ->
                        i++
                        RailItem(last = i == all, tone = BizColors.Signal, active = e.startMillis > System.currentTimeMillis()) {
                            Text("삼성 캘린더 일정" + if (e.calendarEventId == null) " (폰에서 등록 대기)" else "", style = MaterialTheme.typography.labelSmall, color = BizColors.Signal)
                            Text(e.title, style = MaterialTheme.typography.bodyMedium, color = BizColors.Text)
                            Text(listOfNotNull(TimeUtil.formatDateTime(e.startMillis), e.place).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                        }
                    }
                    tasks.forEach { t ->
                        i++
                        RailItem(last = i == all, tone = BizColors.Muted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("할 일, ${Labels.owner(t.owner)}" + (t.due?.let { ", ${TimeUtil.formatDate(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = BizColors.Muted)
                                    Text(t.title, style = MaterialTheme.typography.bodyMedium, color = if (t.done) BizColors.Muted else BizColors.Text)
                                    if (!t.note.isNullOrBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                                }
                                Checkbox(checked = t.done, onCheckedChange = { d -> scope.launch { repo.setTaskDone(t.uid, d) } }, colors = CheckboxDefaults.colors(checkedColor = BizColors.Signal, uncheckedColor = BizColors.Line))
                            }
                        }
                    }
                    money.forEach { m ->
                        i++
                        RailItem(last = i == all, tone = BizColors.Green) {
                            Text(Labels.moneyKind(m.kind) + (m.due?.let { ", ${TimeUtil.formatDate(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = BizColors.Green)
                            Text(Labels.won(m.amount), style = MaterialTheme.typography.bodyMedium, color = BizColors.Text)
                            if (!m.note.isNullOrBlank()) Text(m.note, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                        }
                    }
                    tickets.forEach { t ->
                        i++
                        RailItem(last = i == all, tone = if (t.status == "done") BizColors.Muted else BizColors.Red) {
                            Text("장애 티켓, ${Labels.ticketStatus(t.status)}", style = MaterialTheme.typography.labelSmall, color = BizColors.Red)
                            Text(t.symptom, style = MaterialTheme.typography.bodyMedium, color = BizColors.Text)
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "원문 닫기" else "원문 보기", color = BizColors.Muted) }
                if (showRaw) {
                    Column(Modifier.fillMaxWidth().background(BizColors.Panel, RoundedCornerShape(12.dp)).padding(14.dp)) {
                        Text(r.rawText, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!r.replyDraft.isNullOrBlank()) {
                    Pill("답장 초안 있음", BizColors.Muted)
                    Text(r.replyDraft, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

private fun parseAnswers(json: String): List<Pair<String, String>> = runCatching {
    (Json.parseToJsonElement(json) as? JsonArray)?.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val q = (o["q"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val a = (o["a"] as? JsonPrimitive)?.contentOrNull ?: ""
        q to a
    } ?: emptyList()
}.getOrDefault(emptyList())

@Suppress("unused")
private val clickableHint = Modifier.clickable { }
