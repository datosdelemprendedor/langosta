package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.langosta.mission.data.repository.MemoryRepository
import com.langosta.mission.domain.model.AgentMemoryFile
import com.langosta.mission.domain.model.MemoryJournalFile
import kotlinx.coroutines.launch

@Composable
fun MemoryScreen(modifier: Modifier = Modifier) {
    val repo = remember { MemoryRepository() }
    val scope = rememberCoroutineScope()
    var agentFiles by remember { mutableStateOf<List<AgentMemoryFile>>(emptyList()) }
    var journalFiles by remember { mutableStateOf<List<MemoryJournalFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAgent by remember { mutableStateOf<AgentMemoryFile?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMessage by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            isLoading = true
            val (agents, daily) = repo.getMemoryOverview()
            agentFiles = agents
            journalFiles = daily
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(snackMessage) {
        snackMessage?.let { snackbarHostState.showSnackbar(it); snackMessage = null }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (selectedAgent != null) {
            AgentMemoryDetail(
                agent = selectedAgent!!,
                onBack = { selectedAgent = null },
                onReindex = {
                    scope.launch {
                        repo.reindex(selectedAgent!!.agentId).fold(
                            onSuccess = { snackMessage = "✅ $it"; reload() },
                            onFailure = { snackMessage = "❌ ${it.message}" }
                        )
                    }
                }
            )
            return@Scaffold
        }

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
                    Text("🧠 Memoria",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("${agentFiles.size} agentes · ${journalFiles.size} entradas de diario",
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
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Sección: memoria por agente
                    if (agentFiles.isNotEmpty()) {
                        item {
                            Text("Memoria por agente",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(agentFiles, key = { it.agentId }) { agent ->
                            AgentMemoryCard(
                                agent = agent,
                                onClick = { selectedAgent = agent }
                            )
                        }
                    }

                    // Sección: diario
                    if (journalFiles.isNotEmpty()) {
                        item { Spacer(Modifier.height(4.dp)) }
                        item {
                            Text("Diario de memoria (memory/*.md)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(journalFiles, key = { it.name }) { journal ->
                            JournalFileRow(journal)
                        }
                    }

                    if (agentFiles.isEmpty() && journalFiles.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 64.dp),
                                contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🧠", style = MaterialTheme.typography.displaySmall)
                                    Text("Sin entradas de memoria",
                                        style = MaterialTheme.typography.bodyLarge)
                                    Text("Los agentes guardarán memoria en MEMORY.md",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentMemoryCard(agent: AgentMemoryFile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(agent.agentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    if (agent.isDefault) {
                        Text("default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (!agent.exists) {
                        Text("sin MEMORY.md",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${agent.words} palabras",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("· ${agent.indexedChunks} chunks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (agent.dirty) {
                        Text("· pendiente re-index",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            VectorStateBadge(agent.vectorState)
            Icon(Icons.Filled.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgentMemoryDetail(
    agent: AgentMemoryFile,
    onBack: () -> Unit,
    onReindex: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Volver")
                }
                Column {
                    Text(agent.agentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(agent.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VectorStateBadge(agent.vectorState)
                OutlinedButton(onClick = onReindex) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Re-indexar")
                }
            }
        }

        // Stats chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("📝 ${agent.words} palabras")
            InfoChip("🧱 ${agent.indexedChunks} chunks")
            InfoChip("📄 ${agent.indexedFiles} archivos")
            if (agent.dirty) InfoChip("⚠️ pendiente",
                color = MaterialTheme.colorScheme.errorContainer)
        }

        // Contenido del MEMORY.md
        if (agent.exists && agent.content.isNotBlank()) {
            Text("Contenido de ${agent.fileName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(Modifier.padding(16.dp)) {
                    Text(
                        text = agent.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(if (agent.exists) "MEMORY.md vacío"
                     else "Este agente aún no tiene MEMORY.md",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun JournalFileRow(journal: MemoryJournalFile) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(journal.date,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
            Text(journal.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${journal.words}w",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            VectorStateBadge(journal.vectorState)
        }
    }
}

@Composable
private fun VectorStateBadge(state: String) {
    val (bg, label) = when (state) {
        "indexed"     -> Color(0xFF4CAF50) to "✓ indexed"
        "stale"       -> Color(0xFFFFC107) to "⚠ stale"
        "not_indexed" -> Color(0xFF78909C) to "○ sin indexar"
        else          -> Color(0xFF90A4AE) to "? unknown"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun InfoChip(text: String, color: androidx.compose.ui.graphics.Color =
    MaterialTheme.colorScheme.secondaryContainer) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
