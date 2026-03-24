package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langosta.mission.desktop.TaskViewModel
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(viewModel: TaskViewModel, onConnect: () -> Unit) {
    var serverUrl        by remember { mutableStateOf(ConfigManager.getServerUrl()) }
    var wsUrl            by remember { mutableStateOf(ConfigManager.getWebSocketUrl()) }
    var isLoading        by remember { mutableStateOf(false) }
    var connectionError  by remember { mutableStateOf<String?>(null) }
    val scope            = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.width(480.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Logo
                Text("🦞", style = MaterialTheme.typography.displayMedium)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Langosta",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Mission Control",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // Info gateway
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Gateway detectado", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoPill("Modo", "local")
                            InfoPill("Puerto", "18789")
                            InfoPill("Bind", "loopback")
                        }
                    }
                }

                // HTTP URL
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; connectionError = null },
                    label = { Text("Server URL (HTTP)") },
                    placeholder = { Text("http://localhost:18789") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = connectionError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // WS URL
                OutlinedTextField(
                    value = wsUrl,
                    onValueChange = { wsUrl = it; connectionError = null },
                    label = { Text("WebSocket host:port") },
                    placeholder = { Text("localhost:18789") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = connectionError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Error
                if (connectionError != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(10.dp)
                    ) {
                        Text(
                            connectionError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Botón
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            connectionError = null
                            ConfigManager.set("server_url", serverUrl)
                            ConfigManager.set("ws_url", wsUrl)
                            val ok = viewModel.testConnection()
                            isLoading = false
                            if (ok) onConnect()
                            else connectionError = "❌ No se pudo conectar. Verifica que OpenClaw gateway esté corriendo (puerto 18789)."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Conectando...")
                    } else {
                        Text("Conectar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
