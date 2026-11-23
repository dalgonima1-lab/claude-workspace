package com.jpntech.bizassistant.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.ui.confirm.ConfirmScreen
import com.jpntech.bizassistant.ui.confirm.ConfirmViewModel
import com.jpntech.bizassistant.ui.theme.BizTheme

/** 공유 시트에서 "사업 비서에 정리"를 고르면 열리는 화면. 받은 텍스트를 바로 분석한다. */
class ShareActivity : ComponentActivity() {
    private val vm: ConfirmViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConfirmViewModel(BizApp.from(this@ShareActivity).repository) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = extractText(intent)
        if (savedInstanceState == null) {
            if (text.isBlank()) {
                setContent { BizTheme { Scaffold { p -> Column(Modifier.padding(p).padding(24.dp)) { Text("공유된 글이 비어 있습니다.") } } } }
                return
            }
            vm.start(text, guessSource(text))
        }
        setContent { BizTheme { ShareContent(vm) { finish() } } }
    }

    private fun extractText(intent: Intent?): String {
        if (intent?.action != Intent.ACTION_SEND) return ""
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        return if (subject.isNotBlank() && !body.contains(subject)) "$subject\n$body" else body
    }

    /** 갤럭시 AI 통화 요약은 "통화" 관련 문구가 들어가는 경우가 많다. 아니면 문자로 본다. 확인 카드에서 바꿀 수 있다. */
    private fun guessSource(text: String): String = when {
        text.contains("통화") || text.contains("녹음") || text.lines().size >= 6 -> "call"
        text.contains("[Web발신]") || text.contains("Web발신") -> "sms"
        else -> "sms"
    }
}

@Composable
private fun ShareContent(vm: ConfirmViewModel, onFinished: () -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ConfirmScreen(vm = vm, onFinished = onFinished)
        }
    }
}
