package com.jpntech.bizassistant.ui.orgs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.jpntech.bizassistant.data.db.OrgEntity
import com.jpntech.bizassistant.data.db.RecordEntity
import com.jpntech.bizassistant.ui.common.AssistantBand
import com.jpntech.bizassistant.ui.common.EmptyNote
import com.jpntech.bizassistant.ui.common.EntityFilterRow
import com.jpntech.bizassistant.ui.common.EntityPill
import com.jpntech.bizassistant.ui.common.Pill
import com.jpntech.bizassistant.ui.common.RailItem
import com.jpntech.bizassistant.ui.common.SectionTitle
import com.jpntech.bizassistant.ui.common.TypePill
import com.jpntech.bizassistant.ui.common.voice
import com.jpntech.bizassistant.ui.theme.BizColors

private const val STALE_DAYS = 14L

/** 펼친 화면: 왼쪽 거래처 목록, 오른쪽 타임라인. 접은 화면: 목록에서 고르면 타임라인으로 넘어간다. */
@Composable
fun OrgsScreen(expanded: Boolean, onOpenRecord: (String) -> Unit = {}) {
    val repo = BizApp.from(LocalContext.current).repository
    val orgs by remember { repo.db.orgs().all() }.collectAsStateWithLifecycle(emptyList())
    var selectedUid by rememberSaveable { mutableStateOf<String?>(null) }
    var entityFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = orgs.firstOrNull { it.uid == selectedUid }

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(340.dp).fillMaxHeight()) { OrgList(orgs, selectedUid, onSelect = { selectedUid = it }) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(BizColors.LineSoft))
            Box(Modifier.fillMaxSize()) {
                if (selected == null) EmptyNote("왼쪽에서 거래처를 고르면 기록이 여기 나옵니다.", Modifier.padding(20.dp))
                else OrgTimeline(selected, entityFilter, onFilter = { entityFilter = it }, onOpenRecord = onOpenRecord)
            }
        }
    } else {
        if (selected == null) {
            OrgList(orgs, null, onSelect = { selectedUid = it })
        } else {
            BackHandler { selectedUid = null }
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { selectedUid = null }, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) { Text("거래처 목록으로", color = BizColors.Muted) }
                OrgTimeline(selected, entityFilter, onFilter = { entityFilter = it }, onOpenRecord = onOpenRecord)
            }
        }
    }
}

private fun daysSince(at: Long?): Long? = at?.let { (System.currentTimeMillis() - it) / (24L * 3600 * 1000) }

private fun orgsSentence(orgs: List<OrgEntity>): String {
    if (orgs.isEmpty()) return "아직 기억하는 거래처가 없습니다. 첫 통화를 정리하면 여기에 자동으로 생깁니다."
    val stale = orgs.filter { (daysSince(it.lastContactAt) ?: 0L) >= STALE_DAYS }.sortedByDescending { daysSince(it.lastContactAt) }
    val clients = orgs.count { it.kind == "client" }
    val sb = StringBuilder("거래처 {${orgs.size}곳}을 기억하고 있습니다.")
    if (clients > 0) sb.append(" 발주처가 ${clients}곳입니다.")
    stale.firstOrNull()?.let { sb.append(" {${it.name}}와는 ${daysSince(it.lastContactAt)}일째 연락이 없습니다.") }
    if (stale.size > 1) sb.append(" 오래 연락하지 않은 곳이 ${stale.size}곳입니다.")
    return sb.toString()
}

@Composable
private fun OrgList(orgs: List<OrgEntity>, selectedUid: String?, onSelect: (String) -> Unit) {
    val groups = listOf("client" to "발주처", "partner" to "협력업체", "vendor" to "자재와 외주", "internal" to "내부", "other" to "기타")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { AssistantBand(kicker = "거래처", voice = voice(orgsSentence(orgs))) }
        groups.forEach { (kind, title) ->
            val list = orgs.filter { it.kind == kind }
            if (list.isNotEmpty()) {
                item { SectionTitle(title, Modifier.padding(start = 20.dp, top = 12.dp)) }
                items(list.size) { i -> Box(Modifier.padding(horizontal = 20.dp)) { OrgRail(list[i], list[i].uid == selectedUid, last = i == list.lastIndex, onClick = { onSelect(list[i].uid) }) } }
            }
        }
    }
}

@Composable
private fun OrgRail(o: OrgEntity, selected: Boolean, last: Boolean, onClick: () -> Unit) {
    val days = daysSince(o.lastContactAt)
    val stale = (days ?: 0L) >= STALE_DAYS
    val tone = when { selected -> BizColors.Signal; stale -> BizColors.Amber; else -> BizColors.Muted }
    RailItem(last = last, tone = tone, active = selected, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = BizColors.Text, modifier = Modifier.weight(1f))
            val rel = o.lastContactAt?.let { TimeUtil.relative(it) } ?: "기록 없음"
            Text(rel, style = MaterialTheme.typography.labelSmall, color = if (stale) BizColors.Amber else if (rel == "오늘") BizColors.Signal else BizColors.Muted)
        }
        if (!o.lastPerson.isNullOrBlank()) Text(o.lastPerson, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
    }
}

