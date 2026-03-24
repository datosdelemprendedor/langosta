package com.langosta.mission.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val key: String,
    val kind: String = "",
    val sessionId: String = "",
    val model: String = "unknown",
    val updatedAt: Long = 0L,
    val ageMs: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val contextTokens: Long = 0L
)
