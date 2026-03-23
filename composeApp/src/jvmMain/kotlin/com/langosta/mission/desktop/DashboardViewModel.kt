package com.langosta.mission.desktop

import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.data.api.WebSocketManager
import com.langosta.mission.data.repository.DashboardRepository
import com.langosta.mission.domain.model.DashboardState
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Connected(val data: DashboardState) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

class DashboardViewModel {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _incidentEvents = MutableStateFlow<List<String>>(emptyList())
    val incidentEvents = _incidentEvents.asStateFlow()

    private fun buildRepository(): DashboardRepository =
        DashboardRepository(OpenClawClient(ConfigManager.getServerUrl()))

    private fun buildWebSocket(): WebSocketManager =
        WebSocketManager(ConfigManager.getWebSocketUrl())

    fun startPolling() {
        scope.launch {
            val repository = buildRepository()
            repository.dashboardStream().collect { state ->
                _uiState.value = DashboardUiState.Connected(state)
                AppLogger.i("DashboardViewModel", "Dashboard updated")
            }
        }
    }

    fun startIncidentStream() {
        scope.launch {
            val ws = buildWebSocket()
            try {
                ws.connectWithRetry("/ws/broadcast")
            } catch (e: Exception) {
                AppLogger.e("DashboardViewModel", "WebSocket error", e)
            }
            ws.events.collect { event ->
                val current = _incidentEvents.value.toMutableList()
                current.add(0, event)
                _incidentEvents.value = current.take(50)
            }
        }
    }

    fun retry() {
        _uiState.value = DashboardUiState.Loading
        startPolling()
    }

    fun dispose() {
        scope.cancel()
    }
}
