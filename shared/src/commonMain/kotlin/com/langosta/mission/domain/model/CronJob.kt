package com.langosta.mission.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CronJob(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val description: String = "",
    val agentId: String? = null,
    val scheduleKind: String = "every",   // "cron" | "every" | "at"
    val scheduleExpr: String = "",         // cron expr o intervalo legible
    val payloadMessage: String = "",
    val model: String? = null,
    val deliveryMode: String = "none",     // "none" | "announce" | "webhook"
    val deliveryChannel: String? = null,
    val deliveryTo: String? = null,
    val nextRunAtMs: Long? = null,
    val lastRunAtMs: Long? = null,
    val lastStatus: String? = null,
    val lastError: String? = null,
    val consecutiveErrors: Int = 0
)
