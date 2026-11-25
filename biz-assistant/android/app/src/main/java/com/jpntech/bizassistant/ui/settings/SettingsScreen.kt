package com.jpntech.bizassistant.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.data.settings.AppSettings
import com.jpntech.bizassistant.data.settings.ENTITIES
import com.jpntech.bizassistant.recordings.RecordingWatcher
import com.jpntech.bizassistant.update.UpdateChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpntech.bizassistant.ui.common.AssistantBand
import com.jpntech.bizassistant.ui.common.SectionTitle
import com.jpntech.bizassistant.ui.common.voice
import com.jpntech.bizassistant.ui.theme.BizColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val app = BizApp.from(LocalContext.current)
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<AppSettings?>(null) }
    var serverUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var entity by remember { mutableStateOf(ENTITIES.first()) }
    var calendarId by remember { mutableStateOf(-1L) }
    var reminders by remember { mutableStateOf(true) }
    var autoProcess by remember { mutableStateOf(false) }
    var autoRegister by remember { mutableStateOf(false) }
    val recentRecordings by remember { app.repository.db.recordings().recent(3) }.collectAsStateWithLifecycle(emptyList())
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var calendars by remember { mutableStateOf(app.calendar.listCalendars()) }

    LaunchedEffect(Unit) {
        val s = app.settings.current()
        loaded = s
        serverUrl = s.serverUrl; token = s.appToken; entity = s.defaultEntity; calendarId = s.calendarId; reminders = s.remindersEnabled
        autoProcess = s.autoProcess; autoRegister = s.autoRegister
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        calendars = app.calendar.listCalendars()
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val ok = granted[RecordingWatcher.audioPermission()] == true
        autoProcess = ok
        scope.launch {
            app.settings.update { it.copy(autoProcess = ok, autoSince = if (ok && it.autoSince == 0L) System.currentTimeMillis() else it.autoSince) }
            if (ok) RecordingWatcher.ensureScheduled(app)
        }
    }

    val sentence = when {
        loaded == null -> "설정을 불러오고 있습니다."
        token.isBlank() -> "아직 {서버 토큰}이 없습니다. 토큰을 넣고 연결 테스트를 눌러 주세요. 그 전에는 분석을 할 수 없습니다."
        !app.calendar.hasPermission() -> "서버 토큰은 있습니다. 이제 {캘린더 권한}을 허용하면 일정을 삼성 캘린더에 넣을 수 있습니다."
        calendars.isEmpty() -> "캘린더 권한은 있는데 쓸 수 있는 캘린더가 보이지 않습니다. 삼성 캘린더에서 계정을 확인해 주세요."
        status != null && statusOk -> "{연결 정상}입니다. 기본 소속은 {$entity}, 일정은 ${calendars.firstOrNull { it.id == calendarId }?.name?.let { "{$it}" } ?: "첫 번째 캘린더"}에 넣습니다."
        !autoProcess -> "준비가 되어 있습니다. {통화 녹음 자동 정리}를 켜면 통화가 끝날 때마다 제가 먼저 정리해 두겠습니다."
        else -> "자동 정리가 켜져 있습니다. 통화가 끝나면 ${if (autoRegister) "{바로 등록}하고" else "{대기함}에 넣고"} 알림으로 알려 드립니다."
    }
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = BizColors.SignalSoft, selectedLabelColor = BizColors.Signal, containerColor = Color.Transparent, labelColor = BizColors.Muted)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AssistantBand(kicker = "설정", voice = voice(sentence))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("중계 서버")
                OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("서버 주소") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = token, onValueChange = { token = it }, label = { Text("서버 토큰") }, singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "숨기기" else "보기") } },
                    supportingText = { Text("Vercel 환경 변수 APP_SHARED_TOKEN과 같은 값", color = BizColors.Muted) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        status = "확인 중"; statusOk = false
                        scope.launch {
                            try {
                                app.settings.update { it.copy(serverUrl = serverUrl.trim(), appToken = token.trim()) }
                                val h = app.repository.api.health()
                                statusOk = h.ok
                                status = if (h.ok) "연결 정상, 모델 ${h.model}" else "서버 설정 문제: ${h.problem}"
                            } catch (e: Exception) { statusOk = false; status = "연결 실패: ${e.message}" }
                        }
                    }) { Text("연결 테스트") }
                    if (status != null) Text(status!!, style = MaterialTheme.typography.bodySmall, color = if (statusOk) BizColors.Green else BizColors.Amber)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("기본 소속")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ENTITIES.forEach { e -> FilterChip(selected = entity == e, onClick = { entity = e }, label = { Text(e) }, colors = chipColors) }
                }
                Text("분석에서 소속을 판단하지 못할 때 쓰는 값입니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("일정을 넣을 캘린더")
                if (!app.calendar.hasPermission()) {
                    OutlinedButton(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) { Text("캘린더 권한 허용") }
                } else if (calendars.isEmpty()) {
                    Text("쓸 수 있는 캘린더가 없습니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Amber)
                } else {
                    calendars.forEach { c ->
                        FilterChip(selected = calendarId == c.id, onClick = { calendarId = c.id }, label = { Text("${c.name}  ${c.account}") }, colors = chipColors)
                    }
                    Text("고르지 않으면 첫 번째 캘린더에 넣습니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("할 일 알림")
                    Text("기한 전날 아침 9시에 알려 드립니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                }
                Switch(checked = reminders, onCheckedChange = { reminders = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("통화 녹음 자동 정리")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("통화가 끝나면 녹음을 글자로 바꿔 정리", style = MaterialTheme.typography.bodyMedium, color = BizColors.Text)
                        Text("통화가 끊기면 바로 시작해 보통 1분 안에 알림이 옵니다. 전화 앱의 통화 자동 녹음이 켜져 있어야 합니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                        if (autoProcess && !RecordingWatcher.hasPhonePermission(app)) {
                            Text("전화 상태 권한이 없어 통화 종료를 바로 알 수 없습니다. 스위치를 껐다 켜서 허용해 주세요.", style = MaterialTheme.typography.bodySmall, color = BizColors.Amber)
                        }
                    }
                    Switch(checked = autoProcess, onCheckedChange = { on ->
                        if (on) {
                            val perms = mutableListOf(RecordingWatcher.audioPermission(), Manifest.permission.READ_PHONE_STATE)
                            if (android.os.Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                            audioPermissionLauncher.launch(perms.toTypedArray())
                        } else {
                            autoProcess = false
                            scope.launch { app.settings.update { it.copy(autoProcess = false) }; RecordingWatcher.cancel(app) }
                        }
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("확인 없이 바로 등록", style = MaterialTheme.typography.bodyMedium, color = BizColors.Text)
                        Text("끄면 대기함에 넣고 알림만 보냅니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
                    }
                    Switch(checked = autoRegister, enabled = autoProcess, onCheckedChange = { v -> autoRegister = v; scope.launch { app.settings.update { it.copy(autoRegister = v) } } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { RecordingWatcher.runNow(app); status = "녹음 폴더를 확인하고 있습니다"; statusOk = true }, enabled = autoProcess) { Text("지금 녹음 확인") }
                    OutlinedButton(onClick = {
                        val i = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + app.packageName))
                        runCatching { app.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("배터리 최적화 제외") }
                }
                if (recentRecordings.isNotEmpty()) {
                    recentRecordings.forEach { r ->
                        val label = when (r.status) { "done" -> "처리 완료"; "processing" -> "처리 중" + (r.processedAt?.let { " (${com.jpntech.bizassistant.data.TimeUtil.formatTime(it)} 시작)" } ?: ""); "empty" -> "말소리 없음"; "skipped" -> "사업 무관"; "retry" -> (r.error ?: "잠시 뒤 다시 시도"); "error" -> "실패: ${r.error ?: ""}"; else -> r.status }
                        Text("${r.displayName}  $label", style = MaterialTheme.typography.bodySmall, color = if (r.status == "error") BizColors.Amber else BizColors.Muted)
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        app.settings.update { it.copy(serverUrl = serverUrl.trim(), appToken = token.trim(), defaultEntity = entity, calendarId = calendarId, remindersEnabled = reminders) }
                        status = "저장했습니다"; statusOk = true
                    }
                },
                enabled = loaded != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("저장") }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("컴퓨터와 동기화")
                val syncState by app.sync.status.collectAsStateWithLifecycle()
                val lastSyncAt = syncState.lastSyncAt
                Text(
                    when {
                        syncState.running -> "동기화 중"
                        syncState.lastError != null -> "마지막 동기화 실패: ${syncState.lastError}"
                        lastSyncAt != null -> "마지막 동기화 ${com.jpntech.bizassistant.data.TimeUtil.formatDateTime(lastSyncAt)}  (올림 ${syncState.pushed}, 받음 ${syncState.pulled})"
                        else -> "아직 동기화하지 않았습니다"
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (syncState.lastError != null) BizColors.Amber else BizColors.Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { app.sync.syncNow() } }, enabled = !syncState.running) { Text("지금 동기화") }
                }
                Text("컴퓨터에서는 브라우저로 ${serverUrl.ifBlank { "서버 주소" }} 에 들어가 같은 토큰을 넣으면 됩니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("앱 버전")
                val updateState by UpdateChecker.state.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("현재 ${UpdateChecker.installedVersionName(app)}", style = MaterialTheme.typography.bodyMedium, color = BizColors.Text, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { scope.launch { UpdateChecker.check(app) } }) { Text("업데이트 확인") }
                }
                val msg = when (val u = updateState) {
                    UpdateChecker.State.Checking -> "확인 중"
                    UpdateChecker.State.UpToDate -> "최신 버전입니다"
                    is UpdateChecker.State.Available -> "새 버전 ${u.versionName}이 있습니다. 오늘 화면에서 받을 수 있습니다."
                    is UpdateChecker.State.Error -> u.message
                    else -> null
                }
                if (msg != null) Text(msg, style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
            }

            Text("통화 내용은 중계 서버를 거쳐 Claude로 전송되어 분석됩니다. 민감한 통화는 공유하지 마세요.", style = MaterialTheme.typography.bodySmall, color = BizColors.Dim)
            Spacer(Modifier.height(24.dp))
        }
    }
}
