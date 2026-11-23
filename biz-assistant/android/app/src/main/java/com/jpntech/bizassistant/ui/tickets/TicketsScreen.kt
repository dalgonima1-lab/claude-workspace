package com.jpntech.bizassistant.ui.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.jpntech.bizassistant.ui.common.EmptyNote
import com.jpntech.bizassistant.ui.common.EntityFilterRow
import com.jpntech.bizassistant.ui.common.Tag
import com.jpntech.bizassistant.ui.theme.BizColors
import kotlinx.coroutines.launch

/** 장애·AS 보드. 펼친 화면은 접수/방문 예정/조치 중/완료 네 칸, 접은 화면은 세로 목록. */
@Composable
fun TicketsScreen(expanded: Boolean) {
    val repo = BizApp.from(LocalContext.current).repository
    val scope = rememberCoroutineScope()
    val all by remember { repo.db.tickets().all() }.collectAsStateWithLifecycle(emptyList())
    var entityFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val tickets = if (entityFilter == null) all else all.filter { it.entity == entityFilter }
    val advance: (TicketEntity) -> Unit = { t -> scope.launch { repo.advanceTicket(t) } }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("장애 · AS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("미완료 ${tickets.count { it.status != "done" }}건", color = BizColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        EntityFilterRow(selected = entityFilter, onSelect = { entityFilter = it })
        if (expanded) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Labels.ticketStatuses.forEach { (code, title) ->
                    val list = tickets.filter { it.status == code }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { ColumnHeader(title, list.size) }
                        if (list.isEmpty()) item { EmptyNote("없음") }
                        items(list.size) { i -> TicketCard(list[i], onAdvance = advance) }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Labels.ticketStatuses.forEach { (code, title) ->
                    val list = tickets.filter { it.status == code }
                    item { ColumnHeader(title, list.size) }
                    if (list.isEmpty()) item { EmptyNote("없음") }
                    items(list.size) { i -> TicketCard(list[i], onAdvance = advance) }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = BizColors.Ink, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = BizColors.Muted)
    }
}

@Composable
private fun TicketCard(t: TicketEntity, onAdvance: (TicketEntity) -> Unit) {
    val bar: Color = when {
        t.status == "done" -> Color(0xFF7A838F)
        t.urgency == "high" -> BizColors.Danger
        t.urgency == "low" -> Color(0xFF7A838F)
        else -> BizColors.Navy
    }
    Row(Modifier.fillMaxWidth().background(BizColors.Surface, RoundedCornerShape(12.dp)).border(1.dp, BizColors.Border, RoundedCornerShape(12.dp))) {
        Column(Modifier.padding(start = 4.dp).background(bar).fillMaxWidth(0.012f)) { Text(" ") }
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("T-${t.id} · ${TimeUtil.relative(t.createdAt)}", style = MaterialTheme.typography.labelSmall, color = BizColors.Muted, modifier = Modifier.weight(1f))
                Text(Labels.urgency(t.urgency), style = MaterialTheme.typography.labelSmall, color = bar, fontWeight = FontWeight.Bold)
            }
            Text(listOfNotNull(t.site, Labels.system(t.system).takeIf { t.system != "none" }).joinToString(" · ").ifBlank { "현장 미상" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(t.symptom, style = MaterialTheme.typography.bodySmall, color = BizColors.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(t.entity)
                if (!t.orgName.isNullOrBlank()) Tag(t.orgName)
            }
            if (t.status != "done") {
                val next = when (t.status) { "open" -> "방문 일정 잡음"; "scheduled" -> "조치 시작"; else -> "조치 완료" }
                Button(onClick = { onAdvance(t) }, modifier = Modifier.fillMaxWidth()) { Text(next) }
            } else if (t.closedAt != null) {
                Text("완료 " + TimeUtil.formatDateTime(t.closedAt), style = MaterialTheme.typography.labelSmall, color = BizColors.Ok)
            }
        }
    }
}
