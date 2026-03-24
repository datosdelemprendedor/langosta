package com.langosta.mission.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OpenClawBootstrapConfig(
    val basePath: String,
    val assistantName: String,
    val assistantAvatar: String,
    val assistantAgentId: String,
    val serverVersion: String? = null
)

@Serializable
data class GatewayStatus(
    val status: String,
    val mode: String,
    val version: String? = null,
    val uptime: Long? = null,
    val latencyMs: Int? = null
)

@Serializable
data class SessionsInfo(
    val active: Int,
    val total: Int
)

@Serializable
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

@Serializable
data class SystemMetrics(
    val memoryPercent: Int,
    val diskPercent: Int,
    val errors: Int,
    val queueSize: Int
)

@Serializable
data class DashboardState(
    val gateway: GatewayStatus,
    val sessions: SessionsInfo,
    val agents: List<AgentNode>,
    val system: SystemMetrics,
    val auditEvents24h: Int,
    val loginFailures24h: Int
)

@Serializable
data class SessionInfo(
    val sessionKey: String,
    val agentId: String,
    val model: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val totalTokens: Long,
    val messagesCount: Int
)

fun SessionInfo.isActive(): Boolean = status == "running" || status == "processing"

fun SessionInfo.getDurationMs(): Long = if (updatedAt > createdAt) updatedAt - createdAt else 0L
