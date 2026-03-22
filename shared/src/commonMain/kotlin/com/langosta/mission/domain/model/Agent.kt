package com.langosta.mission.domain.model

data class Agent(
    val id: String,
    val name: String,
    val model: String,
    val isOnline: Boolean = false,
    val currentTaskId: String? = null,
    val lastSeen: Long = 0L
)