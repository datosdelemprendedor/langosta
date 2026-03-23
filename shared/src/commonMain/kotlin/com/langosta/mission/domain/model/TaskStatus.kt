package com.langosta.mission.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
}
