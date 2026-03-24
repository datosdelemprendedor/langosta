package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
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
import com.langosta.mission.domain.model.Agent
import com.langosta.mission.domain.model.AgentStatus

private val AgentGreen = Color(0xFF4CAF50)
private val AgentGrey  = Color(0xFF78909C)
private val AgentRed   = Color(0xFFF44336)
private val AgentAmber = Color(0xFFFFC107)

@Composable
fun AgentsScreen(
    viewModel: DashboardViewModel,
    onOpenSkills: (Agent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val agents  by viewModel.agents.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startPolling()
        viewModel.loadAgents()
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Agentes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Nodos de procesamiento del gateway",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { viewModel.loadAgents() }) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Actualizar")
            }
        }

        // Stats pills
        if (agents.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AgentStatPill("Total",   "${agents.size}",                                                         MaterialTheme.colorScheme.primary)
                AgentStatPill("Activos", "${agents.count { it.status == AgentStatus.ACTIVE }}",                    AgentGreen)
                AgentStatPill("Ocupados","${agents.count { it.status == AgentStatus.BUSY }}",                      AgentAmber)
                AgentStatPill("Idle",    "${agents.count { it.status == AgentStatus.IDLE }}",                      AgentGrey)
                AgentStatPill("Error",   "${agents.count { it.status == AgentStatus.ERROR }}",                     AgentRed)
            }
        }

        when {
            agents.isEmpty() && uiState is DashboardUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            uiState is DashboardUiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        Text((uiState as DashboardUiState.Error).message, color = MaterialTheme.colorScheme.error)
                    }
                }
            agents.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.SmartToy, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Sin agentes conectados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(agents, key = { it.id }) { agent ->
                        AgentCard(agent = agent, onClick = { onOpenSkills(agent) })
                    }
                }
        }
    }
}

@Composable
private fun AgentCard(agent: Agent, onClick: () -> Unit) {
    val statusColor = when (agent.status) {
        AgentStatus.ACTIVE -> AgentGreen
        AgentStatus.BUSY   -> AgentAmber
        AgentStatus.ERROR  -> AgentRed
        AgentStatus.IDLE   -> AgentGrey
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji + nombre + modelo
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(agent.emoji, style = MaterialTheme.typography.titleLarge)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(agent.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(agent.model,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (agent.skills.isNotEmpty()) {
                        Text("${agent.skills.count { it.enabled }} skills activos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // Status + indicador
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusChip(agent.status.label)
                    Text("Ver skills ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            }
        }
    }
}

@Composable
private fun AgentStatPill(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
