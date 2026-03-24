package com.langosta.mission.desktop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langosta.mission.desktop.DashboardUiState
import com.langosta.mission.desktop.DashboardViewModel
import com.langosta.mission.domain.model.AgentNode
import com.langosta.mission.domain.model.DashboardState

private val ColorOnline  = Color(0xFF4CAF50)
private val ColorWarning = Color(0xFFFFC107)
private val ColorError   = Color(0xFFF44336)
private val ColorIdle    = Color(0xFF78909C)
private val ColorInfo    = Color(0xFF42A5F5)

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val wsState by viewModel.wsConnectionState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startPolling()
        viewModel.startIncidentStream()
    }

    when (val state = uiState) {
        is DashboardUiState.Loading -> LoadingView(modifier)
        is DashboardUiState.Error   -> ErrorView(state.message, modifier) { viewModel.retry() }
        is DashboardUiState.Connected -> DashboardContent(data = state.data, wsState = wsState, modifier = modifier)
    }
}

@Composable
private fun LoadingView(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Conectando al gateway...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorView(message: String, modifier: Modifier, onRetry: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Composable
private fun DashboardContent(data: DashboardState, wsState: String, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { KpiRow(data, wsState) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AgentsSection(data)
                    if (data.sessions.items.isNotEmpty()) SessionsSection(data)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SystemSection(data, wsState)
                    if (data.usage != null) UsageSection(data)
                    if (data.cronJobs.isNotEmpty()) CronSection(data)
                }
            }
        }
    }
}

@Composable
private fun KpiRow(data: DashboardState, wsState: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiCard(
            icon = Icons.Filled.CheckCircle,
            label = "Gateway",
            value = data.gateway.status.uppercase(),
            color = gatewayColor(data.gateway.status),
            badge = data.gateway.version,
            pulse = data.gateway.status == "online",
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            icon = Icons.Filled.Forum,
            label = "Sesiones",
            value = "${data.sessions.active}",
            subtitle = "de ${data.sessions.total} totales",
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            icon = Icons.Filled.SmartToy,
            label = "Agentes",
            value = "${data.agents.size}",
            subtitle = "${data.agents.count { it.status in listOf("busy","active") }} activos",
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            icon = Icons.Filled.Shield,
            label = "Auditoría 24h",
            value = "${data.auditEvents24h}",
            subtitle = "${data.loginFailures24h} fallos login",
            color = if (data.loginFailures24h > 5) ColorWarning else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        data.gateway.latencyMs?.let {
            KpiCard(
                icon = Icons.Filled.Speed,
                label = "Latencia",
                value = "${it}ms",
                color = when { it < 100 -> ColorOnline; it < 500 -> ColorWarning; else -> ColorError },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AgentsSection(data: DashboardState) {
    SectionCard(title = "Agentes", icon = Icons.Filled.SmartToy, count = data.agents.size) {
        if (data.agents.isEmpty()) {
            EmptyState("Sin agentes registrados")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                data.agents.forEach { AgentRow(it) }
            }
        }
    }
}

@Composable
private fun AgentRow(agent: AgentNode) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(agentStatusColor(agent.status)))
            Column {
                Text(agent.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(agent.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusChip(agent.status)
            Spacer(Modifier.height(2.dp))
            Text("↑${formatTokens(agent.tokensIn)} ↓${formatTokens(agent.tokensOut)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionsSection(data: DashboardState) {
    SectionCard(title = "Sesiones Recientes", icon = Icons.Filled.History, count = data.sessions.items.size) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            data.sessions.items.take(8).forEach { session ->
                SessionRow(
                    id = session.id.take(18),
                    model = session.model,
                    status = session.status,
                    messages = session.messagesCount,
                    tokens = session.tokensIn + session.tokensOut
                )
            }
        }
    }
}

@Composable
private fun SessionRow(id: String, model: String, status: String, messages: Int, tokens: Long) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(id, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$messages msgs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTokens(tokens) + " tkn", style = MaterialTheme.typography.labelSmall)
            StatusChip(status)
        }
    }
}

@Composable
private fun SystemSection(data: DashboardState, wsState: String) {
    SectionCard(title = "Sistema", icon = Icons.Filled.Memory) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricWithBar("Memoria", "${data.system.memoryPercent}%", data.system.memoryPercent / 100f, progressColor(data.system.memoryPercent))
            MetricWithBar("Disco",   "${data.system.diskPercent}%",   data.system.diskPercent   / 100f, progressColor(data.system.diskPercent))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniMetric("Modo",  data.gateway.mode,          Modifier.weight(1f))
                MiniMetric("Cola",  "${data.system.queueSize}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniMetric("Errores",    "${data.system.errors}", Modifier.weight(1f),
                    valueColor = if (data.system.errors > 0) ColorError else ColorOnline)
                MiniMetric("Transporte", wsState, Modifier.weight(1f),
                    valueColor = if (wsState.contains("OK")) ColorOnline else ColorIdle)
            }
            data.gateway.uptime?.let { MiniMetric("Uptime", formatUptime(it), Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun UsageSection(data: DashboardState) {
    val usage = data.usage ?: return
    SectionCard(title = "Tokens (${usage.period})", icon = Icons.Filled.BarChart) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsagePill("Entrada", formatTokens(usage.tokensIn),  ColorInfo,    Modifier.weight(1f))
                UsagePill("Salida",  formatTokens(usage.tokensOut), ColorWarning, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsagePill("Total", formatTokens(usage.tokensIn + usage.tokensOut), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                UsagePill("Costo", "${usage.currency} ${"%.4f".format(usage.totalCost)}", ColorOnline, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UsagePill(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CronSection(data: DashboardState) {
    SectionCard(title = "Cron Jobs", icon = Icons.Filled.Schedule, count = data.cronJobs.size) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            data.cronJobs.forEach { job ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(job.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(job.schedule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(if (job.enabled) job.status else "disabled")
                }
            }
        }
    }
}

// ── Componentes reutilizables ─────────────────────────────────────────────────────

@Composable
private fun KpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String? = null,
    badge: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    pulse: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scale by if (pulse) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 1f, targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse)
        )
    } else remember { mutableStateOf(1f) }

    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp).scale(scale))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                badge?.let { Text("v$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                count?.let {
                    Text("$it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            content()
        }
    }
}

@Composable
private fun MetricWithBar(label: String, value: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusChip(status: String) {
    val (bg, fg) = when (status.lowercase()) {
        "active", "online", "running", "busy" -> Color(0xFF1B5E20) to ColorOnline
        "idle", "scheduled"                   -> Color(0xFF263238) to Color(0xFF90A4AE)
        "error", "failed"                     -> Color(0xFF7F0000) to Color(0xFFEF9A9A)
        "disabled"                            -> Color(0xFF212121) to Color(0xFF757575)
        else                                  -> Color(0xFF1A237E) to Color(0xFF90CAF9)
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(status, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun gatewayColor(status: String) = when (status) {
    "online"   -> ColorOnline
    "degraded" -> ColorWarning
    else       -> ColorError
}
private fun agentStatusColor(status: String) = when (status) {
    "busy", "active" -> ColorOnline
    "idle"           -> ColorIdle
    "error"          -> ColorError
    else             -> ColorWarning
}
private fun progressColor(percent: Int) = when {
    percent >= 90 -> ColorError
    percent >= 70 -> ColorWarning
    else          -> ColorOnline
}
fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000     -> "${"%.1f".format(n / 1_000.0)}K"
    else           -> "$n"
}
private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
