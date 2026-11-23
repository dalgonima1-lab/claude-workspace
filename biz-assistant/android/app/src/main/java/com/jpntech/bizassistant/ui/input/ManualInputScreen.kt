package com.jpntech.bizassistant.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jpntech.bizassistant.ui.common.AssistantBand
import com.jpntech.bizassistant.ui.common.voice
import com.jpntech.bizassistant.ui.confirm.SourceChips
import com.jpntech.bizassistant.ui.theme.BizColors

/** 공유 없이 직접 붙여넣거나 적어서 분석하는 화면. 음성 입력은 키보드의 마이크 버튼을 쓴다. */
@Composable
fun ManualInputScreen(onAnalyze: (String, String) -> Unit, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var source by remember { mutableStateOf("memo") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AssistantBand(kicker = null, voice = voice("무엇을 정리할까요? 통화 요약이나 문자를 {붙여넣거나}, 키보드 마이크로 {말씀}하셔도 됩니다."), modifier = Modifier.padding(horizontal = 0.dp))
        SourceChips(selected = source, onSelect = { source = it })
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text("예: 군자역 김 과장, 내일 오전 10시 B2 조명 제어기 점검 방문") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAnalyze(text.trim(), source) }, enabled = text.isNotBlank(), modifier = Modifier.weight(2f).height(50.dp)) { Text("분석") }
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(50.dp)) { Text("취소") }
        }
        Spacer(Modifier.height(4.dp))
    }
}
