package com.langosta.mission.desktop

import com.langosta.mission.data.api.OpenClawGatewayClient
import com.langosta.mission.data.api.WebSocketManager
import com.langosta.mission.data.repository.AgentRepository
import com.langosta.mission.data.repository.DashboardRepository
import com.langosta.mission.domain.model.Agent
import com.langosta.mission.domain.model.DashboardState
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Connected(val data: DashboardState) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

class DashboardViewModel {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents = _agents.asStateFlow()

    private val _incidentEvents = MutableStateFlow<List<String>>(emptyList())
    val incidentEvents = _incidentEvents.asStateFlow()

    private val _wsConnectionState = MutableStateFlow<String>("Disconnected")
    val wsConnectionState = _wsConnectionState.asStateFlow()

    private val wsManager = WebSocketManager(ConfigManager.getWebSocketUrl())
    private val gatewayClient = OpenClawGatewayClient()
    private val repository = DashboardRepository(gatewayClient = gatewayClient)
    private val agentRepository = AgentRepository()

    private val isPolling   = AtomicBoolean(false)
    private val isStreaming  = AtomicBoolean(false)

    fun startPolling() {
        if (!isPolling.compareAndSet(false, true)) {
            AppLogger.i("DashboardViewModel", "startPolling() ya activo, ignorando")
            return
        }
        scope.launch {
            try {
                repository.dashboardStream().collect { state ->
                    _uiState.value = DashboardUiState.Connected(state)
                    AppLogger.i("DashboardViewModel", "Dashboard updated")
                }
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Error desconocido")
                AppLogger.e("DashboardViewModel", "Polling error", e)
            } finally {
                isPolling.set(false)
            }
        }
    }

    fun loadAgents() {
        scope.launch {
            try {
                agentRepository.agentsStream().collect { agents ->
                    _agents.value = agents
                }
            } catch (e: Exception) {
                AppLogger.e("DashboardViewModel", "loadAgents error: ${e.message}", e)
            }
        }
    }

    fun toggleSkill(agentId: String, skillId: String, enabled: Boolean) {
        scope.launch {
            try {
                agentRepository.toggleSkill(agentId, skillId, enabled)
            } catch (e: Exception) {
                AppLogger.e("DashboardViewModel", "toggleSkill error: ${e.message}", e)
            }
        }
    }

    fun startIncidentStream() {
        if (!isStreaming.compareAndSet(false, true)) {
            AppLogger.i("DashboardViewModel", "startIncidentStream() ya activo, ignorando")
            return
        }
        scope.launch {
            try { wsManager.connectWithRetry() }
            catch (e: Exception) { AppLogger.e("DashboardViewModel", "WS error", e) }
        }
        scope.launch {
            wsManager.connectionState.collect { state ->
                _wsConnectionState.value = when (state) {
                    is WebSocketManager.ConnectionState.Disconnected -> "REST polling"
                    is WebSocketManager.ConnectionState.Connecting   -> "Conectando..."
                    is WebSocketManager.ConnectionState.Connected    -> "WebSocket OK"
                    is WebSocketManager.ConnectionState.Error        -> "Error: ${state.message}"
                }
            }
        }
        scope.launch {
            wsManager.events.collect { event ->
                val current = _incidentEvents.value.toMutableList()
                current.add(0, event)
                _incidentEvents.value = current.take(50)
            }
        }
    }

    fun retry() {
        isPolling.set(false)
        _uiState.value = DashboardUiState.Loading
        startPolling()
    }

    fun dispose() {
        wsManager.close()
        scope.cancel()
    }
}
