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
    val uptime: Long? = null
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
    val tokensIn: Long,
    val tokensOut: Long,
    val utilization: Int,
    val lastSeen: String
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
