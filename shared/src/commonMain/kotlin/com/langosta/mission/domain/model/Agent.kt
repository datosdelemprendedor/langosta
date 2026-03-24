package com.langosta.mission.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val emoji: String = "🤖",
    val model: String,
    val status: AgentStatus = AgentStatus.IDLE,
    val isOnline: Boolean = false,
    val currentTaskId: String? = null,
    val lastSeen: Long = 0L,
    val skills: List<AgentSkill> = emptyList()
)

@Serializable
enum class AgentStatus {
    ACTIVE, IDLE, BUSY, ERROR;

    val label: String get() = when (this) {
        ACTIVE -> "active"
        IDLE   -> "idle"
        BUSY   -> "busy"
        ERROR  -> "error"
    }
}

@Serializable
data class AgentSkill(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val binding: String? = null   // ej: "read_file", "web_search"
)
