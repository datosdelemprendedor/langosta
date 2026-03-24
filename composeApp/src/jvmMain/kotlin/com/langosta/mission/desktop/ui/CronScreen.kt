package com.langosta.mission.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langosta.mission.data.repository.CronRepository
import com.langosta.mission.domain.model.CronJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CronScreen(modifier: Modifier = Modifier) {
    val repo = remember { CronRepository() }
    val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf<List<CronJob>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { jobs = repo.getCronJobs(); isLoading = false }
    LaunchedEffect(snackMessage) {
        snackMessage?.let { snackbarHostState.showSnackbar(it); snackMessage = null }
    }

    fun reload() { scope.launch { isLoading = true; jobs = repo.getCronJobs(); isLoading = false } }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("⏰ Cron Jobs",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("${jobs.count { it.enabled }} activos · ${jobs.size} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = ::reload) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (jobs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏳", style = MaterialTheme.typography.displaySmall)
                        Text("Sin cron jobs configurados", style = MaterialTheme.typography.bodyLarge)
                        Text("Crea uno desde la CLI: openclaw cron add ...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(jobs, key = { it.id }) { job ->
                        CronJobCard(
                            job = job,
                            onToggle = { action ->
                                scope.launch {
                                    repo.doAction(action, job.id).fold(
                                        onSuccess = { reload(); snackMessage = "✅ Job ${it}" },
                                        onFailure = { snackMessage = "❌ ${it.message}" }
                                    )
                                }
                            },
                            onRun = {
                                scope.launch {
                                    repo.doAction("run", job.id).fold(
                                        onSuccess = { snackMessage = "▶️ Ejecutando ${job.name}..." },
                                        onFailure = { snackMessage = "❌ ${it.message}" }
                                    )
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    repo.doAction("delete", job.id).fold(
                                        onSuccess = { reload(); snackMessage = "✅ Job eliminado" },
                                        onFailure = { snackMessage = "❌ ${it.message}" }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CronJobCard(
    job: CronJob,
    onToggle: (String) -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val nextRun = job.nextRunAtMs?.let { fmt.format(Date(it)) } ?: "-"
    val lastRun = job.lastRunAtMs?.let { fmt.format(Date(it)) } ?: "-"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(job.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    StatusChip(if (job.enabled) "active" else "idle")
                    job.lastStatus?.let {
                        StatusChip(it)
                    }
                }
                Row {
                    // Toggle enable/disable
                    IconButton(onClick = { onToggle(if (job.enabled) "disable" else "enable") }) {
                        Icon(
                            if (job.enabled) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                            contentDescription = if (job.enabled) "Desactivar" else "Activar",
                            tint = if (job.enabled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Run now
                    IconButton(onClick = onRun) {
                        Icon(Icons.Filled.PlayArrow, "Ejecutar ahora",
                            tint = MaterialTheme.colorScheme.secondary)
                    }
                    // Delete
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, "Eliminar",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Descripción + agente
            if (job.description.isNotBlank()) {
                Text(job.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Schedule + delivery
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelValue("⏱️", job.scheduleExpr.ifBlank { job.scheduleKind })
                job.agentId?.let { LabelValue("🤖", it) }
                if (job.deliveryMode != "none") {
                    LabelValue("📤", job.deliveryChannel ?: job.deliveryMode)
                }
            }

            // Mensaje del payload
            if (job.payloadMessage.isNotBlank()) {
                Text("“${job.payloadMessage.take(120)}${if (job.payloadMessage.length > 120) "…" else ""}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }

            // Última/siguiente ejecución
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabelValue("⏪", lastRun)
                LabelValue("⏩", nextRun)
                if (job.consecutiveErrors > 0) {
                    Text("${job.consecutiveErrors} errores consecutivos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }

            // Error
            job.lastError?.let {
                Text("⚠️ $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
