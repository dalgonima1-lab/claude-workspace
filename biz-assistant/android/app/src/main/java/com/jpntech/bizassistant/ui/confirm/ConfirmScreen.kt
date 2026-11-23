package com.jpntech.bizassistant.ui.confirm

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import com.jpntech.bizassistant.ui.common.AssistantBand
import com.jpntech.bizassistant.ui.common.voice
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.data.ConfirmDraft
import com.jpntech.bizassistant.data.Labels
import com.jpntech.bizassistant.data.TimeUtil
import com.jpntech.bizassistant.data.settings.ENTITIES
import com.jpntech.bizassistant.ui.common.Tag
import com.jpntech.bizassistant.ui.theme.BizColors

/**
 * 공유 수신 또는 직접 입력 뒤에 나오는 확인 카드.
 * 분석 중 → 수정 가능한 카드 → 등록 완료 순서로 바뀐다.
 */
@Composable
fun ConfirmScreen(vm: ConfirmViewModel, onFinished: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        ConfirmState.Idle -> Box(Modifier.fillMaxSize())
        is ConfirmState.Analyzing -> AnalyzingView(s.text)
        is ConfirmState.Failed -> FailedView(s, onRetry = vm::retry, onClose = onFinished)
        is ConfirmState.Ready -> ReadyView(s, vm)
        is ConfirmState.Saved -> SavedView(s, onClose = onFinished)
        ConfirmState.Discarded -> DiscardedView(onClose = onFinished)
    }
}

private val ANALYZE_STEPS = listOf("내용 읽기", "누구와의 대화인지 확인", "일정과 약속 찾기", "금액과 할 일 정리", "거래처 기록과 대조")

/** 비서가 전사본을 훑어 내려가는 장면. 서버 응답이 올 때까지 단계 안내가 천천히 진행된다. */
@Composable
private fun AnalyzingView(text: String) {
    val lines = remember(text) { text.lines().filter { it.isNotBlank() }.take(16) }
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "scanline",
    )
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (step < ANALYZE_STEPS.lastIndex) { delay(2600); step++ }
    }
    val msg = when (step) {
        0 -> "내용을 읽고 있습니다."
        1 -> "누구와의 대화인지 확인하고 있습니다."
        2 -> "날짜와 약속을 찾고 있습니다."
        3 -> "금액과 할 일을 정리하고 있습니다."
        else -> "거래처 기록과 맞춰 보고 있습니다. 거의 끝났습니다."
    }
    Column(Modifier.fillMaxSize()) {
        AssistantBand(kicker = "분석 중", voice = voice(msg), live = true)
        Box(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().weight(1f, fill = false)
                .background(BizColors.Panel, RoundedCornerShape(12.dp)).padding(16.dp),
        ) {
            Column {
                lines.forEachIndexed { i, line ->
                    val lit = i.toFloat() / lines.size.coerceAtLeast(1) < progress
                    Text(line, style = MaterialTheme.typography.bodySmall, color = if (lit) BizColors.Text else BizColors.Dim, maxLines = 2)
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = (progress * (lines.size * 18)).dp).height(2.dp)
                    .background(BizColors.Signal.copy(alpha = 0.9f)),
            )
        }
        Column(Modifier.padding(20.dp)) {
            ANALYZE_STEPS.forEachIndexed { i, label ->
                val tone = when { i < step -> BizColors.Green; i == step -> BizColors.Signal; else -> BizColors.Line }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                    Box(Modifier.size(7.dp).background(tone, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = when { i < step -> BizColors.Text; i == step -> BizColors.Signal; else -> BizColors.Muted })
                }
            }
        }
    }
}

