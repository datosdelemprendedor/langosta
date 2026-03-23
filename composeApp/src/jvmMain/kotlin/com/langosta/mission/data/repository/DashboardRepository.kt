package com.langosta.mission.data.repository

import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.data.api.OpenClawGatewayClient
import com.langosta.mission.data.api.OpenClawRpcClient
import com.langosta.mission.domain.model.*
import com.langosta.mission.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DashboardRepository(
    private val httpClient: OpenClawClient,
    private val gatewayClient: OpenClawGatewayClient? = null
) {

    fun dashboardStream(): Flow<DashboardState> = flow {
        while (true) {
            try {
                AppLogger.i("DashboardRepository", "Fetching data from OpenClaw CLI...")
                
                val config = httpClient.getBootstrapConfig()
                
                val sessions = try {
                    gatewayClient?.listSessions() ?: emptyList()
                } catch (e: Exception) {
                    AppLogger.w("DashboardRepository", "CLI not available: ${e.message}")
                    emptyList()
                }

                val activeSessions = sessions.filter { it.isActive() }

                val state = DashboardState(
                    gateway = GatewayStatus(
                        status = "online",
                        mode = "local",
                        version = config.serverVersion ?: "unknown"
                    ),
                    sessions = SessionsInfo(
                        active = activeSessions.size,
                        total = sessions.size
                    ),
                    agents = listOf(
                        AgentNode(
                            id = config.assistantAgentId,
                            name = config.assistantName,
                            type = "assistant",
                            model = "openclaw",
                            tokensIn = 0,
                            tokensOut = 0,
                            utilization = if (activeSessions.isNotEmpty()) 100 else 0,
                            lastSeen = "now"
                        )
                    ),
                    system = SystemMetrics(
                        memoryPercent = 0,
                        diskPercent = 0,
                        errors = 0,
                        queueSize = activeSessions.size
                    ),
                    auditEvents24h = sessions.sumOf { it.messagesCount },
                    loginFailures24h = 0
                )
                AppLogger.i("DashboardRepository", "Dashboard: ${state.sessions.total} sessions, ${state.sessions.active} active")
                emit(state)
            } catch (e: Exception) {
                AppLogger.e("DashboardRepository", "Error: ${e.message}", e)
            }
            delay(5_000)
        }
    }

    suspend fun getBootstrapConfig() = httpClient.getBootstrapConfig()
}
