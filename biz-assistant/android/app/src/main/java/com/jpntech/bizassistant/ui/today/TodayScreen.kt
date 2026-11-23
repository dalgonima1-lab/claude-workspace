package com.jpntech.bizassistant.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.jpntech.bizassistant.data.db.MoneyEntity
import com.jpntech.bizassistant.data.db.RecordEntity
import com.jpntech.bizassistant.data.db.TaskEntity
import com.jpntech.bizassistant.ui.common.EmptyNote
import com.jpntech.bizassistant.ui.common.EntityTag
import com.jpntech.bizassistant.ui.common.SectionTitle
import com.jpntech.bizassistant.ui.common.TypeTag
import com.jpntech.bizassistant.ui.theme.BizColors
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(expanded: Boolean, onManualInput: () -> Unit, onOpenTickets: () -> Unit) {
    val repo = BizApp.from(LocalContext.current).repository
    val scope = rememberCoroutineScope()
    val today = TimeUtil.today()
    val dayStart = TimeUtil.startOfDay(today)
    val dayEnd = TimeUtil.startOfDay(today.plusDays(1))

    val events by remember { repo.db.events().between(dayStart, dayEnd) }.collectAsStateWithLifecycle(emptyList())
    val tasks by remember { repo.db.tasks().open() }.collectAsStateWithLifecycle(emptyList())
    val openTickets by remember { repo.db.tickets().openCount() }.collectAsStateWithLifecycle(0)
    val money by remember { repo.db.money().open() }.collectAsStateWithLifecycle(emptyList())
    val recent by remember { repo.db.records().recent(5) }.collectAsStateWithLifecycle(emptyList())

    val weekEnd = today.plusDays(7)
    val receivable = money.filter { it.kind == "receivable" && (TimeUtil.parseDate(it.due)?.let { d -> !d.isAfter(weekEnd) } ?: false) }.sumOf { it.amount ?: 0L }
    val dueSoon = tasks.filter { t -> TimeUtil.parseDate(t.due)?.let { !it.isAfter(weekEnd) } ?: false }.take(5)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text(TimeUtil.formatDate(today), color = BizColors.Muted, style = MaterialTheme.typography.bodySmall)
                    Text("오늘", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            item {
                SectionTitle("오늘 일정")
                Panel {
                    if (events.isEmpty()) EmptyNote("오늘 등록된 일정이 없습니다.")
                    events.forEachIndexed { i, e -> EventRow(e, last = i == events.lastIndex) }
                }
            }
            item {
                SectionTitle("기한 임박 할 일 (7일 이내)")
                Panel {
                    if (dueSoon.isEmpty()) EmptyNote("일주일 안에 기한이 오는 할 일이 없습니다.")
                    dueSoon.forEachIndexed { i, t -> TaskRow(t, last = i == dueSoon.lastIndex) { done -> scope.launch { repo.db.tasks().setDone(t.id, done) } } }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("미완료 장애", "${openTickets}건", if (openTickets > 0) BizColors.Danger else BizColors.Ink, Modifier.weight(1f).clickable(onClick = onOpenTickets))
                    StatCard("이번 주 받을 돈", if (receivable > 0) shortWon(receivable) else "없음", BizColors.Ok, Modifier.weight(1f))
                }
            }
            item {
                SectionTitle("최근 기록")
                Panel {
                    if (recent.isEmpty()) EmptyNote("아직 기록이 없습니다. 통화 요약을 공유하거나 직접 입력해 보세요.")
                    recent.forEachIndexed { i, r -> RecentRow(r, last = i == recent.lastIndex) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onManualInput,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("직접 입력") },
            containerColor = BizColors.Ink,
            contentColor = BizColors.Surface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(if (expanded) 24.dp else 16.dp),
        )
    }
}

private fun shortWon(amount: Long): String = when {
    amount >= 100_000_000 -> "%.1f억".format(amount / 100_000_000.0)
    amount >= 10_000 -> "${amount / 10_000}만"
    else -> "${amount}원"
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(BizColors.Surface, RoundedCornerShape(14.dp)).border(1.dp, BizColors.Border, RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(BizColors.Border))

@Composable
private fun EventRow(e: EventEntity, last: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(TimeUtil.formatTime(e.startMillis), color = BizColors.Navy, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!e.place.isNullOrBlank()) Text(e.place, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        }
    }
    if (!last) Divider()
}

@Composable
private fun TaskRow(t: TaskEntity, last: Boolean, onDone: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = t.done, onCheckedChange = onDone)
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(Labels.owner(t.owner) + (t.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        }
        val dd = TimeUtil.dDay(t.due)
        if (dd != null) Text(dd, color = if (dd.startsWith("D+") || dd == "D-Day" || dd == "D-1") BizColors.Danger else BizColors.Warn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp))
    }
    if (!last) Divider()
}

@Composable
private fun RecentRow(r: RecordEntity, last: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeTag(r.type)
            EntityTag(r.entity)
            Spacer(Modifier.weight(1f))
            Text(TimeUtil.relative(r.createdAt), style = MaterialTheme.typography.labelSmall, color = BizColors.Muted)
        }
        Text((r.orgName ?: "상대 미상") + (r.person?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = BizColors.Muted)
        Text(r.summary, style = MaterialTheme.typography.bodyMedium)
    }
    if (!last) Divider()
}

@Composable
private fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(BizColors.Surface, RoundedCornerShape(14.dp)).border(1.dp, BizColors.Border, RoundedCornerShape(14.dp)).padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Suppress("unused")
private fun MoneyEntity.label() = Labels.moneyKind(kind)
