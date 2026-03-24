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
                            selectedAgent = null
                        },
                        isConnected = isConnected
                    )

                    VerticalDivider()

                    when {
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

                        currentDestination == AppDestination.CHANNELS ||
                        currentDestination == AppDestination.CHANNELS_LIST ->
                            ComingSoonScreen(
                                title = "📡 Channels",
                                description = "Configura canales de entrada: WhatsApp, Telegram, webhooks.",
                                modifier = Modifier.weight(1f)
                            )

                        currentDestination == AppDestination.SESSIONS ||
                        currentDestination == AppDestination.SESSIONS_LIST ->
                            ComingSoonScreen(
                                title = "💬 Sesiones",
                                description = "Sesiones activas de agentes con el gateway.",
                                modifier = Modifier.weight(1f)
                            )

                        currentDestination == AppDestination.MEMORY ||
                        currentDestination == AppDestination.MEMORY_LIST ->
                            ComingSoonScreen(
                                title = "🧠 Memoria",
                                description = "Entradas de memoria persistente de los agentes.",
                                modifier = Modifier.weight(1f)
                            )

                        currentDestination == AppDestination.CRON ||
                        currentDestination == AppDestination.CRON_LIST ->
                            ComingSoonScreen(
                                title = "⏰ Cron Jobs",
                                description = "Tareas programadas que ejecutan agentes automáticamente.",
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

                        else ->
                            ComingSoonScreen(
                                title = currentDestination.label,
                                description = "Próximamente.",
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
