package com.langosta.mission.data.db

import com.langosta.mission.domain.model.Task
import com.langosta.mission.domain.model.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DatabaseManager {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val _agents = MutableStateFlow<List<String>>(emptyList())
    val agents = _agents.asStateFlow()

    fun saveTasks(tasks: List<Task>) {
        _tasks.value = tasks
    }

    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) task.copy(
                status = status,
                updatedAt = System.currentTimeMillis()
            ) else task
        }
    }

    fun getTaskById(taskId: String): Task? =
        _tasks.value.find { it.id == taskId }

    fun clearAll() {
        _tasks.value = emptyList()
        _agents.value = emptyList()
    }
}
