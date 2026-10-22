package com.obpiub.myapplication.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obpiub.myapplication.domain.model.Note
import com.obpiub.myapplication.domain.repository.NoteRepository
import com.obpiub.myapplication.domain.repository.NoteImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NoteEditorUiState(
    val imagePath: String? = null,
    val isImportingImage: Boolean = false,
    val imageError: String? = null,
    val title: String = "",
    val text: String = "",
    val titleError: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canEdit: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class NoteEditorViewModel(
    private val repository: NoteRepository,
    private val savedState: SavedStateHandle,
    private val imageStore: NoteImageStore? = null,
) : ViewModel() {
    private val noteId: Long = savedState["noteId"] ?: 0L
    private var original: Note? = null
    private val _state = MutableStateFlow(NoteEditorUiState(
        title = savedState["title"] ?: "",
        text = savedState["text"] ?: "",
    ))
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        if (state.value.isSaving) return
        _state.update { it.copy(isLoading = true, error = null, canEdit = false) }
        viewModelScope.launch {
            try {
                original = if (noteId == 0L) Note(title = "") else repository.getNote(noteId)
                val note = original
                if (note == null) {
                    _state.update { it.copy(isLoading = false, error = "Заметка не найдена") }
                } else {
                    val title = savedState.get<String>("title") ?: note.title
                    val text = savedState.get<String>("text") ?: note.text
                    val imagePath = if (savedState.contains("imagePath")) savedState.get<String>("imagePath") else note.imagePath
                    _state.update { it.copy(title = title, text = text, imagePath = imagePath, isLoading = false, canEdit = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Не удалось загрузить заметку") }
            }
        }
    }

    fun changeTitle(title: String) {
        if (!state.value.canEdit || state.value.isSaving) return
        savedState["title"] = title
        _state.update { it.copy(title = title, titleError = null) }
    }

    fun changeText(text: String) {
        if (!state.value.canEdit || state.value.isSaving) return
        savedState["text"] = text
        _state.update { it.copy(text = text) }
    }

    fun imageError(message: String) {
        _state.update { it.copy(imageError = message) }
    }

    fun removeImage() {
        if (!state.value.canEdit || state.value.isSaving || state.value.isImportingImage) return
        val previous = state.value.imagePath
        savedState["imagePath"] = null
        _state.update { it.copy(imagePath = null, imageError = null) }
        viewModelScope.launch { deleteDraftImage(previous) }
    }

    private suspend fun deleteDraftImage(path: String?) {
        if (path == null || path == original?.imagePath) return
        withContext(NonCancellable) { runCatching { imageStore?.deleteImage(path) } }
    }

    fun discard(onDiscarded: () -> Unit) {
        if (state.value.isSaving || state.value.isImportingImage) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            deleteDraftImage(state.value.imagePath)
            onDiscarded()
        }
    }

    fun importImage(uri: String, onFinished: (() -> Unit)? = null) {
        val store = imageStore ?: return
        if (!state.value.canEdit || state.value.isSaving || state.value.isImportingImage) return
        _state.update { it.copy(isImportingImage = true, imageError = null) }
        viewModelScope.launch {
            try {
                val path = store.importImage(uri)
                val previous = state.value.imagePath
                savedState["imagePath"] = path
                _state.update { it.copy(imagePath = path) }
                deleteDraftImage(previous)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                imageError("Не удалось открыть изображение. Выберите доступный файл до 30 МБ")
            } finally {
                if (onFinished != null) withContext(NonCancellable + Dispatchers.IO) { runCatching(onFinished) }
                _state.update { it.copy(isImportingImage = false) }
            }
        }
    }

    fun save() {
        val current = state.value
        val note = original ?: return
        if (!current.canEdit || current.isSaving || current.saved || current.isImportingImage) return
        if (current.title.isBlank()) {
            _state.update { it.copy(titleError = "Введите заголовок заметки") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.saveNote(note.copy(
                    title = current.title.trim(),
                    text = current.text,
                    imagePath = current.imagePath,
                    createdAt = if (noteId == 0L) System.currentTimeMillis() else note.createdAt,
                ))
                _state.update { it.copy(saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = "Не удалось сохранить заметку. Попробуйте ещё раз") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}