@Composable
private fun OrgTimeline(org: OrgEntity, entityFilter: String?, onFilter: (String?) -> Unit, onOpenRecord: (String) -> Unit) {
    val repo = BizApp.from(LocalContext.current).repository
    val records by remember(org.uid) { repo.db.records().forOrg(org.uid) }.collectAsStateWithLifecycle(emptyList())
    val events by remember(org.uid) { repo.db.events().forOrg(org.uid) }.collectAsStateWithLifecycle(emptyList())
    val tasks by remember(org.uid) { repo.db.tasks().forOrg(org.uid) }.collectAsStateWithLifecycle(emptyList())
    val money by remember(org.uid) { repo.db.money().forOrg(org.uid) }.collectAsStateWithLifecycle(emptyList())
    val shown = if (entityFilter == null) records else records.filter { it.entity == entityFilter }

    val openTasks = tasks.count { !it.done }
    val receivable = money.filter { it.kind == "receivable" && !it.settled }.sumOf { it.amount ?: 0L }
    val nextEvent = events.filter { it.startMillis > System.currentTimeMillis() }.minByOrNull { it.startMillis }
    val sentence = buildString {
        val last = org.lastContactAt?.let { TimeUtil.relative(it) }
        append(if (last == null) "{${org.name}}의 기록입니다." else "{${org.name}}${org.lastPerson?.let { " $it" } ?: ""}와 ${if (last == "오늘") "오늘" else "${last}에"} 마지막으로 연락했습니다.")
        if (nextEvent != null) append(" 다음 일정은 {${TimeUtil.formatDateTime(nextEvent.startMillis)} ${nextEvent.title}}입니다.")
        if (openTasks > 0) append(" 남은 할 일이 {${openTasks}건}")
        if (receivable > 0) append(if (openTasks > 0) ", 받을 돈이 {${Labels.won(receivable)}} 있습니다." else " 받을 돈이 {${Labels.won(receivable)}} 있습니다.")
        else if (openTasks > 0) append(" 있습니다.")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { AssistantBand(kicker = Labels.orgKind(org.kind), voice = voice(sentence)) }
        item { EntityFilterRow(selected = entityFilter, onSelect = onFilter, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        if (shown.isEmpty()) item { EmptyNote("기록이 없습니다.", Modifier.padding(horizontal = 20.dp)) }
        items(shown.size) { i ->
            val r = shown[i]
            Box(Modifier.padding(horizontal = 20.dp)) {
                RecordRail(
                    r = r,
                    last = i == shown.lastIndex,
                    onOpen = { onOpenRecord(r.uid) },
                    eventLabels = events.filter { it.recordUid == r.uid }.map { TimeUtil.formatDateTime(it.startMillis) + "  " + it.title },
                    taskLabels = tasks.filter { it.recordUid == r.uid }.map { (if (it.done) "완료  " else "") + it.title + (it.due?.let { d -> ", " + TimeUtil.formatDate(d) } ?: "") },
                    moneyLabels = money.filter { it.recordUid == r.uid }.map { Labels.moneyKind(it.kind) + "  " + Labels.won(it.amount) + (it.due?.let { d -> ", " + TimeUtil.formatDate(d) } ?: "") },
                )
            }
        }
    }
}

@Composable
private fun RecordRail(r: RecordEntity, last: Boolean, onOpen: () -> Unit, eventLabels: List<String>, taskLabels: List<String>, moneyLabels: List<String>) {
    val open = false
    RailItem(last = last, tone = if (r.urgency == "high") BizColors.Red else BizColors.Muted, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Labels.source(r.source) + "  " + TimeUtil.formatDateTime(r.createdAt), style = MaterialTheme.typography.labelSmall, color = BizColors.Muted, modifier = Modifier.weight(1f))
            TypePill(r.type)
            EntityPill(r.entity)
        }
        Text(r.summary, style = MaterialTheme.typography.bodyMedium, color = BizColors.Text, modifier = Modifier.padding(top = 6.dp))
        if (!r.projectName.isNullOrBlank() || !r.site.isNullOrBlank()) {
            Text(listOfNotNull(r.projectName, r.site).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        }
        if (r.decisions.isNotBlank()) Column(Modifier.padding(top = 6.dp)) { r.decisions.lines().forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = BizColors.Text) } }
        if (eventLabels.isNotEmpty() || taskLabels.isNotEmpty() || moneyLabels.isNotEmpty()) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                eventLabels.forEach { Pill(it, BizColors.Signal) }
                taskLabels.forEach { Pill(it, BizColors.Muted) }
                moneyLabels.forEach { Pill(it, BizColors.Green) }
            }
        }
        val q = r.questions.lines().count { it.isNotBlank() }
        if (q > 0) Text("확인할 점 ${q}가지, 눌러서 답하기", style = MaterialTheme.typography.labelSmall, color = BizColors.Amber, modifier = Modifier.padding(top = 6.dp))
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text("원문", style = MaterialTheme.typography.labelSmall, color = BizColors.Muted)
            Text(r.rawText, style = MaterialTheme.typography.bodySmall, color = BizColors.Dim)
        }
    }
}
