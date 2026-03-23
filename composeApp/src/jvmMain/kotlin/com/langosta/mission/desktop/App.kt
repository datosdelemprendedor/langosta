package com.langosta.mission.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.langosta.mission.desktop.ui.AgentPanel
import com.langosta.mission.desktop.ui.NotificationPanel
import com.langosta.mission.desktop.ui.SetupScreen
import com.langosta.mission.desktop.ui.TaskBoardScreen

@Composable
fun App(viewModel: TaskViewModel) {
    var isConnected by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!isConnected) {
                SetupScreen(viewModel = viewModel, onConnect = { isConnected = true })
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    AgentPanel(
                        viewModel = viewModel,
                        modifier = Modifier
                    )
                    TaskBoardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                    NotificationPanel()
                }
            }
        }
    }
}
