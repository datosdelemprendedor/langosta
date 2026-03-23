package com.langosta.mission.desktop

import com.langosta.mission.data.TaskHistoryDatabase
import com.langosta.mission.data.TaskHistoryEntry
import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.data.api.OpenClawGatewayClient
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

    private val _history = MutableStateFlow<List<TaskHistoryEntry>>(emptyList())
    val history = _history.asStateFlow()

    val tasks = repository.tasks

    init {
        TaskHistoryDatabase.init()
    }

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
            client.ping()
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
                val task = tasks.value.find { it.id == taskId }
                repository.updateStatus(taskId, status)

                if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                    val now = System.currentTimeMillis()
                    val started = task?.createdAt ?: now
                    val agentName = _agents.value
                        .find { it.id == task?.assignedAgentId }?.name ?: "Sin agente"

                    TaskHistoryDatabase.insert(
                        TaskHistoryEntry(
                            taskId          = taskId,
                            title           = task?.title ?: taskId,
                            agentName       = agentName,
                            startedAt       = started,
                            completedAt     = now,
                            durationSeconds = (now - started) / 1000,
                            result          = status.name
                        )
                    )
                    _history.value = TaskHistoryDatabase.getAll()
                }
                NotificationManager.success("Updated", "Task status → ${status.name}")
            } catch (e: Exception) {
                _error.value = e.message
                AppLogger.e("TaskViewModel", "Error updating task", e)
            }
        }
    }

    // Crear tarea en OpenClaw via CLI
    fun createTask(title: String, description: String, agentId: String?) {
        scope.launch {
            try {
                val targetAgent = agentId ?: _agents.value.firstOrNull()?.id ?: "main"
                val fullTask = "Tarea: $title\nDescripción: $description"
                
                AppLogger.i("TaskViewModel", "Creating task via CLI for agent: $targetAgent")
                
                val gatewayClient = OpenClawGatewayClient()
                val response = gatewayClient.sendMessage(targetAgent, fullTask)
                
                AppLogger.i("TaskViewModel", "Task response: $response")
                NotificationManager.success("Tarea creada", "Agent: $targetAgent")
                
            } catch (e: Exception) {
                AppLogger.e("TaskViewModel", "Error creating task: ${e.message}", e)
                _error.value = "Error: ${e.message}"
                NotificationManager.error("Error", e.message ?: "Unknown error")
            }
        }
    }

    fun startAutoRefresh() {
        scope.launch {
            while (true) {
                delay(5000)
                try {
                    val client = OpenClawClient(ConfigManager.getServerUrl())
                    _agents.value = client.getAgents()
                    _serverStatus.value = true
                } catch (e: Exception) {
                    _serverStatus.value = false
                    AppLogger.e("TaskViewModel", "Auto-refresh error", e)
                }
            }
        }
    }

    fun loadHistory() {
        scope.launch { _history.value = TaskHistoryDatabase.getAll() }
    }

    fun clearHistory() {
        scope.launch {
            TaskHistoryDatabase.clearAll()
            _history.value = emptyList()
        }
    }

    fun clearError() { _error.value = null }

    fun dispose() { scope.cancel() }
}
