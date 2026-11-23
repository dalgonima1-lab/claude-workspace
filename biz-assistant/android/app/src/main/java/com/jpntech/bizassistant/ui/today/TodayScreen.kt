package com.jpntech.bizassistant.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.data.Labels
import com.jpntech.bizassistant.data.TimeUtil
import com.jpntech.bizassistant.data.db.EventEntity
import com.jpntech.bizassistant.data.db.RecordEntity
import com.jpntech.bizassistant.data.db.TaskEntity
import com.jpntech.bizassistant.ui.common.AssistantBand
import com.jpntech.bizassistant.ui.common.EmptyNote
import com.jpntech.bizassistant.ui.common.EntityPill
import com.jpntech.bizassistant.ui.common.Pill
import com.jpntech.bizassistant.ui.common.RailItem
import com.jpntech.bizassistant.ui.common.SectionTitle
import com.jpntech.bizassistant.ui.common.Stat
import com.jpntech.bizassistant.ui.common.TypePill
import com.jpntech.bizassistant.ui.common.voice
import com.jpntech.bizassistant.ui.theme.BizColors
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(expanded: Boolean, onManualInput: () -> Unit, onOpenTickets: () -> Unit) {
    val repo = BizApp.from(LocalContext.current).repository
    val scope = rememberCoroutineScope()
    val today = TimeUtil.today()
    val dayStart = TimeUtil.startOfDay(today)
    val dayEnd = TimeUtil.startOfDay(today.plusDays(1))
    val tomorrowEnd = TimeUtil.startOfDay(today.plusDays(2))

    val events by remember { repo.db.events().between(dayStart, tomorrowEnd) }.collectAsStateWithLifecycle(emptyList())
    val tasks by remember { repo.db.tasks().open() }.collectAsStateWithLifecycle(emptyList())
    val openTickets by remember { repo.db.tickets().openCount() }.collectAsStateWithLifecycle(0)
    val money by remember { repo.db.money().open() }.collectAsStateWithLifecycle(emptyList())
    val recent by remember { repo.db.records().recent(6) }.collectAsStateWithLifecycle(emptyList())

    val todayEvents = events.filter { it.startMillis < dayEnd }
    val tomorrowEvents = events.filter { it.startMillis >= dayEnd }
    val now = System.currentTimeMillis()
    val weekEnd = today.plusDays(7)
    val receivable = money.filter { it.kind == "receivable" && (TimeUtil.parseDate(it.due)?.let { d -> !d.isAfter(weekEnd) } ?: false) }.sumOf { it.amount ?: 0L }
    val dueSoon = tasks.filter { t -> TimeUtil.parseDate(t.due)?.let { !it.isAfter(weekEnd) } ?: false }.take(6)
    val dueTomorrow = tasks.count { TimeUtil.parseDate(it.due) == today.plusDays(1) }

    val sentence = buildString {
        if (recent.isEmpty() && todayEvents.isEmpty() && tasks.isEmpty()) {
            append("아직 정리한 기록이 없습니다. 통화 요약을 {공유}하거나 아래에 적어 주시면 여기서 정리해 드립니다.")
        } else {
            val next = todayEvents.firstOrNull { it.startMillis > now }
            append(if (todayEvents.isEmpty()) "오늘 잡힌 일정은 없습니다." else "오늘 일정 {${todayEvents.size}건}이 있습니다.")
            if (next != null) append(" 다음은 {${TimeUtil.formatTime(next.startMillis)} ${next.title}}입니다.")
            tomorrowEvents.firstOrNull()?.let { append(" 내일은 {${it.title}}이 잡혀 있습니다.") }
            if (dueTomorrow > 0) append(" 내일까지 할 일이 {${dueTomorrow}건} 있고,") else append("")
            if (openTickets > 0) append(if (dueTomorrow > 0) " 미완료 장애는 {${openTickets}건}입니다." else " 미완료 장애가 {${openTickets}건} 있습니다.")
            else if (dueTomorrow > 0) append(" 장애는 모두 처리되었습니다.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            item { AssistantBand(kicker = TimeUtil.formatDate(today), voice = voice(sentence), live = true) }

            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionTitle("오늘")
                    if (todayEvents.isEmpty()) EmptyNote("등록된 일정이 없습니다.")
                    todayEvents.forEachIndexed { i, e -> EventRail(e, active = e.startMillis > now && todayEvents.firstOrNull { it.startMillis > now } == e, last = i == todayEvents.lastIndex) }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionTitle("기한이 가까운 할 일")
                    if (dueSoon.isEmpty()) EmptyNote("일주일 안에 기한이 오는 할 일이 없습니다.")
                    dueSoon.forEachIndexed { i, t -> TaskRail(t, last = i == dueSoon.lastIndex) { done -> scope.launch { repo.db.tasks().setDone(t.id, done) } } }
                }
            }

            item {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat("$openTickets", null, "미완료 장애", if (openTickets > 0) BizColors.Red else BizColors.Text, Modifier.weight(1f), onClick = onOpenTickets)
                    val (v, u) = shortWon(receivable)
                    Stat(v, u, "이번 주 받을 돈", if (receivable > 0) BizColors.Green else BizColors.Text, Modifier.weight(1f))
                    Stat("${tomorrowEvents.size}", null, "내일 일정", BizColors.Text, Modifier.weight(1f))
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionTitle("최근 정리한 기록")
                    if (recent.isEmpty()) EmptyNote("아직 없습니다.")
                    recent.forEachIndexed { i, r -> RecentRail(r, last = i == recent.lastIndex) }
                }
            }
        }
        CommandBar(onClick = onManualInput, modifier = Modifier.padding(horizontal = if (expanded) 24.dp else 16.dp, vertical = 12.dp))
    }
}

