package com.obpiub.myapplication.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obpiub.myapplication.domain.model.Note
import com.obpiub.myapplication.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val searchText: String = "",
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.NewestFirst,
    val deleteMode: Boolean = false,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val error: String? = null,
)

class NotesViewModel(
    private val repository: NoteRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(NotesUiState(
        searchText = savedState["searchText"] ?: "",
        query = savedState["query"] ?: "",
        sortOrder = SortOrder.valueOf(savedState["sortOrder"] ?: SortOrder.NewestFirst.name),
        deleteMode = savedState["deleteMode"] ?: false,
    ))
    val state = _state.asStateFlow()
    private var allNotes = emptyList<Note>()
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                repository.observeNotes().collect { notes ->
                    allNotes = notes
                    refresh()
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Не удалось загрузить заметки") }
            }
        }
    }

    fun changeSearch(text: String) {
        savedState["searchText"] = text
        _state.update { it.copy(searchText = text) }
    }

    fun search() {
        savedState["query"] = state.value.searchText.trim()
        _state.update { it.copy(query = it.searchText.trim()) }
        refresh()
    }

    fun sort(order: SortOrder) {
        savedState["sortOrder"] = order.name
        _state.update { it.copy(sortOrder = order) }
        refresh()
    }

    fun toggleDeleteMode() {
        if (state.value.isDeleting) return
        val enabled = !state.value.deleteMode
        savedState["deleteMode"] = enabled
        _state.update { it.copy(deleteMode = enabled) }
    }

    fun delete(id: Long) {
        if (!state.value.deleteMode || state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            try {
                repository.deleteNote(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = "Не удалось удалить заметку. Попробуйте ещё раз") }
            } finally {
                _state.update { it.copy(isDeleting = false) }
            }
        }
    }

    private fun refresh() {
        _state.update { it.copy(notes = selectNotes(allNotes, it.query, it.sortOrder)) }
    }
}