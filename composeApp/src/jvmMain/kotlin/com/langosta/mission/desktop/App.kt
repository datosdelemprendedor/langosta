package com.langosta.mission.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.langosta.mission.desktop.ui.*
import com.langosta.mission.desktop.ui.theme.AppTheme

@Composable
fun App(viewModel: TaskViewModel, dashboardViewModel: DashboardViewModel) {
    var isConnected by remember { mutableStateOf(false) }
    var darkTheme by remember { mutableStateOf(true) }
    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }

    AppTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!isConnected) {
                SetupScreen(
                    viewModel = viewModel,
                    onConnect = {
                        isConnected = true
                        viewModel.startAutoRefresh()
                    }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {

                    Sidebar(
                        selected = currentDestination,
                        onSelect = { currentDestination = it }
                    )

                    VerticalDivider()

                    when (currentDestination) {
                        AppDestination.DASHBOARD -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            modifier = Modifier.weight(1f)
                        )
                        AppDestination.AGENTS,
                        AppDestination.AGENTS_LIST -> AgentPanel(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        AppDestination.TASKS,
                        AppDestination.TASKS_BOARD -> TaskBoardScreen(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        AppDestination.SETTINGS,
                        AppDestination.SETTINGS_SERVER -> SetupScreen(
                            viewModel = viewModel,
                            onConnect = { isConnected = true }
                        )
                        else -> TaskBoardScreen(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    VerticalDivider()

                    NotificationPanel(
                        darkTheme = darkTheme,
                        onToggleTheme = { darkTheme = !darkTheme }
                    )
                }
            }
        }
    }
}
