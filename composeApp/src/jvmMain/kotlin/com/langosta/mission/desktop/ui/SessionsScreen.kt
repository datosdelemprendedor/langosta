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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langosta.mission.data.repository.SessionRepository
import com.langosta.mission.domain.model.Session
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsScreen(modifier: Modifier = Modifier) {
    val repo = remember { SessionRepository() }
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { sessions = repo.getSessions(); isLoading = false }
    LaunchedEffect(snackMessage) {
        snackMessage?.let { snackbarHostState.showSnackbar(it); snackMessage = null }
    }

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
                    Text("💬 Sesiones",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("${sessions.size} sesiones activas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = {
                    scope.launch { isLoading = true; sessions = repo.getSessions(); isLoading = false }
                }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💤", style = MaterialTheme.typography.displaySmall)
                        Text("Sin sesiones activas", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions, key = { it.key }) { session ->
                        SessionRow(
                            session = session,
                            onDelete = {
                                scope.launch {
                                    val result = repo.deleteSession(session.key)
                                    result.fold(
                                        onSuccess = {
                                            sessions = sessions.filter { it.key != session.key }
                                            snackMessage = "✅ Sesión eliminada"
                                        },
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
private fun SessionRow(session: Session, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val agentId = if (session.key.startsWith("agent:")) session.key.substringAfter("agent:").substringBefore(":") else session.key
    val ageLabel = when {
        session.ageMs > 3_600_000  -> "${session.ageMs / 3_600_000}h ago"
        session.ageMs > 60_000     -> "${session.ageMs / 60_000}m ago"
        session.ageMs > 0          -> "${session.ageMs / 1000}s ago"
        else                       -> "-"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(agentId, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(session.model,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("• ${session.kind}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• $ageLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("IN: ${formatTokens(session.inputTokens)}",
                        style = MaterialTheme.typography.labelSmall)
                    Text("OUT: ${formatTokens(session.outputTokens)}",
                        style = MaterialTheme.typography.labelSmall)
                    Text("CTX: ${formatTokens(session.contextTokens)}",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar sesión",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
