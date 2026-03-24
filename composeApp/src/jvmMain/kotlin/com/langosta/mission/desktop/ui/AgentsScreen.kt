package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langosta.mission.desktop.DashboardUiState
import com.langosta.mission.desktop.DashboardViewModel
import com.langosta.mission.domain.model.AgentNode

private val Green = Color(0xFF4CAF50)
private val Grey  = Color(0xFF78909C)
private val Red   = Color(0xFFF44336)
private val Amber = Color(0xFFFFC107)

@Composable
fun AgentsScreen(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.startPolling() }

    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Agentes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Nodos de procesamiento del gateway", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { viewModel.retry() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Actualizar")
            }
        }

        when (val state = uiState) {
            is DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DashboardUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is DashboardUiState.Connected -> {
                val agents = state.data.agents

                // Stats bar
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AgentStatPill("Total", "${agents.size}", MaterialTheme.colorScheme.primary)
                    AgentStatPill("Activos", "${agents.count { it.status in listOf("active","busy") }}", Green)
                    AgentStatPill("Idle", "${agents.count { it.status == "idle" }}", Grey)
                    AgentStatPill("Error", "${agents.count { it.status == "error" }}", Red)
                }

                if (agents.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Sin agentes conectados", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(agents) { agent -> AgentDetailCard(agent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentDetailCard(agent: AgentNode) {
    val statusColor = when (agent.status) {
        "busy", "active" -> Green
        "idle"           -> Grey
        "error"          -> Red
        else             -> Amber
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Column {
                        Text(agent.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(agent.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        agent.model,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    StatusChip(agent.status)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Métricas
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBox("Tokens entrada", formatTokens(agent.tokensIn), Modifier.weight(1f))
                MetricBox("Tokens salida", formatTokens(agent.tokensOut), Modifier.weight(1f))
                MetricBox("Utilización", "${agent.utilization}%", Modifier.weight(1f),
                    valueColor = when {
                        agent.utilization >= 90 -> Red
                        agent.utilization >= 60 -> Amber
                        else -> Green
                    }
                )
                MetricBox("Última vez", agent.lastSeen, Modifier.weight(1.5f))
            }

            // Barra de utilización
            if (agent.utilization > 0) {
                LinearProgressIndicator(
                    progress = { agent.utilization / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = when {
                        agent.utilization >= 90 -> Red
                        agent.utilization >= 60 -> Amber
                        else -> Green
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AgentStatPill(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
