package com.langosta.mission.data.repository

import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.data.db.DatabaseManager
import com.langosta.mission.domain.model.Task
import com.langosta.mission.domain.model.TaskStatus
import kotlinx.coroutines.flow.StateFlow

class TaskRepository(
    private val client: OpenClawClient,
    private val db: DatabaseManager
) {

    val tasks: StateFlow<List<Task>> = db.tasks

    suspend fun fetchTasks() {
        val tasks = client.getTasks()
        db.saveTasks(tasks)
    }

    suspend fun updateStatus(taskId: String, status: TaskStatus) {
        client.updateTaskStatus(taskId, status.name)
        db.updateTaskStatus(taskId, status)
    }

    fun getTaskById(taskId: String): Task? =
        db.getTaskById(taskId)
}
