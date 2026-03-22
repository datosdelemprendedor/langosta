package com.langosta.mission.domain.model

data class Task(
    val id: String,
    val title: String,
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val assignedAgentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val result: String? = null,
    val errorMessage: String? = null
)
