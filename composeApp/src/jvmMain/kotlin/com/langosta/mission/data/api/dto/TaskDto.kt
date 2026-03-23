package com.langosta.mission.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val description: String,
    val status: String = "PENDING",
    val assignedAgentId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val result: String? = null,
    val errorMessage: String? = null
)
