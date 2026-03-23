package com.langosta.mission.desktop

import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.data.api.WebSocketManager
import com.langosta.mission.data.repository.DashboardRepository
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Connected(val data: com.langosta.mission.domain.model.DashboardState) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

class DashboardViewModel {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _incidentEvents = MutableStateFlow<List<String>>(emptyList())
    val incidentEvents = _incidentEvents.asStateFlow()

    private val wsManager = WebSocketManager(
        ConfigManager.getServerUrl().removePrefix("http://")
    )

    private fun buildRepository(): DashboardRepository =
        DashboardRepository(OpenClawClient(ConfigManager.getServerUrl()))

    fun startPolling() {
        scope.launch {
            try {
                val repository = buildRepository()
                repository.dashboardStream().collect { state ->
                    _uiState.value = DashboardUiState.Connected(state)
                    AppLogger.i("DashboardViewModel", "Dashboard updated")
                }
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Error desconocido")
                AppLogger.e("DashboardViewModel", "Polling error", e)
            }
        }
    }

    fun startIncidentStream() {
        // Conectar WebSocket
        scope.launch {
            try {
                wsManager.connectWithRetry()
            } catch (e: Exception) {
                AppLogger.e("DashboardViewModel", "WebSocket error", e)
            }
        }
        // Consumir eventos
        scope.launch {
            wsManager.events.collect { event ->
                val current = _incidentEvents.value.toMutableList()
                current.add(0, event)
                _incidentEvents.value = current.take(50)
                AppLogger.i("DashboardViewModel", "WS event: $event")
            }
        }
    }

    fun retry() {
        _uiState.value = DashboardUiState.Loading
        startPolling()
    }

    fun dispose() {
        wsManager.close()
        scope.cancel()
    }
}
