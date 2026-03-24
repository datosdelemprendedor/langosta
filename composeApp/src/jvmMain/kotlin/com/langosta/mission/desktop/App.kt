package com.langosta.mission.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.langosta.mission.desktop.ui.*
import com.langosta.mission.desktop.ui.theme.AppTheme
import com.langosta.mission.domain.model.Agent

@Composable
fun App(viewModel: TaskViewModel, dashboardViewModel: DashboardViewModel) {
    var isConnected by remember { mutableStateOf(false) }
    var darkTheme by remember { mutableStateOf(true) }
    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }
    var selectedAgent by remember { mutableStateOf<Agent?>(null) }

    AppTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!isConnected) {
                SetupScreen(
                    viewModel = viewModel,
                    onConnect = {
                        isConnected = true
                        viewModel.startAutoRefresh()
                        dashboardViewModel.startPolling()
                    }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {

                    Sidebar(
                        selected = currentDestination,
                        onSelect = {
                            currentDestination = it
                            selectedAgent = null  // limpiar agente seleccionado al cambiar sección
                        },
                        isConnected = isConnected
                    )

                    VerticalDivider()

                    when {
                        // SkillsScreen: se muestra cuando hay un agente seleccionado
                        selectedAgent != null ->
                            SkillsScreen(
                                agent = selectedAgent!!,
                                viewModel = dashboardViewModel,
                                onBack = { selectedAgent = null },
                                modifier = Modifier.weight(1f)
                            )

                        currentDestination == AppDestination.DASHBOARD ->
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                modifier = Modifier.weight(1f)
                            )

                        currentDestination == AppDestination.AGENTS ||
                        currentDestination == AppDestination.AGENTS_LIST ->
                            AgentsScreen(
                                viewModel = dashboardViewModel,
                                onOpenSkills = { agent -> selectedAgent = agent },
                                modifier = Modifier.weight(1f)
                            )

                        currentDestination == AppDestination.TASKS ||
                        currentDestination == AppDestination.TASKS_BOARD ->
                            TaskBoardScreen(
                                viewModel = viewModel,
                                modifier = Modifier.weight(1f)
                            )

                        currentDestination == AppDestination.MONITOR ||
                        currentDestination == AppDestination.MONITOR_LOG ->
                            MonitorScreen(
                                viewModel = dashboardViewModel,
                                modifier = Modifier.weight(1f)
                            )

                        currentDestination == AppDestination.SETTINGS ->
                            SetupScreen(
                                viewModel = viewModel,
                                onConnect = { isConnected = true }
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
