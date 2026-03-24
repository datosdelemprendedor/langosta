package com.langosta.mission.data.repository

import com.langosta.mission.data.api.HttpAgent
import com.langosta.mission.data.api.HttpCronJob
import com.langosta.mission.data.api.HttpSession
import com.langosta.mission.data.api.HttpUsage
import com.langosta.mission.data.api.OpenClawGatewayClient
import com.langosta.mission.data.api.OpenClawHttpClient
import com.langosta.mission.domain.model.*
import com.langosta.mission.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class DashboardRepository(
    private val httpClient: OpenClawHttpClient = OpenClawHttpClient(),
    private val gatewayClient: OpenClawGatewayClient? = null
) {

    fun dashboardStream(): Flow<DashboardState> = flow {
        while (true) {
            try {
                val state = fetchDashboard()
                emit(state)
            } catch (e: Exception) {
                AppLogger.e("DashboardRepository", "Error: ${e.message}", e)
            }
            delay(5_000)
        }
    }

    private suspend fun fetchDashboard(): DashboardState = withContext(Dispatchers.IO) {
        AppLogger.i("DashboardRepository", "Fetching dashboard data...")

        // Paralelo: status + sessions + agents + cron + usage
        val status = try { httpClient.getStatus() } catch (e: Exception) { null }
        val sessions = try { httpClient.getSessions() } catch (e: Exception) { emptyList() }
        val agents = try { httpClient.getAgents() } catch (e: Exception) { emptyList() }
        val cronJobs = try { httpClient.getCronJobs() } catch (e: Exception) { emptyList() }
        val usage = try { httpClient.getUsage() } catch (e: Exception) { null }

        // Si HTTP no responde sesiones, intentar via CLI
        val effectiveSessions = if (sessions.isEmpty() && gatewayClient != null) {
            try {
                gatewayClient.listSessions().map { s ->
                    HttpSession(
                        id = s.sessionKey,
                        agentId = s.agentId,
                        model = s.model,
                        status = s.status,
                        messagesCount = s.messagesCount,
                        tokensIn = 0L,
                        tokensOut = s.totalTokens,
                        createdAt = s.createdAt,
                        updatedAt = s.updatedAt
                    )
                }
            } catch (e: Exception) { emptyList() }
        } else sessions

        val activeSessions = effectiveSessions.filter { it.isActive }
        val gatewayOnline = status?.gateway == "online" || effectiveSessions.isNotEmpty()

        AppLogger.i("DashboardRepository",
            "Dashboard: ${effectiveSessions.size} sessions, ${activeSessions.size} active, " +
            "${agents.size} agents, ${cronJobs.size} crons")

        DashboardState(
            gateway = GatewayStatus(
                status = status?.gateway ?: if (gatewayOnline) "online" else "offline",
                mode = status?.transport ?: "cli",
                version = null,
                uptime = null,
                latencyMs = status?.latencyMs
            ),
            sessions = SessionsInfo(
                active = activeSessions.size,
                total = effectiveSessions.size,
                items = effectiveSessions
            ),
            agents = agents.map { a ->
                AgentNode(
                    id = a.id,
                    name = a.name,
                    type = a.type,
                    model = a.model,
                    tokensIn = a.tokensIn,
                    tokensOut = a.tokensOut,
                    utilization = if (a.activeSessions > 0) 100 else 0,
                    lastSeen = "now",
                    status = a.status
                )
            }.ifEmpty {
                // fallback: agente generico si no hay endpoint de agentes
                listOf(AgentNode(
                    id = "main", name = "OpenClaw", type = "assistant",
                    model = "unknown", tokensIn = 0L, tokensOut = 0L,
                    utilization = if (activeSessions.isNotEmpty()) 100 else 0,
                    lastSeen = "now", status = if (activeSessions.isNotEmpty()) "busy" else "idle"
                ))
            },
            cronJobs = cronJobs,
            usage = usage,
            system = SystemMetrics(
                memoryPercent = 0,
                diskPercent = 0,
                errors = 0,
                queueSize = activeSessions.size
            ),
            auditEvents24h = effectiveSessions.sumOf { it.messagesCount },
            loginFailures24h = 0
        )
    }
}
