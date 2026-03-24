package com.langosta.mission.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.data.db.DatabaseManager
import com.langosta.mission.data.repository.TaskRepository
import com.langosta.mission.util.ConfigManager

fun main() = application {
    ConfigManager.loadDefaults()
    println(">>> SERVER URL: ${ConfigManager.getServerUrl()}")
    println(">>> TOKEN: ${ConfigManager.getAuthToken()}")
    val db = DatabaseManager()
    val client = OpenClawClient(ConfigManager.getServerUrl())
    val repository = TaskRepository(client, db)
    val viewModel = TaskViewModel(repository)
    val dashboardViewModel = DashboardViewModel()

    Window(
        onCloseRequest = ::exitApplication,
        title = "OpenCLAW Mission Control"
    ) {
        App(viewModel, dashboardViewModel)
    }
}
