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
import com.langosta.mission.domain.model.TaskStatus
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File

@Composable
fun TaskBoardScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val tasks        by viewModel.tasks.collectAsState()
    val agents       by viewModel.agents.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val error        by viewModel.error.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val history      by viewModel.history.collectAsState()

    var showCreateDialog  by remember { mutableStateOf(false) }
    var selectedFilter    by remember { mutableStateOf<TaskStatus?>(null) }
    var showHistory       by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchTasks()
        viewModel.loadHistory()
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            agents = agents,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, agentId ->
                viewModel.createTask(title, description, agentId)
                showCreateDialog = false
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (serverStatus) "🟢" else "🔴",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Mission Control", style = MaterialTheme.typography.headlineMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Botón historial con badge
                BadgedBox(
                    badge = {
                        if (history.isNotEmpty()) Badge { Text("${history.size}") }
                    }
                ) {
                    OutlinedButton(onClick = {
                        showHistory = !showHistory
                        if (showHistory) viewModel.loadHistory()
                    }) {
                        Text(if (showHistory) "📋 Ocultar" else "📋 Historial")
                    }
                }
                Button(onClick = { showCreateDialog = true }) {
                    Text("+ Nueva Tarea")
                }
                OutlinedButton(onClick = { viewModel.fetchTasks() }) {
                    Text("Refresh")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Panel historial (reemplaza la lista cuando está visible)
        if (showHistory) {
            TaskHistoryPanel(
                entries = history,
                onClear = { viewModel.clearHistory() },
                onExportCsv = {
                    exportCsv(history.map {
                        listOf(it.taskId, it.title, it.agentName,
                            it.durationSeconds.toString(), it.result,
                            it.completedAt.toString())
                    })
                }
            )
        } else {
            // Stats
            StatsBar(tasks = tasks)

            Spacer(modifier = Modifier.height(16.dp))

            // Filtros
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("ALL") }
                )
                TaskStatus.entries.forEach { status ->
                    FilterChip(
                        selected = selectedFilter == status,
                        onClick = { selectedFilter = status },
                        label = { Text(status.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Error
            error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Task list
            val filtered = if (selectedFilter == null) tasks
            else tasks.filter { it.status == selectedFilter }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered) { task ->
                        TaskCard(
                            task = task,
                            onStatusChange = { newStatus ->
                                viewModel.updateTaskStatus(task.id, newStatus)
                            }
                        )
                    }
                }
            }
        }
    }
}

// Export CSV usando JFileChooser (JVM Desktop)
private fun exportCsv(rows: List<List<String>>) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Guardar historial CSV"
        fileFilter = FileNameExtensionFilter("CSV files", "csv")
        selectedFile = File("historial_tareas.csv")
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(".csv")) file = File(file.absolutePath + ".csv")
        val header = "ID,Título,Agente,Duración(s),Resultado,FechaCompletado\n"
        val content = rows.joinToString("\n") { it.joinToString(",") { v -> "\"$v\"" } }
        file.writeText(header + content)
    }
}
