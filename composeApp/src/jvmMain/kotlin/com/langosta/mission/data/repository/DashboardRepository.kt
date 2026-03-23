package com.langosta.mission.data.repository

import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DashboardRepository(private val client: OpenClawClient) {

    fun dashboardStream(): Flow<DashboardState> = flow {
        while (true) {
            try {
                val config = client.getBootstrapConfig()
                val state = DashboardState(
                    gateway = GatewayStatus(
                        status = "online",
                        mode = "local",
                        version = config.serverVersion
                    ),
                    sessions = SessionsInfo(active = 0, total = 0),
                    agents = listOf(
                        AgentNode(
                            id = config.assistantAgentId,
                            name = config.assistantName,
                            type = "assistant",
                            model = "openclaw",
                            tokensIn = 0,
                            tokensOut = 0,
                            utilization = 0,
                            lastSeen = "ahora"
                        )
                    ),
                    system = SystemMetrics(
                        memoryPercent = 0,
                        diskPercent = 0,
                        errors = 0,
                        queueSize = 0
                    ),
                    auditEvents24h = 0,
                    loginFailures24h = 0
                )
                emit(state)
            } catch (e: Exception) {
                // silencioso, reintenta en 5s
            }
            delay(5_000)
        }
    }

    suspend fun getBootstrapConfig() = client.getBootstrapConfig()
}
