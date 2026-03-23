package com.langosta.mission.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.langosta.mission.domain.model.Task
import com.langosta.mission.domain.model.TaskStatus

@Composable
fun StatsBar(tasks: List<Task>) {
    val total = tasks.size
    val pending = tasks.count { it.status == TaskStatus.PENDING }
    val inProgress = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val completed = tasks.count { it.status == TaskStatus.COMPLETED }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Total", total, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        StatCard("Pending", pending, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        StatCard("In Progress", inProgress, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        StatCard("Completed", completed, MaterialTheme.colorScheme.outline, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: Int, color: Color, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
