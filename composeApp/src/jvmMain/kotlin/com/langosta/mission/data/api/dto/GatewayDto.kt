package com.langosta.mission.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class OpenClawBootstrapConfigDto(
    val basePath: String,
    val assistantName: String,
    val assistantAvatar: String,
    val assistantAgentId: String,
    val serverVersion: String? = null
)

@Serializable
data class GatewayStatusDto(
    val status: String,
    val mode: String,
    val version: String? = null,
    val uptime: Long? = null
)

@Serializable
data class SessionsInfoDto(
    val active: Int,
    val total: Int
)

@Serializable
data class AgentNodeDto(
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
data class SystemMetricsDto(
    val memoryPercent: Int,
    val diskPercent: Int,
    val errors: Int,
    val queueSize: Int
)

@Serializable
data class DashboardStateDto(
    val gateway: GatewayStatusDto,
    val sessions: SessionsInfoDto,
    val agents: List<AgentNodeDto>,
    val system: SystemMetricsDto,
    val auditEvents24h: Int,
    val loginFailures24h: Int
)
