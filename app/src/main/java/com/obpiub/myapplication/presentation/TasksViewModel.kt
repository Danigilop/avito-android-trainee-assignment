package com.obpiub.myapplication.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obpiub.myapplication.domain.model.Task
import com.obpiub.myapplication.domain.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val searchText: String = "",
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.NewestFirst,
    val filter: TaskFilter = TaskFilter.All,
    val isCreating: Boolean = false,
    val draft: String = "",
    val draftError: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

class TasksViewModel(
    private val repository: TaskRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(TasksUiState(
        searchText = savedState["searchText"] ?: "",
        query = savedState["query"] ?: "",
        sortOrder = SortOrder.valueOf(savedState["sortOrder"] ?: SortOrder.NewestFirst.name),
        filter = TaskFilter.valueOf(savedState["filter"] ?: TaskFilter.All.name),
        isCreating = savedState["isCreating"] ?: false,
        draft = savedState["draft"] ?: "",
    ))
    val state = _state.asStateFlow()
    private var allTasks = emptyList<Task>()
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                repository.observeTasks().collect { tasks ->
                    allTasks = tasks
                    refresh()
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "Не удалось загрузить задачи") }
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

    fun filter(filter: TaskFilter) {
        savedState["filter"] = filter.name
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    fun startCreating() {
        if (state.value.isSaving) return
        savedState["isCreating"] = true
        _state.update { it.copy(isCreating = true, draftError = null) }
    }

    fun changeDraft(text: String) {
        savedState["draft"] = text
        _state.update { it.copy(draft = text, draftError = null) }
    }

    fun cancelCreating() {
        if (state.value.isSaving) return
        clearDraft()
    }

    fun saveTask() {
        val current = state.value
        if (!current.isCreating || current.isSaving) return
        if (current.draft.isBlank()) {
            _state.update { it.copy(draftError = "Введите название задачи") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.saveTask(Task(title = current.draft.trim()))
                clearDraft()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = "Не удалось сохранить задачу. Введённый текст сохранён") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun setCompleted(id: Long, completed: Boolean) {
        if (state.value.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.setCompleted(id, completed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = "Не удалось изменить статус задачи") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun clearDraft() {
        savedState["isCreating"] = false
        savedState["draft"] = ""
        _state.update { it.copy(isCreating = false, draft = "", draftError = null) }
    }

    private fun refresh() {
        _state.update { it.copy(tasks = selectTasks(allTasks, it.query, it.sortOrder, it.filter)) }
    }
}