package com.langosta.mission.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.langosta.mission.util.ConfigManager

@Composable
fun SetupScreen(onConnect: () -> Unit) {
    var serverUrl by remember { mutableStateOf(ConfigManager.getServerUrl()) }

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
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                ConfigManager.set("server_url", serverUrl)
                onConnect()
            },
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text("Connect")
        }
    }
}
