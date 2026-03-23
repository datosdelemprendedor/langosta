package com.langosta.mission.desktop

import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.data.repository.TaskRepository
import com.langosta.mission.domain.model.Agent
import com.langosta.mission.domain.model.Task
import com.langosta.mission.domain.model.TaskStatus
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import com.langosta.mission.util.NotificationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskViewModel(private val repository: TaskRepository) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _serverStatus = MutableStateFlow(false)
    val serverStatus = _serverStatus.asStateFlow()

    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents = _agents.asStateFlow()

    val tasks = repository.tasks

    fun fetchTasks() {
        scope.launch {
            _isLoading.value = true
            try {
                repository.fetchTasks()
                AppLogger.i("TaskViewModel", "Tasks loaded")
            } catch (e: Exception) {
                _error.value = e.message
                AppLogger.e("TaskViewModel", "Error fetching tasks", e)
                NotificationManager.error("Error", e.message ?: "Unknown error")
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            val client = OpenClawClient(ConfigManager.getServerUrl())
            client.getTasks()
            _serverStatus.value = true
            true
        } catch (e: Exception) {
            _serverStatus.value = false
            false
        }
    }

    fun fetchAgents() {
        scope.launch {
            try {
                val client = OpenClawClient(ConfigManager.getServerUrl())
                _agents.value = client.getAgents()
                AppLogger.i("TaskViewModel", "Agents loaded")
            } catch (e: Exception) {
                AppLogger.e("TaskViewModel", "Error fetching agents", e)
            }
        }
    }

    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        scope.launch {
            try {
                repository.updateStatus(taskId, status)
                NotificationManager.success("Updated", "Task status changed to ${status.name}")
            } catch (e: Exception) {
                _error.value = e.message
                AppLogger.e("TaskViewModel", "Error updating task", e)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun dispose() {
        scope.cancel()
    }
}
