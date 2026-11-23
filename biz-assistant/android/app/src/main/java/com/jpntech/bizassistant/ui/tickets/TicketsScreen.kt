package com.jpntech.bizassistant.ui.tickets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.data.Labels
import com.jpntech.bizassistant.data.TimeUtil
import com.jpntech.bizassistant.data.db.TicketEntity
import com.jpntech.bizassistant.ui.common.AssistantBand
import com.jpntech.bizassistant.ui.common.EmptyNote
import com.jpntech.bizassistant.ui.common.EntityFilterRow
import com.jpntech.bizassistant.ui.common.EntityPill
import com.jpntech.bizassistant.ui.common.RailItem
import com.jpntech.bizassistant.ui.common.SectionTitle
import com.jpntech.bizassistant.ui.common.voice
import com.jpntech.bizassistant.ui.theme.BizColors
import kotlinx.coroutines.launch

private fun ticketsSentence(all: List<TicketEntity>): String {
    val open = all.filter { it.status != "done" }
    if (all.isEmpty()) return "접수된 장애가 없습니다. 통화에서 장애 신고를 찾으면 여기에 티켓으로 올려 두겠습니다."
    if (open.isEmpty()) return "미완료 장애가 없습니다. 이번 달 완료 {${all.count { it.status == "done" }}건}입니다."
    val sb = StringBuilder("미완료 장애 {${open.size}건}입니다.")
    val urgent = open.filter { it.urgency == "high" }
    if (urgent.isNotEmpty()) sb.append(" 그중 긴급이 {${urgent.size}건}")
    val waiting = open.filter { it.status == "open" }
    if (waiting.isNotEmpty()) sb.append(if (urgent.isNotEmpty()) ", 아직 방문 일정이 없는 건이 {${waiting.size}건}입니다." else " 아직 방문 일정이 없는 건이 {${waiting.size}건}입니다.")
    else if (urgent.isNotEmpty()) sb.append("입니다.")
    return sb.toString()
}

/** 장애와 AS 보드. 펼치면 네 칸, 접으면 세로로 이어진다. */
@Composable
fun TicketsScreen(expanded: Boolean) {
    val repo = BizApp.from(LocalContext.current).repository
    val scope = rememberCoroutineScope()
    val all by remember { repo.db.tickets().all() }.collectAsStateWithLifecycle(emptyList())
    var entityFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val tickets = if (entityFilter == null) all else all.filter { it.entity == entityFilter }
    val advance: (TicketEntity) -> Unit = { t -> scope.launch { repo.advanceTicket(t) } }

    Column(Modifier.fillMaxSize()) {
        AssistantBand(kicker = "장애와 AS", voice = voice(ticketsSentence(tickets)))
        EntityFilterRow(selected = entityFilter, onSelect = { entityFilter = it }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (expanded) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Labels.ticketStatuses.forEach { (code, title) ->
                    val list = tickets.filter { it.status == code }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item { SectionTitle("$title  ${list.size}") }
                        if (list.isEmpty()) item { EmptyNote("없음") }
                        items(list.size) { i -> TicketRail(list[i], last = i == list.lastIndex, onAdvance = advance) }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) {
                Labels.ticketStatuses.forEach { (code, title) ->
                    val list = tickets.filter { it.status == code }
                    item { SectionTitle("$title  ${list.size}", Modifier.padding(top = 12.dp)) }
                    if (list.isEmpty()) item { EmptyNote("없음") }
                    items(list.size) { i -> TicketRail(list[i], last = i == list.lastIndex, onAdvance = advance) }
                }
            }
        }
    }
}

@Composable
private fun TicketRail(t: TicketEntity, last: Boolean, onAdvance: (TicketEntity) -> Unit) {
    val tone: Color = when {
        t.status == "done" -> BizColors.Muted
        t.urgency == "high" -> BizColors.Red
        t.urgency == "low" -> BizColors.Muted
        else -> BizColors.Signal
    }
    RailItem(last = last, tone = tone, active = t.status != "done" && t.urgency == "high") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(TimeUtil.relative(t.createdAt), style = MaterialTheme.typography.labelSmall, color = BizColors.Muted, modifier = Modifier.weight(1f))
            Text(Labels.urgency(t.urgency), style = MaterialTheme.typography.labelSmall, color = tone, fontWeight = FontWeight.SemiBold)
        }
        Text(
            listOfNotNull(t.site, Labels.system(t.system).takeIf { t.system != "none" }).joinToString(", ").ifBlank { "현장 미상" },
            style = MaterialTheme.typography.titleMedium, color = BizColors.Text, modifier = Modifier.padding(top = 2.dp),
        )
        Text(t.symptom, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            EntityPill(t.entity)
            if (!t.orgName.isNullOrBlank()) Text(t.orgName, style = MaterialTheme.typography.labelSmall, color = BizColors.Muted, modifier = Modifier.align(Alignment.CenterVertically))
        }
        if (t.status != "done") {
            val next = when (t.status) { "open" -> "방문 일정 잡음"; "scheduled" -> "조치 시작"; else -> "조치 완료" }
            OutlinedButton(
                onClick = { onAdvance(t) },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BizColors.Signal),
            ) { Text(next) }
        } else if (t.closedAt != null) {
            Text("완료  " + TimeUtil.formatDateTime(t.closedAt), style = MaterialTheme.typography.labelSmall, color = BizColors.Green, modifier = Modifier.padding(top = 6.dp))
        }
        Box {}
    }
}
