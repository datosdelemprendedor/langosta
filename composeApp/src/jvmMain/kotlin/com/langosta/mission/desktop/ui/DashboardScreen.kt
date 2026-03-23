package com.langosta.mission.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.langosta.mission.desktop.DashboardUiState
import com.langosta.mission.desktop.DashboardViewModel
import com.langosta.mission.domain.model.AgentNode
import com.langosta.mission.domain.model.DashboardState

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val incidentEvents by viewModel.incidentEvents.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startPolling()
        viewModel.startIncidentStream()
    }

    when (val state = uiState) {
        is DashboardUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is DashboardUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.retry() }) { Text("Reintentar") }
            }
        }
        is DashboardUiState.Connected -> DashboardContent(
            data = state.data,
            incidentEvents = incidentEvents,
            modifier = modifier
        )
    }
}

@Composable
private fun DashboardContent(
    data: DashboardState,
    incidentEvents: List<String>,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Status Cards ──────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusCard("Gateway", data.gateway.status, Modifier.weight(1f))
                StatusCard("Sesiones", "${data.sessions.active} activas", Modifier.weight(1f))
                StatusCard("Queue", "${data.system.queueSize} tareas", Modifier.weight(1f))
                StatusCard("Errores 24h", "${data.loginFailures24h}", Modifier.weight(1f))
            }
        }

        // ── System Health ─────────────────────────────────────
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("System Health", style = MaterialTheme.typography.titleMedium)
                    MetricRow("Memoria", "${data.system.memoryPercent}%")
                    MetricRow("Disco", "${data.system.diskPercent}%")
                    MetricRow("Errores", "${data.system.errors}")
                }
            }
        }

        // ── Agentes ───────────────────────────────────────────
        item {
            Text("Agentes", style = MaterialTheme.typography.titleMedium)
        }
        items(data.agents) { agent ->
            AgentNodeCard(agent)
        }

        // ── Incident Stream ───────────────────────────────────
        item {
            Text("Incident Stream", style = MaterialTheme.typography.titleMedium)
        }
        if (incidentEvents.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Sin eventos recientes", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            items(incidentEvents) { event ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = event,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AgentNodeCard(agent: AgentNode) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(agent.name, style = MaterialTheme.typography.titleSmall)
                Text(agent.model, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${agent.utilization}%", style = MaterialTheme.typography.titleSmall)
                Text(agent.type, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
