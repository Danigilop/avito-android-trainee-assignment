package com.obpiub.myapplication.domain.repository

import com.obpiub.myapplication.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>
    suspend fun saveTask(task: Task): Long
    suspend fun setCompleted(id: Long, completed: Boolean)
}
