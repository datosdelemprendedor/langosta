package com.langosta.mission.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AppNotification(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO
)

enum class NotificationType {
    INFO, SUCCESS, WARNING, ERROR
}

object NotificationManager {

    private val _notifications = MutableSharedFlow<AppNotification>()
    val notifications = _notifications.asSharedFlow()

    suspend fun send(title: String, message: String, type: NotificationType = NotificationType.INFO) {
        _notifications.emit(
            AppNotification(
                title = title,
                message = message,
                type = type
            )
        )
    }

    suspend fun success(title: String, message: String) =
        send(title, message, NotificationType.SUCCESS)

    suspend fun error(title: String, message: String) =
        send(title, message, NotificationType.ERROR)

    suspend fun warning(title: String, message: String) =
        send(title, message, NotificationType.WARNING)
}
