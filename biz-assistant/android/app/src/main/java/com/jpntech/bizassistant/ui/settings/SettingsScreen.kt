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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.data.settings.AppSettings
import com.jpntech.bizassistant.data.settings.ENTITIES
import com.jpntech.bizassistant.ui.common.SectionTitle
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
    var status by remember { mutableStateOf<String?>(null) }
    var calendars by remember { mutableStateOf(app.calendar.listCalendars()) }

    LaunchedEffect(Unit) {
        val s = app.settings.current()
        loaded = s
        serverUrl = s.serverUrl; token = s.appToken; entity = s.defaultEntity; calendarId = s.calendarId; reminders = s.remindersEnabled
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        calendars = app.calendar.listCalendars()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("설정", style = MaterialTheme.typography.titleLarge)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("중계 서버")
            OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("서버 주소") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = token, onValueChange = { token = it }, label = { Text("서버 토큰 (APP_SHARED_TOKEN)") }, singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "숨김" else "보기") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    status = "확인 중..."
                    scope.launch {
                        status = try {
                            app.settings.update { it.copy(serverUrl = serverUrl.trim(), appToken = token.trim()) }
                            val h = app.repository.api.health()
                            if (h.ok) "연결 정상 · 모델 ${h.model}" else "서버 설정 문제: ${h.problem}"
                        } catch (e: Exception) { "연결 실패: ${e.message}" }
                    }
                }) { Text("연결 테스트") }
                if (status != null) Text(status!!, style = MaterialTheme.typography.bodySmall, color = if (status!!.startsWith("연결 정상")) BizColors.Ok else BizColors.Danger)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("기본 소속")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ENTITIES.forEach { e -> FilterChip(selected = entity == e, onClick = { entity = e }, label = { Text(e) }) }
            }
            Text("분석에서 소속을 판단하지 못할 때 쓰는 기본값입니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("일정을 넣을 캘린더")
            if (!app.calendar.hasPermission()) {
                OutlinedButton(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) { Text("캘린더 권한 허용") }
                Text("허용하면 삼성 캘린더 목록이 나타납니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
            } else if (calendars.isEmpty()) {
                Text("쓰기 가능한 캘린더가 없습니다. 삼성 캘린더에서 계정을 확인하세요.", style = MaterialTheme.typography.bodySmall, color = BizColors.Danger)
            } else {
                calendars.forEach { c ->
                    FilterChip(selected = calendarId == c.id, onClick = { calendarId = c.id }, label = { Text("${c.name}  (${c.account})") })
                }
                Text("고르지 않으면 첫 번째 캘린더에 넣습니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("할 일 알림")
                Text("기한 전날 아침 9시에 알림을 보냅니다.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
            }
            Switch(checked = reminders, onCheckedChange = { reminders = it })
        }

        Button(
            onClick = {
                scope.launch {
                    app.settings.update { it.copy(serverUrl = serverUrl.trim(), appToken = token.trim(), defaultEntity = entity, calendarId = calendarId, remindersEnabled = reminders) }
                    status = "저장했습니다"
                }
            },
            enabled = loaded != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("저장") }

        Text("통화 내용은 이 서버를 거쳐 Claude API로 전송되어 분석됩니다. 민감한 통화는 공유하지 마세요.", style = MaterialTheme.typography.bodySmall, color = BizColors.Muted)
        Spacer(Modifier.height(24.dp))
    }
}