@Composable
private fun FailedView(s: ConfirmState.Failed, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("분석하지 못했습니다", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(s.message, color = BizColors.Danger)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text("다시 시도") }
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        Spacer(Modifier.height(16.dp))
        Text("서버 주소와 토큰은 설정 화면에서 확인할 수 있습니다.", color = BizColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SavedView(s: ConfirmState.Saved, onClose: () -> Unit) {
    val r = s.result
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("등록했습니다", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("캘린더 일정 ${r.calendarWritten}건" + if (r.calendarSkipped > 0) " (${r.calendarSkipped}건은 캘린더에 쓰지 못해 앱에만 저장)" else "")
        Text("할 일 ${r.tasksSaved}건")
        if (r.ticketCreated) Text("장애 티켓 1건")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose) { Text("닫기") }
    }
}

@Composable
private fun DiscardedView(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("저장하지 않았습니다", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose) { Text("닫기") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyView(s: ConfirmState.Ready, vm: ConfirmViewModel) {
    val d = s.draft
    val context = LocalContext.current
    val app = BizApp.from(context)
    var showRaw by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.save() }
    fun saveWithPermissions() {
        val needed = mutableListOf<String>()
        if (!app.calendar.hasPermission() && d.events.any { it.include }) {
            needed += Manifest.permission.READ_CALENDAR
            needed += Manifest.permission.WRITE_CALENDAR
        }
        if (Build.VERSION.SDK_INT >= 33 && d.tasks.any { it.include && it.dto.due != null }) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isEmpty()) vm.save() else permissionLauncher.launch(needed.toTypedArray())
    }

    if (s.reply != null) {
        ReplyDialog(reply = s.reply, onDismiss = vm::dismissReply)
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssistantBand(
                kicker = listOfNotNull(d.orgName.ifBlank { null }, d.person.ifBlank { null }, Labels.orgKind(d.orgKind)).joinToString(", ") + if (d.confidence < 0.7) "  (확신도 ${(d.confidence * 100).toInt()}%)" else "",
                voice = voice(readySentence(d)),
                modifier = Modifier.padding(horizontal = 0.dp),
            )

            Card(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "원문 닫기" else "원문 보기 (${Labels.source(d.source)})", style = MaterialTheme.typography.labelLarge, color = BizColors.Muted)
                if (showRaw) {
                    Spacer(Modifier.height(6.dp))
                    Text(d.rawText, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (d.type == "personal") {
                Card(bg = BizColors.WarnSoft) {
                    Text("사업과 무관한 내용으로 판단했습니다. 저장하지 않으려면 아래 \"버리기\"를 누르세요. 유형을 바꾸면 저장할 수 있습니다.", color = BizColors.Warn, style = MaterialTheme.typography.bodySmall)
                }
            }

            Card {
                FieldLabel("소속")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ENTITIES.forEach { e -> FilterChip(selected = d.entity == e, onClick = { vm.edit { it.copy(entity = e) } }, label = { Text(e) }) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = d.orgName, onValueChange = { v -> vm.edit { it.copy(orgName = v) } }, label = { Text("상대 조직") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = d.person, onValueChange = { v -> vm.edit { it.copy(person = v) } }, label = { Text("담당자") }, singleLine = true, modifier = Modifier.weight(1f))
                    Dropdown(label = "구분", options = Labels.orgKinds, selected = d.orgKind, onSelect = { v -> vm.edit { it.copy(orgKind = v) } }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = d.projectName, onValueChange = { v -> vm.edit { it.copy(projectName = v) } }, label = { Text("사업·현장명") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = d.site, onValueChange = { v -> vm.edit { it.copy(site = v) } }, label = { Text("현장(역)") }, singleLine = true, modifier = Modifier.weight(1f))
                    Dropdown(label = "시스템", options = Labels.systems, selected = d.system, onSelect = { v -> vm.edit { it.copy(system = v) } }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown(label = "유형", options = Labels.types, selected = d.type, onSelect = { v -> vm.edit { it.copy(type = v, createTicket = v == "fault") } }, modifier = Modifier.weight(1.4f))
                    Dropdown(label = "긴급도", options = Labels.urgencies, selected = d.urgency, onSelect = { v -> vm.edit { it.copy(urgency = v) } }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = d.summary, onValueChange = { v -> vm.edit { it.copy(summary = v) } }, label = { Text("요약") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }

            if (d.decisions.isNotEmpty()) {
                Card {
                    FieldLabel("결정 사항")
                    d.decisions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (d.questions.isNotEmpty()) {
                Card(bg = BizColors.WarnSoft) {
                    FieldLabel("확인이 필요한 점", BizColors.Warn)
                    d.questions.forEach { Text("? $it", style = MaterialTheme.typography.bodySmall, color = BizColors.Warn) }
                }
            }

            Card {
                FieldLabel("등록할 항목  (체크를 끄면 등록하지 않음)")
                d.events.forEachIndexed { i, e ->
                    CheckRow(
                        checked = e.include,
                        onChecked = { c -> vm.edit { it.copy(events = it.events.mapIndexed { j, x -> if (j == i) x.copy(include = c) else x }) } },
                        kicker = "삼성 캘린더 일정",
                        kickerColor = BizColors.Navy,
                        title = e.dto.title,
                        sub = listOfNotNull(TimeUtil.parseToMillis(e.dto.start)?.let { TimeUtil.formatDateTime(it) }, e.dto.place).joinToString(" · "),
                    )
                }
                if (d.type == "fault" || d.createTicket) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("장애 티켓", style = MaterialTheme.typography.labelSmall, color = BizColors.Danger, fontWeight = FontWeight.Bold)
                            Text(d.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = d.createTicket, onCheckedChange = { c -> vm.edit { it.copy(createTicket = c) } })
                    }
                }
                d.tasks.forEachIndexed { i, t ->
                    CheckRow(
                        checked = t.include,
                        onChecked = { c -> vm.edit { it.copy(tasks = it.tasks.mapIndexed { j, x -> if (j == i) x.copy(include = c) else x }) } },
                        kicker = "할 일 · ${Labels.owner(t.dto.owner)}" + (t.dto.due?.let { " · ${TimeUtil.formatDate(it)}" } ?: ""),
                        kickerColor = BizColors.Muted,
                        title = t.dto.title,
                        sub = t.dto.note ?: "",
                    )
                }
                d.money.forEachIndexed { i, m ->
                    CheckRow(
                        checked = m.include,
                        onChecked = { c -> vm.edit { it.copy(money = it.money.mapIndexed { j, x -> if (j == i) x.copy(include = c) else x }) } },
                        kicker = Labels.moneyKind(m.dto.kind) + (m.dto.due?.let { " · ${TimeUtil.formatDate(it)}" } ?: ""),
                        kickerColor = BizColors.Ok,
                        title = Labels.won(m.dto.amount),
                        sub = m.dto.note ?: "",
                    )
                }
                if (d.events.isEmpty() && d.tasks.isEmpty() && d.money.isEmpty() && !d.createTicket) {
                    Text("등록할 일정이나 할 일이 없습니다. 기록만 저장됩니다.", color = BizColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (s.message != null) Text(s.message, color = BizColors.Danger, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saveWithPermissions() }, enabled = !s.saving, modifier = Modifier.weight(2f).height(50.dp)) {
                    Text(if (s.saving) "저장 중" else "등록")
                }
                OutlinedButton(onClick = vm::requestReply, enabled = !s.replyLoading, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text(if (s.replyLoading) "작성 중" else "답장 초안")
                }
            }
            TextButton(onClick = vm::discard, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("사업 무관으로 버리기", color = BizColors.Muted)
            }
        }
    }
}

private fun readySentence(d: ConfirmDraft): String {
    if (d.type == "personal") return "사업과 관계없는 대화로 보입니다. 저장하지 않으려면 아래에서 버리면 됩니다."
    val parts = mutableListOf<String>()
    if (d.events.isNotEmpty()) parts += "일정 {${d.events.size}건}"
    if (d.tasks.isNotEmpty()) parts += "할 일 {${d.tasks.size}건}"
    if (d.money.isNotEmpty()) parts += "금전 {${d.money.size}건}"
    val found = if (parts.isEmpty()) "따로 등록할 일정이나 할 일은 찾지 못했습니다." else parts.joinToString(", ") + "을 찾았습니다."
    val typeLine = "{${Labels.type(d.type)}}" + (if (d.urgency == "high") ", 긴급으로 보입니다." else "으로 정리했습니다.")
    val q = if (d.questions.isNotEmpty()) " 확인할 점이 {${d.questions.size}가지} 있습니다." else ""
    return "$typeLine $found$q"
}

@Composable
private fun ReplyDialog(reply: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("확인 문자 초안") },
        text = { Text(reply) },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", reply)
                runCatching { context.startActivity(intent) }
            }) { Text("문자로 보내기") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(reply)) }) { Text("복사") }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
}

@Composable
private fun Card(bg: androidx.compose.ui.graphics.Color = BizColors.Panel, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = Modifier
        .fillMaxWidth()
        .background(bg, RoundedCornerShape(14.dp))
    val m = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(m.padding(14.dp)) { content() }
}

@Composable
private fun FieldLabel(text: String, color: androidx.compose.ui.graphics.Color = BizColors.Muted) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun CheckRow(checked: Boolean, onChecked: (Boolean) -> Unit, kicker: String, kickerColor: androidx.compose.ui.graphics.Color, title: String, sub: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(kicker, style = MaterialTheme.typography.labelSmall, color = kickerColor, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(code); expanded = false })
            }
        }
    }
}

@Composable
fun SourceChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Labels.sources.filter { it.first != "visit" }.forEach { (code, text) ->
            FilterChip(selected = selected == code, onClick = { onSelect(code) }, label = { Text(text) })
        }
    }
}

@Suppress("unused")
private fun ConfirmDraft.hasAnything() = events.isNotEmpty() || tasks.isNotEmpty() || money.isNotEmpty()

@Suppress("unused")
private val spacerWidth = Modifier.width(0.dp)
