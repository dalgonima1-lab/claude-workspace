package com.jpntech.bizassistant.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpntech.bizassistant.data.ConfirmDraft
import com.jpntech.bizassistant.data.Repository
import com.jpntech.bizassistant.data.SaveResult
import com.jpntech.bizassistant.data.remote.Analysis
import com.jpntech.bizassistant.data.remote.RelayException
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConfirmState {
    data object Idle : ConfirmState
    data class Analyzing(val text: String) : ConfirmState
    data class Ready(val draft: ConfirmDraft, val saving: Boolean = false, val reply: String? = null, val replyLoading: Boolean = false, val message: String? = null) : ConfirmState
    data class Saved(val result: SaveResult) : ConfirmState
    data class Failed(val text: String, val source: String, val callerHint: String?, val message: String) : ConfirmState
    data object Discarded : ConfirmState
}

class ConfirmViewModel(private val repo: Repository) : ViewModel() {
    private val _state = MutableStateFlow<ConfirmState>(ConfirmState.Idle)
    val state: StateFlow<ConfirmState> = _state
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    /** 대기함에서 연 건이면 그 id. 저장하거나 버리면 대기함에서 지운다. */
    private var pendingId: Long? = null

    /** 자동 처리로 이미 분석된 건을 대기함에서 연다. 다시 분석하지 않는다. */
    fun openPending(id: Long) {
        viewModelScope.launch {
            val p = repo.db.pending().byId(id)
            if (p == null) { _state.value = ConfirmState.Failed("", "call", null, "대기함에서 해당 건을 찾을 수 없습니다. 이미 처리되었을 수 있습니다."); return@launch }
            val analysis = runCatching { json.decodeFromString(Analysis.serializer(), p.analysisJson) }.getOrNull()
            if (analysis == null) { start(p.rawText, p.source, p.callerHint); return@launch }
            pendingId = id
            val settings = repo.settings.current()
            _state.value = ConfirmState.Ready(ConfirmDraft.from(analysis, p.rawText, p.source, settings.defaultEntity))
        }
    }

    fun start(text: String, source: String, callerHint: String? = null) {
        pendingId = null
        _state.value = ConfirmState.Analyzing(text)
        viewModelScope.launch {
            try {
                val settings = repo.settings.current()
                val res = repo.analyze(text, source, callerHint)
                _state.value = ConfirmState.Ready(ConfirmDraft.from(res.analysis, text, source, settings.defaultEntity))
            } catch (e: RelayException) {
                _state.value = ConfirmState.Failed(text, source, callerHint, e.message ?: "분석 실패")
            } catch (e: Exception) {
                _state.value = ConfirmState.Failed(text, source, callerHint, e.message ?: "알 수 없는 오류")
            }
        }
    }

    fun retry() {
        val s = _state.value as? ConfirmState.Failed ?: return
        start(s.text, s.source, s.callerHint)
    }

    fun edit(block: (ConfirmDraft) -> ConfirmDraft) {
        _state.update { s -> if (s is ConfirmState.Ready) s.copy(draft = block(s.draft), message = null) else s }
    }

    fun save() {
        val s = _state.value as? ConfirmState.Ready ?: return
        if (s.saving) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            try {
                val result = repo.save(s.draft)
                pendingId?.let { repo.db.pending().delete(it) }
                pendingId = null
                _state.value = ConfirmState.Saved(result)
            } catch (e: Exception) {
                _state.value = s.copy(saving = false, message = "저장 실패: ${e.message}")
            }
        }
    }

    fun requestReply() {
        val s = _state.value as? ConfirmState.Ready ?: return
        if (s.replyLoading) return
        if (s.draft.replyDraft != null && s.reply == null) {
            _state.value = s.copy(reply = s.draft.replyDraft)
            return
        }
        _state.value = s.copy(replyLoading = true)
        viewModelScope.launch {
            try {
                val reply = repo.api.draftReply(s.draft.toAnalysis())
                _state.update { cur -> if (cur is ConfirmState.Ready) cur.copy(reply = reply, replyLoading = false) else cur }
            } catch (e: Exception) {
                _state.update { cur -> if (cur is ConfirmState.Ready) cur.copy(replyLoading = false, message = "답장 초안 실패: ${e.message}") else cur }
            }
        }
    }

    fun dismissReply() = _state.update { s -> if (s is ConfirmState.Ready) s.copy(reply = null) else s }

    fun discard() {
        val id = pendingId
        pendingId = null
        if (id != null) viewModelScope.launch { repo.db.pending().delete(id) }
        _state.value = ConfirmState.Discarded
    }

    fun reset() { pendingId = null; _state.value = ConfirmState.Idle }
}
