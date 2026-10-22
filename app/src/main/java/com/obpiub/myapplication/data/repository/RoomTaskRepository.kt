package com.obpiub.myapplication.data.repository

import com.obpiub.myapplication.data.local.TaskDao
import com.obpiub.myapplication.data.local.TaskEntity
import com.obpiub.myapplication.domain.model.Task
import com.obpiub.myapplication.domain.repository.TaskRepository
import kotlinx.coroutines.flow.map

class RoomTaskRepository(private val dao: TaskDao) : TaskRepository {
    override fun observeTasks() = dao.observeAll().map { tasks ->
        tasks.map { Task(it.id, it.title, it.isCompleted, it.createdAt) }
    }

    override suspend fun saveTask(task: Task): Long {
        require(task.title.isNotBlank()) { "Название задачи не должно быть пустым" }
        val entity = TaskEntity(task.id, task.title.trim(), task.isCompleted, task.createdAt)
        if (task.id == 0L) return dao.insert(entity)
        dao.update(entity)
        return task.id
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) = dao.setCompleted(id, completed)
}
