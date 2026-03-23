package com.langosta.mission.data

data class TaskHistoryEntry(
    val id: Int = 0,
    val taskId: String,
    val title: String,
    val agentName: String,
    val startedAt: Long,
    val completedAt: Long,
    val durationSeconds: Long,
    val result: String  // "COMPLETED" | "FAILED"
)
