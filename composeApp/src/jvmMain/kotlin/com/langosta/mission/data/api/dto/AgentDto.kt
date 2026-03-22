package com.langosta.mission.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AgentDto(
    val id: String,
    val name: String,
    val model: String,
    val isOnline: Boolean = false,
    val currentTaskId: String? = null,
    val lastSeen: Long = 0L
)
