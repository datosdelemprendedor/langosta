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
import androidx.compose.ui.graphics.vector.ImageVector
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
    val wsConnectionState by viewModel.wsConnectionState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startPolling()
        viewModel.startIncidentStream()
    }

    when (val state = uiState) {
        is DashboardUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Conectando al gateway...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        is DashboardUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.retry() }) { Text("Reintentar") }
            }
        }
        is DashboardUiState.Connected -> DashboardContent(
            data = state.data,
            wsState = wsConnectionState,
            modifier = modifier
        )
    }
}

@Composable
private fun DashboardContent(
    data: DashboardState,
    wsState: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Fila 1: KPI Cards ─────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val gatewayColor = when (data.gateway.status) {
                    "online" -> Color(0xFF4CAF50)
                    "degraded" -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
                KpiCard(
                    icon = Icons.Default.CheckCircle,
                    label = "Gateway",
                    value = data.gateway.status.uppercase(),
                    color = gatewayColor,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    icon = Icons.Default.Forum,
                    label = "Sesiones",
                    value = "${data.sessions.active} / ${data.sessions.total}",
                    subtitle = "activas / total",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    icon = Icons.Default.SmartToy,
                    label = "Agentes",
                    value = "${data.agents.size}",
                    subtitle = "${data.agents.count { it.status == "busy" }} ocupados",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    icon = Icons.Default.Schedule,
                    label = "Cron Jobs",
                    value = "${data.cronJobs.size}",
                    subtitle = "${data.cronJobs.count { it.enabled }} activos",
                    modifier = Modifier.weight(1f)
                )
                data.gateway.latencyMs?.let {
                    KpiCard(
                        icon = Icons.Default.Speed,
                        label = "Latencia",
                        value = "${it}ms",
                        color = if (it < 100) Color(0xFF4CAF50) else if (it < 500) Color(0xFFFFC107) else Color(0xFFF44336),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Fila 2: Uso de tokens ─────────────────────────────────
        data.usage?.let { usage ->
            item {
                SectionCard(title = "Uso de Tokens", icon = Icons.Default.Token) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        UsageMetric("Tokens entrada", formatTokens(usage.tokensIn))
                        UsageMetric("Tokens salida", formatTokens(usage.tokensOut))
                        UsageMetric("Total", formatTokens(usage.tokensIn + usage.tokensOut))
                        UsageMetric("Costo (${usage.period})", "${usage.currency} ${ "%.4f".format(usage.totalCost) }")
                    }
                }
            }
        }

        // ── Agentes ───────────────────────────────────────────────
        if (data.agents.isNotEmpty()) {
            item {
                SectionCard(title = "Agentes", icon = Icons.Default.SmartToy) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.agents.forEach { agent -> AgentRow(agent) }
                    }
                }
            }
        }

        // ── Sesiones recientes ────────────────────────────────────
        if (data.sessions.items.isNotEmpty()) {
            item {
                SectionCard(title = "Sesiones Recientes", icon = Icons.Default.History) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.sessions.items.take(10).forEach { session ->
                            SessionRow(
                                id = session.id.take(20),
                                model = session.model,
                                status = session.status,
                                messages = session.messagesCount,
                                tokens = session.tokensIn + session.tokensOut
                            )
                        }
                    }
                }
            }
        }

        // ── Cron Jobs ─────────────────────────────────────────────
        if (data.cronJobs.isNotEmpty()) {
            item {
                SectionCard(title = "Cron Jobs", icon = Icons.Default.Schedule) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.cronJobs.forEach { job ->
                            CronJobRow(job.name, job.schedule, job.enabled, job.status, job.nextRun)
                        }
                    }
                }
            }
        }

        // ── Sistema ───────────────────────────────────────────────
        item {
            SectionCard(title = "Sistema", icon = Icons.Default.Memory) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    UsageMetric("Modo", data.gateway.mode)
                    UsageMetric("Cola", "${data.system.queueSize} tareas")
                    UsageMetric("Errores (24h)", "${data.system.errors}")
                    UsageMetric("Mensajes (24h)", "${data.auditEvents24h}")
                    UsageMetric("Transporte", wsState)
                }
            }
        }
    }
}

// ── Componentes ───────────────────────────────────────────────────────────────

@Composable
private fun KpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, color = color)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun AgentRow(agent: AgentNode) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val dotColor = when (agent.status) {
                "busy", "active" -> Color(0xFF4CAF50)
                "idle" -> Color(0xFF9E9E9E)
                else -> Color(0xFFFFC107)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Column {
                Text(agent.name, style = MaterialTheme.typography.bodyMedium)
                Text(agent.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("↑${formatTokens(agent.tokensIn)} ↓${formatTokens(agent.tokensOut)}", style = MaterialTheme.typography.bodySmall)
            StatusChip(agent.status)
        }
    }
}

@Composable
private fun SessionRow(id: String, model: String, status: String, messages: Int, tokens: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(id, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$messages msgs", style = MaterialTheme.typography.bodySmall)
            Text(formatTokens(tokens) + " tkn", style = MaterialTheme.typography.bodySmall)
            StatusChip(status)
        }
    }
}

@Composable
private fun CronJobRow(name: String, schedule: String, enabled: Boolean, status: String, nextRun: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(schedule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nextRun != null) {
                Text("next: $nextRun", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(if (enabled) status else "disabled")
        }
    }
}

@Composable
private fun UsageMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusChip(status: String) {
    val (bg, fg) = when (status.lowercase()) {
        "active", "online", "running", "busy" -> Color(0xFF1B5E20) to Color(0xFF81C784)
        "idle", "scheduled" -> Color(0xFF263238) to Color(0xFF90A4AE)
        "error", "failed" -> Color(0xFF7F0000) to Color(0xFFEF9A9A)
        "disabled" -> Color(0xFF212121) to Color(0xFF757575)
        else -> Color(0xFF1A237E) to Color(0xFF90CAF9)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(status, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

private fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "${ "%.1f".format(n / 1_000_000.0) }M"
    n >= 1_000 -> "${ "%.1f".format(n / 1_000.0) }K"
    else -> "$n"
}
