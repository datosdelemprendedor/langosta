package com.langosta.mission.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.langosta.mission.desktop.TaskViewModel
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(viewModel: TaskViewModel, onConnect: () -> Unit) {
    var serverUrl by remember { mutableStateOf(ConfigManager.getServerUrl()) }
    var wsUrl by remember { mutableStateOf(ConfigManager.getWebSocketUrl()) }
    var isLoading by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "OpenCLAW Mission Control",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; connectionError = false },
            label = { Text("Server URL (HTTP)") },
            placeholder = { Text("http://localhost:8000") },
            modifier = Modifier.fillMaxWidth(0.6f),
            isError = connectionError
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = wsUrl,
            onValueChange = { wsUrl = it; connectionError = false },
            label = { Text("WebSocket host:port") },
            placeholder = { Text("localhost:8000") },
            modifier = Modifier.fillMaxWidth(0.6f),
            isError = connectionError
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    connectionError = false
                    ConfigManager.set("server_url", serverUrl)
                    ConfigManager.set("ws_url", wsUrl)
                    val ok = viewModel.testConnection()
                    isLoading = false
                    if (ok) onConnect()
                    else connectionError = true
                }
            },
            modifier = Modifier.fillMaxWidth(0.6f),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Connect")
            }
        }

        if (connectionError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "❌ No se pudo conectar al servidor",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
