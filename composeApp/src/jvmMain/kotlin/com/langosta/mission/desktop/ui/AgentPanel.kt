package com.langosta.mission.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.langosta.mission.desktop.TaskViewModel

@Composable
fun AgentPanel(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val agents by viewModel.agents.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchAgents()
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(200.dp)
            .padding(12.dp)
    ) {
        Text(
            text = "Agentes",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        val online = agents.count { it.isOnline }
        Text(
            text = "$online / ${agents.size} online",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (agents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sin agentes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(agents) { agent ->
                    AgentCard(agent = agent)
                }
            }
        }
    }
}
