package com.jpntech.bizassistant.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpntech.bizassistant.data.ConfirmDraft
import com.jpntech.bizassistant.data.Repository
import com.jpntech.bizassistant.data.SaveResult
import com.jpntech.bizassistant.data.remote.RelayException
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

    fun start(text: String, source: String, callerHint: String? = null) {
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

    fun discard() { _state.value = ConfirmState.Discarded }

    fun reset() { _state.value = ConfirmState.Idle }
}
