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

@Composable
fun TaskBoardScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val tasks by viewModel.tasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchTasks()
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
            Button(onClick = { viewModel.fetchTasks() }) {
                Text("Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats
        StatsBar(tasks = tasks)

        Spacer(modifier = Modifier.height(16.dp))

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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
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
