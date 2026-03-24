package com.langosta.mission.domain.model

import com.langosta.mission.data.api.HttpCronJob
import com.langosta.mission.data.api.HttpSession
import com.langosta.mission.data.api.HttpUsage

data class GatewayStatus(
    val status: String,
    val mode: String,
    val version: String? = null,
    val uptime: Long? = null,
    val latencyMs: Int? = null
)

data class SessionsInfo(
    val active: Int,
    val total: Int,
    val items: List<HttpSession> = emptyList()
)

data class AgentNode(
    val id: String,
    val name: String,
    val type: String,
    val model: String,
    val tokensIn: Long = 0L,
    val tokensOut: Long = 0L,
    val utilization: Int = 0,
    val lastSeen: String = "unknown",
    val status: String = "idle"
)

data class SystemMetrics(
    val memoryPercent: Int,
    val diskPercent: Int,
    val errors: Int,
    val queueSize: Int
)

data class DashboardState(
    val gateway: GatewayStatus,
    val sessions: SessionsInfo,
    val agents: List<AgentNode>,
    val system: SystemMetrics,
    val auditEvents24h: Int,
    val loginFailures24h: Int,
    val cronJobs: List<HttpCronJob> = emptyList(),
    val usage: HttpUsage? = null
)

data class SessionInfo(
    val sessionKey: String,
    val agentId: String,
    val model: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val totalTokens: Long,
    val messagesCount: Int
) {
    fun isActive(): Boolean = status in listOf("active", "running", "streaming")
}

data class OpenClawBootstrapConfig(
    val basePath: String,
    val assistantName: String,
    val assistantAvatar: String,
    val assistantAgentId: String,
    val serverVersion: String? = null
)
