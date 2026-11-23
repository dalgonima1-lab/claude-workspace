package com.jpntech.bizassistant.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpntech.bizassistant.ui.common.voice
import com.jpntech.bizassistant.ui.theme.BizColors
import kotlinx.coroutines.launch

/** 오늘 화면 위쪽에 나오는 업데이트 제안. 받기, 설치까지 한 자리에서 한다. */
@Composable
fun UpdateBanner() {
    val state by UpdateChecker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state
    if (s is UpdateChecker.State.Idle || s is UpdateChecker.State.UpToDate || s is UpdateChecker.State.Checking) return

    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
            .background(BizColors.Panel, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (s) {
            is UpdateChecker.State.Available -> {
                Text(voice("새 버전 {${s.versionName}}이 있습니다. 지금 받을까요? 설치 중에도 기록은 그대로 남습니다."), style = MaterialTheme.typography.bodyMedium, color = BizColors.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { UpdateChecker.download(context) } }, modifier = Modifier.weight(2f)) { Text("업데이트 받기") }
                    TextButton(onClick = { UpdateChecker.dismiss() }, modifier = Modifier.weight(1f)) { Text("나중에", color = BizColors.Muted) }
                }
            }
            is UpdateChecker.State.Downloading -> {
                Text(voice("{${s.versionName}} 받는 중, ${s.percent}%"), style = MaterialTheme.typography.bodyMedium, color = BizColors.Text)
                LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = BizColors.Signal, trackColor = BizColors.Line)
            }
            is UpdateChecker.State.ReadyToInstall -> {
                Text(
                    voice("{${s.versionName}} 받기가 끝났습니다. 알림 창의 {다운로드 완료} 알림을 누르거나, 아래에서 다운로드 목록을 열어 파일을 누르면 설치됩니다."),
                    style = MaterialTheme.typography.bodyMedium, color = BizColors.Text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { UpdateChecker.openDownloads(context) }, modifier = Modifier.weight(2f)) { Text("다운로드 목록 열기") }
                    TextButton(onClick = { UpdateChecker.dismiss() }, modifier = Modifier.weight(1f)) { Text("닫기", color = BizColors.Muted) }
                }
            }
            is UpdateChecker.State.Error -> {
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = BizColors.Amber)
                TextButton(onClick = { UpdateChecker.dismiss() }) { Text("닫기", color = BizColors.Muted) }
            }
            else -> {}
        }
    }
}