private fun shortWon(amount: Long): Pair<String, String?> = when {
    amount <= 0 -> "0" to null
    amount >= 100_000_000 -> "%.1f".format(amount / 100_000_000.0) to "억"
    amount >= 10_000 -> "${amount / 10_000}" to "만"
    else -> "$amount" to "원"
}

/** 화면 아래의 입력줄. 비서에게 말을 거는 자리. */
@Composable
fun CommandBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(54.dp)
            .background(BizColors.Panel, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("무엇을 정리할까요?", color = BizColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box(Modifier.size(32.dp).background(BizColors.Signal, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Mic, contentDescription = "직접 입력", tint = BizColors.OnSignal, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EventRail(e: EventEntity, active: Boolean, last: Boolean) {
    RailItem(last = last, tone = if (active) BizColors.Signal else BizColors.Muted, active = active) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(TimeUtil.formatTime(e.startMillis), style = MaterialTheme.typography.headlineSmall, color = BizColors.Text, modifier = Modifier.width(66.dp))
            Column {
                Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = BizColors.Text)
                if (!e.place.isNullOrBlank()) Text(e.place, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
            }
        }
    }
}

@Composable
private fun TaskRail(t: TaskEntity, last: Boolean, onDone: (Boolean) -> Unit) {
    val dd = TimeUtil.dDay(t.due)
    val urgent = dd == "D-Day" || dd == "D-1" || (dd?.startsWith("D+") ?: false)
    RailItem(last = last, tone = if (urgent) BizColors.Red else BizColors.Muted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = BizColors.Text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (dd != null) Pill(dueLabel(dd), if (urgent) BizColors.Red else BizColors.Amber)
                    Text(listOfNotNull(Labels.owner(t.owner).takeIf { t.owner != "me" }, t.note).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                }
            }
            Checkbox(checked = t.done, onCheckedChange = onDone, colors = CheckboxDefaults.colors(checkedColor = BizColors.Signal, uncheckedColor = BizColors.Line))
        }
    }
}

private fun dueLabel(dd: String): String = when {
    dd == "D-Day" -> "오늘까지"
    dd == "D-1" -> "내일까지"
    dd.startsWith("D+") -> "${dd.drop(2)}일 지남"
    else -> "${dd.drop(2)}일 남음"
}

@Composable
private fun RecentRail(r: RecordEntity, last: Boolean) {
    RailItem(last = last, tone = if (r.urgency == "high") BizColors.Red else BizColors.Muted) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TypePill(r.type)
            EntityPill(r.entity)
            Spacer(Modifier.weight(1f))
            Text(TimeUtil.relative(r.createdAt), style = MaterialTheme.typography.labelSmall, color = BizColors.Muted)
        }
        Text(listOfNotNull(r.orgName, r.person).joinToString(" ").ifBlank { "상대 미상" }, style = MaterialTheme.typography.labelMedium, color = BizColors.Muted, modifier = Modifier.padding(top = 6.dp))
        Text(r.summary, style = MaterialTheme.typography.bodyMedium, color = BizColors.Text)
    }
}
