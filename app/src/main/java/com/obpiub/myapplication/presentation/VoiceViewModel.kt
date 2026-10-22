package com.obpiub.myapplication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obpiub.myapplication.domain.repository.VoiceException
import com.obpiub.myapplication.domain.repository.SpeechInput
import com.obpiub.myapplication.domain.repository.VoiceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoicePhase { Idle, Recording, Recognizing, Formatting, Saving }
data class VoiceUiState(val phase: VoicePhase = VoicePhase.Idle, val error: String? = null) {
    val busy: Boolean get() = phase != VoicePhase.Idle
}

class VoiceViewModel(
    private val recorder: SpeechInput,
    private val repository: VoiceRepository,
    private val isTask: Boolean,
    private val onResult: suspend (String) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(VoiceUiState())
    val state = _state.asStateFlow()
    private var operation: Job? = null

    fun start() {
        if (operation?.isCompleted == false) return
        _state.value = VoiceUiState(VoicePhase.Recording)
        operation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val text = recorder.recognize {
                    _state.value = VoiceUiState(VoicePhase.Recognizing)
                }.trim()
                if (text.isEmpty()) throw VoiceException("Речь не распознана. Повторите ввод")
                val result = if (isTask) {
                    _state.value = VoiceUiState(VoicePhase.Formatting)
                    repository.createTaskTitle(text).trim().also {
                        if (it.isEmpty()) throw VoiceException("GigaChat вернул пустое название задачи")
                    }
                } else text
                _state.value = VoiceUiState(VoicePhase.Saving)
                onResult(result)
                _state.value = VoiceUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VoiceUiState(error = when (e) {
                    is VoiceException -> e.message
                    is SecurityException -> "Нет доступа к микрофону. Разрешите его в настройках приложения"
                    else -> "Не удалось выполнить голосовой ввод. Попробуйте ещё раз"
                })
            }
        }
    }

    fun finish() {
        if (state.value.phase != VoicePhase.Recording) return
        try { recorder.stop() } catch (_: Exception) {
            cancelRecording()
            _state.value = VoiceUiState(error = "Не удалось завершить запись. Повторите ввод")
        }
    }

    fun cancelRecording() {
        if (state.value.phase != VoicePhase.Recording && state.value.phase != VoicePhase.Recognizing) return
        operation?.cancel()
        _state.value = VoiceUiState(error = "Запись отменена")
    }

    fun permissionDenied() {
        if (!state.value.busy) _state.value = VoiceUiState(error = "Доступ к микрофону не предоставлен. Разрешите его в настройках приложения и повторите ввод")
    }
}
