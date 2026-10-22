package com.obpiub.myapplication.presentation

import com.obpiub.myapplication.domain.model.Note
import com.obpiub.myapplication.domain.model.Task

enum class SortOrder(val label: String) {
    NewestFirst("От новых к старым"),
    OldestFirst("От старых к новым"),
}

enum class TaskFilter(val label: String) {
    All("Все"),
    Active("Активные"),
    Completed("Выполненные"),
}

fun selectNotes(notes: List<Note>, query: String, order: SortOrder): List<Note> {
    val matching = notes.filter { it.title.contains(query.trim(), ignoreCase = true) }
    val comparator = compareBy<Note> { it.createdAt }.thenBy { it.id }
    return matching.sortedWith(if (order == SortOrder.NewestFirst) comparator.reversed() else comparator)
}

fun selectTasks(tasks: List<Task>, query: String, order: SortOrder, filter: TaskFilter): List<Task> {
    val matching = tasks.filter {
        it.title.contains(query.trim(), ignoreCase = true) && when (filter) {
            TaskFilter.All -> true
            TaskFilter.Active -> !it.isCompleted
            TaskFilter.Completed -> it.isCompleted
        }
    }
    val byDate = compareBy<Task> { it.createdAt }.thenBy { it.id }
    val dateOrder = if (order == SortOrder.NewestFirst) byDate.reversed() else byDate
    return matching.sortedWith(compareBy<Task> { it.isCompleted }.then(dateOrder))
}