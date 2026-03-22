package com.langosta.mission.desktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.langosta.mission.util.AppNotification
import com.langosta.mission.util.NotificationManager
import com.langosta.mission.util.NotificationType

@Composable
fun NotificationPanel() {
    val notifications = remember { mutableStateListOf<AppNotification>() }

    LaunchedEffect(Unit) {
        NotificationManager.notifications.collect { notification ->
            notifications.add(0, notification)
            if (notifications.size > 20) notifications.removeLast()
        }
    }

    Column(modifier = Modifier.fillMaxHeight().width(280.dp).padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Notifications", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { notifications.clear() }) {
                Text("Clear")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(notifications, key = { it.id }) { notification ->
                NotificationItem(notification)
            }
        }
    }
}

@Composable
fun NotificationItem(notification: AppNotification) {
    val containerColor = when (notification.type) {
        NotificationType.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
        NotificationType.ERROR -> MaterialTheme.colorScheme.errorContainer
        NotificationType.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        NotificationType.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(notification.title, style = MaterialTheme.typography.labelMedium)
            Text(notification.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
