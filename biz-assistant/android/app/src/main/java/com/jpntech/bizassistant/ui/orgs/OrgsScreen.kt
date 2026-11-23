package com.jpntech.bizassistant.ui.orgs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jpntech.bizassistant.ui.common.EmptyNote
import com.jpntech.bizassistant.ui.common.EntityFilterRow
import com.jpntech.bizassistant.ui.common.EntityTag
import com.jpntech.bizassistant.ui.common.Tag
import com.jpntech.bizassistant.ui.common.TypeTag
import com.jpntech.bizassistant.ui.common.UrgencyTag
import com.jpntech.bizassistant.ui.theme.BizColors

/** 펼친 화면: 왼쪽 거래처 목록 + 오른쪽 타임라인. 접은 화면: 목록 → 상세 전환. */
@Composable
fun OrgsScreen(expanded: Boolean) {
    val repo = BizApp.from(LocalContext.current).repository
    val orgs by remember { repo.db.orgs().all() }.collectAsStateWithLifecycle(emptyList())
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var entityFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = orgs.firstOrNull { it.id == selectedId }

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(340.dp).fillMaxHeight().background(BizColors.Surface)) {
                OrgList(orgs, selectedId, onSelect = { selectedId = it })
            }
            Box(Modifier.fillMaxSize().background(BizColors.Border).padding(start = 1.dp)) {
                Box(Modifier.fillMaxSize().background(BizColors.Ground)) {
                    if (selected == null) EmptyNote("왼쪽에서 거래처를 고르세요.") else OrgTimeline(selected, entityFilter, onFilter = { entityFilter = it })
                }
            }
        }
    } else {
        if (selected == null) {
            Column(Modifier.fillMaxSize().background(BizColors.Surface)) { OrgList(orgs, null, onSelect = { selectedId = it }) }
        } else {
            BackHandler { selectedId = null }
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { selectedId = null }) { Text("← 거래처 목록") }
                OrgTimeline(selected, entityFilter, onFilter = { entityFilter = it })
            }
        }
    }
}

@Composable
private fun OrgList(orgs: List<OrgEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    val groups = listOf("client" to "발주처", "partner" to "협력업체", "vendor" to "자재 · 외주", "internal" to "내부", "other" to "기타")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        item {
            Text("거래처", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
            Text("최근 연락 순 · ${orgs.size}곳", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (orgs.isEmpty()) item { EmptyNote("아직 거래처가 없습니다. 첫 통화 요약을 등록하면 자동으로 생깁니다.") }
        groups.forEach { (kind, title) ->
            val list = orgs.filter { it.kind == kind }
            if (list.isNotEmpty()) {
                item { Text(title, style = MaterialTheme.typography.labelMedium, color = BizColors.Muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp)) }
                items(list.size) { i -> OrgRow(list[i], list[i].id == selectedId, onClick = { onSelect(list[i].id) }) }
            }
        }
    }
}

@Composable
private fun OrgRow(o: OrgEntity, selected: Boolean, onClick: () -> Unit) {
    val stale = o.lastContactAt?.let { System.currentTimeMillis() - it > 14L * 24 * 3600 * 1000 } ?: false
    Column(
        Modifier.fillMaxWidth().padding(vertical = 1.dp)
            .background(if (selected) BizColors.NavySoft else BizColors.Surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
            val rel = o.lastContactAt?.let { TimeUtil.relative(it) } ?: ""
            Text(rel, style = MaterialTheme.typography.labelSmall, color = if (stale) BizColors.Warn else if (rel == "오늘") BizColors.Navy else BizColors.Muted, fontWeight = FontWeight.Bold)
        }
        if (!o.lastPerson.isNullOrBlank()) Text(o.lastPerson, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
    }
}

@Composable
private fun OrgTimeline(org: OrgEntity, entityFilter: String?, onFilter: (String?) -> Unit) {
    val repo = BizApp.from(LocalContext.current).repository
    val records by remember(org.id) { repo.db.records().forOrg(org.id) }.collectAsStateWithLifecycle(emptyList())
    val events by remember(org.id) { repo.db.events().forOrg(org.id) }.collectAsStateWithLifecycle(emptyList())
    val tasks by remember(org.id) { repo.db.tasks().forOrg(org.id) }.collectAsStateWithLifecycle(emptyList())
    val money by remember(org.id) { repo.db.money().forOrg(org.id) }.collectAsStateWithLifecycle(emptyList())
    val shown = if (entityFilter == null) records else records.filter { it.entity == entityFilter }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(BizColors.Surface, RoundedCornerShape(14.dp)).border(1.dp, BizColors.Border, RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Labels.orgKind(org.kind), style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                Text(org.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(org.lastPerson, org.lastContactAt?.let { "마지막 연락 " + TimeUtil.formatDateTime(it) }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = BizColors.Muted)
                Spacer(Modifier.padding(2.dp))
                EntityFilterRow(selected = entityFilter, onSelect = onFilter)
            }
        }
        if (shown.isEmpty()) item { EmptyNote("기록이 없습니다.") }
        items(shown.size) { i ->
            val r = shown[i]
            RecordCard(
                r = r,
                eventLabels = events.filter { it.recordId == r.id }.map { TimeUtil.formatDateTime(it.startMillis) + " " + it.title },
                taskLabels = tasks.filter { it.recordId == r.id }.map { (if (it.done) "완료 · " else "") + it.title + " (" + Labels.owner(it.owner) + (it.due?.let { d -> ", " + TimeUtil.formatDate(d) } ?: "") + ")" },
                moneyLabels = money.filter { it.recordId == r.id }.map { Labels.moneyKind(it.kind) + " " + Labels.won(it.amount) + (it.due?.let { d -> " · " + TimeUtil.formatDate(d) } ?: "") },
            )
        }
    }
}

@Composable
private fun RecordCard(r: RecordEntity, eventLabels: List<String>, taskLabels: List<String>, moneyLabels: List<String>) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(BizColors.Surface, RoundedCornerShape(14.dp)).border(1.dp, BizColors.Border, RoundedCornerShape(14.dp)).clickable { open = !open }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Labels.source(r.source) + " · " + TimeUtil.formatDateTime(r.createdAt), style = MaterialTheme.typography.labelMedium, color = BizColors.Muted, modifier = Modifier.weight(1f))
            TypeTag(r.type)
            if (r.urgency == "high") UrgencyTag(r.urgency)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EntityTag(r.entity)
            if (!r.projectName.isNullOrBlank()) Tag(r.projectName)
            if (!r.site.isNullOrBlank()) Tag(r.site)
        }
        Text(r.summary, style = MaterialTheme.typography.bodyMedium)
        if (r.decisions.isNotBlank()) r.decisions.lines().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        if (r.questions.isNotBlank()) r.questions.lines().forEach { Text("? $it", style = MaterialTheme.typography.bodySmall, color = BizColors.Warn) }
        eventLabels.forEach { Tag(it, BizColors.NavySoft, BizColors.Navy) }
        taskLabels.forEach { Tag(it) }
        moneyLabels.forEach { Tag(it, BizColors.OkSoft, BizColors.Ok) }
        if (open) {
            Text("원문", style = MaterialTheme.typography.labelMedium, color = BizColors.Muted)
            Text(r.rawText, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        }
    }
}
